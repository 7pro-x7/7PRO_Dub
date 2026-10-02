/**
 * Paymob (Accept) integration — the Egyptian electronic payment gateway.
 * All secrets stay server-side; the client only ever receives a hosted checkout URL.
 */

/**
 * Paymob production endpoint. There is no sandbox switch on purpose: 7PRO only ever talks to
 * the merchant's live account, with the live keys held in server-side secrets.
 */
const BASE = "https://accept.paymob.com";

export interface PaymobConfig {
  apiKey: string;
  integrationId: string;
  iframeId: string;
  hmacSecret: string;
  /** Mobile-wallet (Vodafone Cash / Orange / Etisalat / WE) integration, when the account has one. */
  walletIntegrationId: string | null;
}

export function readPaymobConfig(): PaymobConfig | null {
  const apiKey = Deno.env.get("PAYMOB_API_KEY");
  const integrationId = Deno.env.get("PAYMOB_INTEGRATION_ID");
  const iframeId = Deno.env.get("PAYMOB_IFRAME_ID");
  const hmacSecret = Deno.env.get("PAYMOB_HMAC_SECRET");
  if (!apiKey || !integrationId || !iframeId || !hmacSecret) return null;
  const wallet = Deno.env.get("PAYMOB_WALLET_INTEGRATION_ID")?.trim();
  return {
    apiKey,
    integrationId,
    iframeId,
    hmacSecret,
    walletIntegrationId: wallet && wallet.length > 0 ? wallet : null,
  };
}

/**
 * Puts an Egyptian wallet number into the shape Paymob expects (`01XXXXXXXXX`).
 *
 * Vodafone Cash is charged against the phone number itself, so a wrong format is refused here
 * rather than at the gateway — an order is never opened for a number that cannot be billed.
 * Accepts what people actually type: spaces, dashes, a `+20` or `0020` prefix.
 *
 * @returns the normalised number, or null when it is not a chargeable Egyptian wallet number.
 */
export function normalizeWalletPhone(input: string | null | undefined): string | null {
  const digits = String(input ?? "").replace(/\D/g, "");
  if (!digits) return null;

  let local = digits;
  if (local.startsWith("0020")) local = local.slice(4);
  else if (local.startsWith("20") && local.length >= 12) local = local.slice(2);
  if (!local.startsWith("0")) local = `0${local}`;

  // 010 Vodafone · 011 Etisalat · 012 Orange · 015 WE — all reachable through the wallet rail.
  if (!/^01[0125]\d{8}$/.test(local)) return null;
  return local;
}

async function post(path: string, body: unknown): Promise<Record<string, unknown>> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`PAYMOB_${res.status}: ${text.slice(0, 300)}`);
  return JSON.parse(text) as Record<string, unknown>;
}

export interface CheckoutInput {
  orderId: string;
  amount: number;
  currency: string;
  description: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  /** Paymob integration to charge through; falls back to the configured default. */
  integrationId?: string | null;
  /** ISO-2 country of the buyer, passed through as billing data. */
  country?: string | null;
}

export interface CheckoutSession {
  checkoutUrl: string;
  providerRef: string;
}

/** The four Egyptian mobile-wallet operators, kept as a closed set the app can style. */
export type WalletBrand = "VODAFONE" | "ORANGE" | "ETISALAT" | "WE";

/** A payment option that actually exists on the merchant's Paymob account. */
export interface PaymentMethod {
  id: string;
  kind: string;
  label: string;
  currency: string;
  live: boolean;
  /**
   * Which operator a WALLET integration bills, when Paymob's own naming says so.
   *
   * Null means the account has one generic wallet rail that serves every operator — the normal
   * Paymob setup. It is deliberately NOT guessed from anything but the merchant's real
   * integration data: the number prefix is a UI hint only (see `walletBrandFromPhone`), never a
   * reason to route a payment somewhere Paymob did not say it belongs.
   */
  walletBrand: WalletBrand | null;
}

/**
 * Reads the operator out of a Paymob integration's own name.
 *
 * Accounts that enable each operator separately name them plainly ("Vodafone Cash", "Orange
 * Money", "Etisalat Cash", "WE Pay"); accounts on the single unified wallet rail do not, and get
 * null so the app shows one wallet option instead of inventing four.
 */
export function walletBrandFromName(name: string): WalletBrand | null {
  const hay = name.toLowerCase();
  if (hay.includes("vodafone") || hay.includes("vf ")) return "VODAFONE";
  if (hay.includes("orange")) return "ORANGE";
  if (hay.includes("etisalat") || hay.includes("e& ") || hay.includes("e&money")) return "ETISALAT";
  // "WE" is a real word, so it is only read as the operator when it stands alone or is paired
  // with a wallet word — never inside "wemoney-like" substrings of unrelated names.
  if (/\bwe\b/.test(hay) && (hay.includes("wallet") || hay.includes("pay") || hay.includes("cash"))) {
    return "WE";
  }
  return null;
}

