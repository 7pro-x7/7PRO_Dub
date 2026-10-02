import {
  AuthError,
  corsHeaders,
  createAdminClient,
  json,
  requireAuth,
} from "../_shared/auth.ts";
import {
  chargeWallet,
  createCheckout,
  listPaymentMethods,
  normalizeWalletPhone,
  PaymentMethod,
  PaymobConfig,
  readPaymobConfig,
  walletBrandFromPhone,
} from "../_shared/paymob.ts";

interface QuoteBody {
  action: "quote" | "create" | "methods";
  item_type: "COURSE" | "COURSE_MONTHLY" | "LIVE" | "AI_TUTOR" | "DUBBING";
  course_id?: string | null;
  live_plan_id?: string | null;
  live_group_id?: string | null;
  coupon_code?: string | null;
  idempotency_key?: string | null;
  /** Paymob integration the learner picked on the payment screen. */
  method_id?: string | null;
  /** Currency of the quote, used to hide methods the account cannot charge in. */
  currency?: string | null;
  /** Wallet number to bill when the learner chose Vodafone Cash or another mobile wallet. */
  wallet_phone?: string | null;
  /** Set when the learner is paying by manual transfer instead of through the gateway. */
  manual?: boolean | null;
  /** Which published wallet the transfer was sent to: VODAFONE / ORANGE / ETISALAT / WE. */
  manual_brand?: string | null;
  /** The number the learner transferred from — what the owner checks the transfer against. */
  sender_phone?: string | null;
  /** Storage key of the uploaded transfer screenshot, inside the private payment-proofs bucket. */
  proof_path?: string | null;
  /** Anything the learner wants the reviewer to know. */
  note?: string | null;
}

/** Payment options for the merchant account, cached briefly so a checkout is not slowed down. */
let methodCache: { at: number; methods: PaymentMethod[] } | null = null;
const METHOD_CACHE_MS = 5 * 60 * 1000;

async function paymentMethods(cfg: PaymobConfig): Promise<PaymentMethod[]> {
  if (methodCache && Date.now() - methodCache.at < METHOD_CACHE_MS) return methodCache.methods;
  const methods = await listPaymentMethods(cfg);
  methodCache = { at: Date.now(), methods };
  return methods;
}

/**
 * The payment options offered to a learner, with the mobile wallet always included when the
 * account has one.
 *
 * Paymob's listing endpoint is not open on every merchant account, and a wallet integration set
 * explicitly by the owner must show up even when the listing cannot be read — otherwise Vodafone
 * Cash would silently disappear from a perfectly capable account.
 *
 * Cash (the KIOSK rail — Aman, Masary and partners) is deliberately excluded here: the app no
 * longer offers it as a checkout option, and excluding it in this single shared function also
 * keeps a client from forcing it through by passing its integration id directly to "create".
 */
async function offeredMethods(cfg: PaymobConfig, currency: string): Promise<PaymentMethod[]> {
  const listed = await paymentMethods(cfg).catch((err) => {
    console.error("paymob_methods_failed", err instanceof Error ? err.message : err);
    return [] as PaymentMethod[];
  });

  const methods = listed.filter((m) => m.currency === currency && m.kind !== "KIOSK");
  const walletId = cfg.walletIntegrationId;
  if (walletId && !methods.some((m) => m.kind === "WALLET")) {
    // The explicitly configured rail is the account's single unified wallet integration: it
    // bills every operator, so it carries no brand and the app presents it as one option rather
    // than pretending the account has four separate wallet integrations.
    methods.push({
      id: walletId,
      kind: "WALLET",
      label: "Mobile wallet",
      currency,
      live: true,
      walletBrand: null,
    });
  }
  return methods;
}