/**
 * The operator that issued an Egyptian mobile number, by prefix.
 *
 * 010 Vodafone · 011 Etisalat · 012 Orange · 015 WE. This is a presentation aid — it colours the
 * field while the payer types — and is never used to pick which Paymob integration to charge.
 */
export function walletBrandFromPhone(phone: string): WalletBrand | null {
  const digits = String(phone ?? "").replace(/\D/g, "");
  const local = digits.startsWith("20") && digits.length >= 12 ? `0${digits.slice(2)}` : digits;
  if (!local.startsWith("01") || local.length < 3) return null;
  switch (local.slice(0, 3)) {
    case "010": return "VODAFONE";
    case "011": return "ETISALAT";
    case "012": return "ORANGE";
    case "015": return "WE";
    default: return null;
  }
}

/** Families the app can name and draw an icon for; anything else keeps its dashboard name. */
export const KNOWN_KINDS = ["CARD", "WALLET", "INSTAPAY", "MEEZA", "KIOSK", "INSTALLMENT"] as const;

/**
 * Classifies a Paymob integration into one of the method families the app knows how to present.
 *
 * Paymob names the same product differently per account ("Mobile Wallets", "Wallet EGP", …) and
 * `gateway_type` is not a closed set, so both fields are matched. Anything unrecognised is kept
 * and shown under its own dashboard name rather than hidden or renamed.
 */
function classify(gatewayType: string, name: string): string {
  const hay = `${gatewayType} ${name}`.toLowerCase();
  if (hay.includes("instapay") || hay.includes("ipn_")) return "INSTAPAY";
  if (hay.includes("meeza")) return "MEEZA";
  if (hay.includes("wallet") || hay.includes("uig") || hay.includes("vodafone") || hay.includes("orange") || hay.includes("etisalat")) {
    return "WALLET";
  }
  if (hay.includes("kiosk") || hay.includes("aman") || hay.includes("masary")) return "KIOSK";
  if (hay.includes("valu") || hay.includes("sympl") || hay.includes("souhoola") || hay.includes("installment")) {
    return "INSTALLMENT";
  }
  if (hay.includes("migs") || hay.includes("card") || hay.includes("cib") || hay.includes("moto") || hay.includes("3ds")) {
    return "CARD";
  }
  return "OTHER";
}

async function authToken(cfg: PaymobConfig): Promise<string> {
  const auth = await post("/api/auth/tokens", { api_key: cfg.apiKey });
  return auth.token as string;
}

/**
 * Reads the payment methods enabled on the merchant's own Paymob account.
 *
 * Nothing here is hardcoded: whatever the account has switched on — card, mobile wallet,
 * InstaPay, Meeza, kiosk — is what the checkout offers. If the listing endpoint is not
 * reachable the caller falls back to the single configured integration.
 */
export async function listPaymentMethods(cfg: PaymobConfig): Promise<PaymentMethod[]> {
  const token = await authToken(cfg);
  const res = await fetch(`${BASE}/api/ecommerce/integrations?page_size=200`, {
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`PAYMOB_INTEGRATIONS_${res.status}`);

  const body = JSON.parse(await res.text()) as Record<string, unknown>;
  const rows = (Array.isArray(body.results) ? body.results : []) as Record<string, unknown>[];

  return rows
    .filter((row) => row.is_deprecated !== true && row.is_active !== false)
    .map((row) => {
      const gatewayType = String(row.gateway_type ?? "");
      const name = String(row.integration_name ?? row.name ?? gatewayType);
      const kind = classify(gatewayType, name);
      return {
        id: String(row.id),
        kind,
        label: name,
        currency: String(row.currency ?? "EGP").toUpperCase(),
        live: row.is_live !== false,
        // Only a wallet integration can carry an operator, and only when Paymob's own naming
        // identifies one. Everything else stays null.
        walletBrand: kind === "WALLET" ? walletBrandFromName(name) : null,
      };
    })
    .filter((method) => method.id && method.id !== "undefined");
}

/** Creates the Paymob order plus the payment key both checkout styles are built on. */
async function createPaymentToken(
  cfg: PaymobConfig,
  input: CheckoutInput,
): Promise<{ paymentToken: string; paymobOrderId: string }> {
  const token = await authToken(cfg);

  const amountCents = Math.round(input.amount * 100);
  const order = await post("/api/ecommerce/orders", {
    auth_token: token,
    delivery_needed: false,
    amount_cents: amountCents,
    currency: input.currency,
    merchant_order_id: input.orderId,
    items: [
      {
        name: input.description.slice(0, 60),
        amount_cents: amountCents,
        description: input.description.slice(0, 120),
        quantity: 1,
      },
    ],
  });
  const paymobOrderId = String(order.id);

  const billing = {
    first_name: input.firstName || "Student",
    last_name: input.lastName || "7PRO",
    email: input.email || "student@7pro.app",
    phone_number: input.phone || "+20000000000",
    apartment: "NA",
    floor: "NA",
    street: "NA",
    building: "NA",
    shipping_method: "NA",
    postal_code: "NA",
    city: "NA",
    country: (input.country ?? "EG").toUpperCase(),
    state: "NA",
  };

  const key = await post("/api/acceptance/payment_keys", {
    auth_token: token,
    amount_cents: amountCents,
    expiration: 3600,
    order_id: paymobOrderId,
    billing_data: billing,
    currency: input.currency,
    integration_id: Number(input.integrationId ?? cfg.integrationId),
    lock_order_when_paid: true,
  });

  return { paymentToken: key.token as string, paymobOrderId };
}

/** Creates a hosted Paymob checkout session (card / iframe) for a pending 7PRO order. */
export async function createCheckout(cfg: PaymobConfig, input: CheckoutInput): Promise<CheckoutSession> {
  const { paymentToken, paymobOrderId } = await createPaymentToken(cfg, input);
  return {
    checkoutUrl: `${BASE}/api/acceptance/iframes/${cfg.iframeId}?payment_token=${paymentToken}`,
    providerRef: paymobOrderId,
  };
}

/** Outcome of charging a mobile wallet directly. */
export interface WalletCharge {
  providerRef: string;
  /** Page the wallet asks the payer to finish on, when the operator returns one. */
  redirectUrl: string | null;
  /** True while the payer still has to approve the request on their phone. */
  pending: boolean;
  /** Set when the wallet refused the request outright. */
  declineReason: string | null;
}

/**
 * Charges a mobile wallet — Vodafone Cash and its Orange / Etisalat / WE siblings.
 *
 * Wallets are not an iframe product: the request is sent straight to the number, and the payer
 * approves it inside their wallet app or over USSD. Paymob answers immediately with a pending
 * transaction, so nothing is unlocked here — the signed webhook remains the only thing that
 * marks the order paid.
 */
export async function chargeWallet(
  cfg: PaymobConfig,
  input: CheckoutInput & { walletPhone: string },
): Promise<WalletCharge> {
  const { paymentToken, paymobOrderId } = await createPaymentToken(cfg, input);

  const res = await post("/api/acceptance/payments/pay", {
    source: { identifier: input.walletPhone, subtype: "WALLET" },
    payment_token: paymentToken,
  });

  const redirect = [res.redirect_url, res.iframe_redirection_url]
    .map((v) => (typeof v === "string" ? v.trim() : ""))
    .find((v) => v.startsWith("http")) ?? null;

  const pending = res.pending === true || res.pending === "true";
  const success = res.success === true || res.success === "true";
  const dataMessage = typeof (res.data as Record<string, unknown> | undefined)?.message === "string"
    ? String((res.data as Record<string, unknown>).message)
    : null;

  return {
    providerRef: paymobOrderId,
    redirectUrl: redirect,
    pending: pending || (!success && redirect !== null),
    // A wallet that refuses on the spot (unregistered number, blocked account) is neither
    // pending nor successful — say so instead of leaving the payer waiting forever.
    declineReason: !pending && !success ? (dataMessage ?? "WALLET_REFUSED") : null,
  };
}

/** Field order mandated by Paymob for HMAC calculation. */
const HMAC_FIELDS = [
  "amount_cents",
  "created_at",
  "currency",
  "error_occured",
  "has_parent_transaction",
  "id",
  "integration_id",
  "is_3d_secure",
  "is_auth",
  "is_capture",
  "is_refunded",
  "is_standalone_payment",
  "is_voided",
  "order.id",
  "owner",
  "pending",
  "source_data.pan",
  "source_data.sub_type",
  "source_data.type",
  "success",
];

function pick(obj: Record<string, unknown>, path: string): string {
  const value = path.split(".").reduce<unknown>((acc, part) => {
    if (acc && typeof acc === "object") return (acc as Record<string, unknown>)[part];
    return undefined;
  }, obj);
  if (value === null || value === undefined) return "";
  if (typeof value === "boolean") return value ? "true" : "false";
  return String(value);
}

async function hmacSha512(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-512" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(message));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/** Verifies the Paymob callback signature. Rejects anything unsigned or tampered with. */
export async function verifyHmac(
  cfg: PaymobConfig,
  transaction: Record<string, unknown>,
  providedHmac: string,
): Promise<boolean> {
  if (!providedHmac) return false;
  const concatenated = HMAC_FIELDS.map((f) => pick(transaction, f)).join("");
  const expected = await hmacSha512(cfg.hmacSecret, concatenated);
  return timingSafeEqual(expected.toLowerCase(), providedHmac.toLowerCase());
}