/** Maps a Postgres exception message to a stable, user-safe error code. */
function toErrorCode(message: string): string {
  const known = [
    "ORDER_ALREADY_PAID",
    "ORDER_NOT_PAYABLE",
    "REQUEST_ALREADY_APPROVED",
    "MANUAL_METHOD_UNAVAILABLE",
    "SENDER_PHONE_INVALID",
    "PROOF_REQUIRED",
    "PROOF_INVALID",
    "COURSE_NOT_AVAILABLE",
    "LIVE_NOT_AVAILABLE",
    "TEACHER_NOT_ACCEPTING_STUDENTS",
    "GROUP_FULL",
    "GROUP_NOT_FOUND",
    "ALREADY_ENROLLED",
    "ACCOUNT_NOT_ACTIVE",
    "ITEM_NOT_FOUND",
  ];
  const hit = known.find((k) => message.includes(k));
  return hit ?? "CHECKOUT_FAILED";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as QuoteBody;
    const admin = createAdminClient();

    // One catalogue, one price, for everyone.
    //
    // Pricing used to follow the buyer's detected location, which meant someone travelling or
    // using a foreign network could be quoted a currency the gateway cannot charge — in effect a
    // country restriction. The platform's own country decides the price now, so anyone anywhere
    // pays the same amount through the same live payment methods.
    const { data: defaultCountryRaw } = await admin
      .from("app_settings")
      .select("value")
      .eq("key", "pricing.default_country")
      .maybeSingle();
    const country =
      (typeof defaultCountryRaw?.value === "string" ? (defaultCountryRaw.value as string) : "EG")
        .toUpperCase();

    // The owner's master switch. With Paymob off nothing is ever sent to the gateway: the app
    // falls back to a manual wallet transfer that staff approve by hand. Missing means on, so
    // an unseeded install keeps working exactly as before.
    const { data: paymobFlagRaw } = await admin
      .from("app_settings")
      .select("value")
      .eq("key", "payments.paymob_enabled")
      .maybeSingle();
    const paymobEnabled = paymobFlagRaw?.value !== false && paymobFlagRaw?.value !== "false";

    if (body.action === "quote") {
      const { data, error } = await admin.rpc("quote_checkout", {
        p_user: user.userId,
        p_item_type: body.item_type,
        p_course_id: body.course_id ?? null,
        p_live_plan_id: body.live_plan_id ?? null,
        p_live_group_id: body.live_group_id ?? null,
        p_coupon_code: body.coupon_code ?? null,
        p_country: country,
      });
      if (error) return json({ error: toErrorCode(error.message), detail: error.message }, 400);
      return json({ ok: true, quote: data });
    }

    if (body.action === "methods") {
      // Switched off means there is nothing chargeable, so the sheet is told plainly rather
      // than being handed options that would fail at the gateway.
      if (!paymobEnabled) return json({ ok: true, methods: [], paymob_enabled: false });
      const cfg = readPaymobConfig();
      if (!cfg) return json({ ok: true, methods: [], paymob_enabled: true });
      const wanted = (body.currency ?? "EGP").toUpperCase();
      const methods = (await offeredMethods(cfg, wanted)).map(({ id, kind, label, walletBrand }) => ({
        id,
        kind,
        label,
        wallet_brand: walletBrand,
      }));
      return json({ ok: true, methods, paymob_enabled: true });
    }

    if (body.action !== "create") return json({ error: "UNKNOWN_ACTION" }, 400);

    // Paying by transfer: the learner has already sent the money to a number the owner
    // published and is now handing over the proof for review.
    const manual = body.manual === true;
    const manualSender = manual ? normalizeWalletPhone(body.sender_phone) : null;
    if (manual) {
      // Everything is checked before create_order runs, so a half-filled form never leaves a
      // pending order sitting in the learner's history.
      if (!body.manual_brand) return json({ error: "MANUAL_BRAND_REQUIRED" }, 400);
      if (!body.sender_phone) return json({ error: "SENDER_PHONE_REQUIRED" }, 400);
      if (!manualSender) return json({ error: "SENDER_PHONE_INVALID" }, 400);
      if (!body.proof_path) return json({ error: "PROOF_REQUIRED" }, 400);
    }
    // Note there is deliberately no gateway check here any more. It used to sit at this point
    // and reject every non-manual checkout the moment Paymob was switched off — including the
    // ones that need no gateway at all. A free course and a 100%-off coupon both owe nothing,
    // so both were being refused with PAYMOB_DISABLED for a payment that was never going to
    // happen. The check now runs below, after the total is known, and only when something is
    // actually owed.

    // A wallet number is checked before anything is created, so a mistyped number never leaves
    // an abandoned pending order behind.
    const preCfg = readPaymobConfig();
    let walletIntegrationId: string | null = null;
    let walletPhone: string | null = null;
    if (!manual && preCfg && body.method_id) {
      const offered = await offeredMethods(preCfg, (body.currency ?? "EGP").toUpperCase());
      const chosen = offered.find((m) => m.id === String(body.method_id));
      if (chosen?.kind === "WALLET") {
        walletIntegrationId = chosen.id;
        walletPhone = normalizeWalletPhone(body.wallet_phone);
        if (!body.wallet_phone) return json({ error: "WALLET_PHONE_REQUIRED" }, 400);
        if (!walletPhone) return json({ error: "WALLET_PHONE_INVALID" }, 400);

        // When the account really does have one integration per operator, a Vodafone number sent
        // to the Orange rail is refused by the gateway only after the order is already open.
        // Catching it here avoids that abandoned pending order and gives the payer a message
        // that names the real problem. Accounts on the single unified wallet rail carry no brand
        // on the integration and are left entirely untouched by this check.
        if (chosen.walletBrand) {
          const numberBrand = walletBrandFromPhone(walletPhone);
          if (numberBrand && numberBrand !== chosen.walletBrand) {
            return json(
              {
                error: "WALLET_PHONE_BRAND_MISMATCH",
                expected: chosen.walletBrand,
                detected: numberBrand,
              },
              400,
            );
          }
        }
      }
    }

    const { data: order, error: orderError } = await admin.rpc("create_order", {
      p_user: user.userId,
      p_item_type: body.item_type,
      p_course_id: body.course_id ?? null,
      p_live_plan_id: body.live_plan_id ?? null,
      p_live_group_id: body.live_group_id ?? null,
      p_coupon_code: body.coupon_code ?? null,
      p_country: country,
      p_idempotency_key: body.idempotency_key ?? null,
    });
    if (orderError) return json({ error: toErrorCode(orderError.message), detail: orderError.message }, 400);

    const row = order as Record<string, unknown>;
    const orderId = row.id as string;
    const total = Number(row.total_amount ?? 0);

    // Free / fully discounted orders are granted immediately — no gateway round trip, and no
    // dependency on any payment method being configured, enabled or even reachable. This is
    // the single path that a free course and a 100%-off coupon both arrive on: the price is
    // resolved server-side (resolve_price forces 0 for a course flagged free; evaluate_coupon
    // caps the discount at the list price), so nothing a client sends can turn a paid course
    // into a free grant.
    if (total <= 0) {
      const { error: grantError } = await admin.rpc("confirm_order_payment", {
        p_order_id: orderId,
        p_provider: "FREE",
        p_provider_ref: `free_${orderId}`,
        p_event_id: `free_${orderId}`,
        p_payload: { reason: "ZERO_TOTAL" },
      });
      if (grantError) return json({ error: toErrorCode(grantError.message), detail: grantError.message }, 400);
      return json({ ok: true, order_id: orderId, free: true, total_amount: 0 });
    }

    // A transfer unlocks nothing on its own: the order stays PENDING and only an owner or
    // admin approval — which runs the same confirm path a signed webhook does — grants access.
    if (manual) {
      const { data: request, error: manualError } = await admin.rpc("submit_manual_payment", {
        p_user: user.userId,
        p_order_id: orderId,
        p_brand: String(body.manual_brand).toUpperCase(),
        p_sender_phone: manualSender,
        p_proof_path: body.proof_path,
        p_note: body.note ?? null,
      });
      if (manualError) {
        return json({ error: toErrorCode(manualError.message), detail: manualError.message }, 400);
      }
      return json({
        ok: true,
        order_id: orderId,
        manual_pending: true,
        request_id: (request as Record<string, unknown> | null)?.request_id ?? null,
        total_amount: total,
        currency: row.currency,
      });
    }

    // Something is owed, so now the gateway actually has to be available. Both refusals below
    // close the order rather than leaving a PENDING row the learner would see in their history
    // for a payment that never opened.
    if (!paymobEnabled) {
      await admin.rpc("fail_order_payment", {
        p_order_id: orderId,
        p_provider: "PAYMOB",
        p_event_id: `disabled_${orderId}`,
        p_status: "PAYMENT_FAILED",
        p_reason: "Card payments are switched off. Please pay by transfer instead.",
        p_payload: {},
      });
      return json({ error: "PAYMOB_DISABLED" }, 503);
    }

    const cfg = readPaymobConfig();
    if (!cfg) {
      await admin.rpc("fail_order_payment", {
        p_order_id: orderId,
        p_provider: "PAYMOB",
        p_event_id: `cfg_${orderId}`,
        p_status: "PAYMENT_FAILED",
        p_reason: "Payment gateway is not configured yet.",
        p_payload: {},
      });
      return json({ error: "GATEWAY_NOT_CONFIGURED" }, 503);
    }

    const { data: profile } = await admin
      .from("profiles")
      .select("full_name, email, phone")
      .eq("id", user.userId)
      .maybeSingle();

    const nameParts = String(profile?.full_name ?? "7PRO Student").trim().split(" ");

    // Only an integration that really belongs to this account, in the order's own currency,
    // may be charged — the id arrives from the client and is never trusted as-is.
    const orderCurrency = String(row.currency ?? "EGP").toUpperCase();
    let integrationId: string | null = null;
    if (body.method_id) {
      const allowed = await offeredMethods(cfg, orderCurrency);
      integrationId = allowed.find((m) => m.id === String(body.method_id))?.id ?? null;
    }

    // Mobile wallets are billed against the number itself, never through the card iframe.
    if (walletIntegrationId && walletPhone && integrationId === walletIntegrationId) {
      let charge;
      try {
        charge = await chargeWallet(cfg, {
          orderId,
          amount: total,
          currency: orderCurrency,
          description: `7PRO ${String(row.item_type)} purchase`,
          email: String(profile?.email ?? user.email ?? "student@7pro.app"),
          firstName: nameParts[0] ?? "Student",
          lastName: nameParts.slice(1).join(" ") || "7PRO",
          phone: walletPhone,
          integrationId: walletIntegrationId,
          country,
          walletPhone,
        });
      } catch (err) {
        console.error("paymob_wallet_failed", err instanceof Error ? err.message : err);
        await admin.rpc("fail_order_payment", {
          p_order_id: orderId,
          p_provider: "PAYMOB",
          p_event_id: `wallet_err_${orderId}`,
          p_status: "PAYMENT_FAILED",
          p_reason: "The wallet request could not be sent. Please try again.",
          p_payload: {},
        });
        return json({ error: "WALLET_UNAVAILABLE" }, 502);
      }

      await admin
        .from("orders")
        .update({ provider: "PAYMOB", provider_ref: charge.providerRef, checkout_url: charge.redirectUrl })
        .eq("id", orderId);

      if (charge.declineReason) {
        await admin.rpc("fail_order_payment", {
          p_order_id: orderId,
          p_provider: "PAYMOB",
          p_event_id: `wallet_declined_${orderId}`,
          p_status: "PAYMENT_FAILED",
          p_reason: "The wallet declined the payment request.",
          p_payload: { reason: charge.declineReason },
        });
        return json({ error: "WALLET_DECLINED", detail: charge.declineReason }, 400);
      }

      // Pending only: access is still granted solely by the signed webhook.
      return json({
        ok: true,
        order_id: orderId,
        wallet_pending: true,
        wallet_phone: walletPhone,
        checkout_url: charge.redirectUrl,
        total_amount: total,
        currency: row.currency,
      });
    }

    let session;
    try {
      session = await createCheckout(cfg, {
        orderId,
        amount: total,
        currency: String(row.currency ?? "EGP"),
        description: `7PRO ${String(row.item_type)} purchase`,
        email: String(profile?.email ?? user.email ?? "student@7pro.app"),
        firstName: nameParts[0] ?? "Student",
        lastName: nameParts.slice(1).join(" ") || "7PRO",
        phone: String(profile?.phone ?? ""),
        integrationId,
        country,
      });
    } catch (err) {
      console.error("paymob_checkout_failed", err instanceof Error ? err.message : err);
      await admin.rpc("fail_order_payment", {
        p_order_id: orderId,
        p_provider: "PAYMOB",
        p_event_id: `err_${orderId}`,
        p_status: "PAYMENT_FAILED",
        p_reason: "Could not open the payment page. Please try again.",
        p_payload: {},
      });
      return json({ error: "GATEWAY_UNAVAILABLE" }, 502);
    }

    await admin
      .from("orders")
      .update({ provider: "PAYMOB", provider_ref: session.providerRef, checkout_url: session.checkoutUrl })
      .eq("id", orderId);

    return json({
      ok: true,
      order_id: orderId,
      checkout_url: session.checkoutUrl,
      total_amount: total,
      currency: row.currency,
    });
  } catch (err) {
    if (err instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("checkout_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
