import { corsHeaders, createAdminClient, json } from "../_shared/auth.ts";
import { readPaymobConfig, verifyHmac } from "../_shared/paymob.ts";

/**
 * Paymob transaction callback. This is the ONLY path that can grant paid access.
 * Signature is verified before anything touches the database, and every event id
 * is recorded so replayed callbacks are ignored.
 */
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);

  const cfg = readPaymobConfig();
  if (!cfg) {
    console.error("webhook_gateway_not_configured");
    return json({ error: "GATEWAY_NOT_CONFIGURED" }, 503);
  }

  let payload: Record<string, unknown>;
  try {
    payload = (await req.json()) as Record<string, unknown>;
  } catch {
    return json({ error: "BAD_PAYLOAD" }, 400);
  }

  const url = new URL(req.url);
  const hmac = url.searchParams.get("hmac") ?? String(payload.hmac ?? "");
  const transaction = (payload.obj ?? payload) as Record<string, unknown>;

  const valid = await verifyHmac(cfg, transaction, hmac);
  if (!valid) {
    console.error("webhook_invalid_signature", { id: transaction.id });
    return json({ error: "INVALID_SIGNATURE" }, 401);
  }

  const order = (transaction.order ?? {}) as Record<string, unknown>;
  const merchantOrderId = String(order.merchant_order_id ?? "");
  const transactionId = String(transaction.id ?? "");
  const success = transaction.success === true || transaction.success === "true";
  const pending = transaction.pending === true || transaction.pending === "true";
  const refunded = transaction.is_refunded === true || transaction.is_refunded === "true";
  const voided = transaction.is_voided === true || transaction.is_voided === "true";

  if (!merchantOrderId) return json({ error: "MISSING_ORDER_REF" }, 400);

  const admin = createAdminClient();

  try {
    if (refunded || voided) {
      const { data: existing } = await admin
        .from("orders")
        .select("id, total_amount, refunded_amount, status")
        .eq("id", merchantOrderId)
        .maybeSingle();
      if (existing && existing.status === "PAID") {
        await admin
          .from("payment_events")
          .insert({
            provider: "PAYMOB",
            provider_event_id: `refund_${transactionId}`,
            order_id: merchantOrderId,
            event_type: refunded ? "REFUNDED" : "VOIDED",
            payload: transaction,
            processed_at: new Date().toISOString(),
          });
      }
      return json({ ok: true, handled: "REFUND_NOTICE" });
    }

    if (success && !pending) {
      // A correct signature only proves the callback came from Paymob — not that it is the
      // callback for THIS order at THIS price. The amount and currency are compared against the
      // order the server itself priced, so a genuine transaction for a cheap order can never be
      // replayed against an expensive one, and a short payment never unlocks anything.
      const { data: expected } = await admin
        .from("orders")
        .select("total_amount, currency, status")
        .eq("id", merchantOrderId)
        .maybeSingle();

      if (!expected) {
        console.error("webhook_unknown_order", { merchantOrderId, transactionId });
        return json({ error: "UNKNOWN_ORDER" }, 404);
      }

      const paidCents = Math.round(Number(transaction.amount_cents ?? 0));
      const owedCents = Math.round(Number(expected.total_amount ?? 0) * 100);
      const paidCurrency = String(transaction.currency ?? "").toUpperCase();
      const owedCurrency = String(expected.currency ?? "").toUpperCase();

      if (paidCents !== owedCents || (paidCurrency && paidCurrency !== owedCurrency)) {
        console.error("webhook_amount_mismatch", {
          merchantOrderId,
          transactionId,
          paidCents,
          owedCents,
          paidCurrency,
          owedCurrency,
        });
        await admin.rpc("fail_order_payment", {
          p_order_id: merchantOrderId,
          p_provider: "PAYMOB",
          p_event_id: `mismatch_${transactionId}`,
          p_status: "PAYMENT_FAILED",
          p_reason: "The amount confirmed by the provider did not match this order.",
          p_payload: transaction,
        });
        return json({ error: "AMOUNT_MISMATCH" }, 400);
      }

      const { data, error } = await admin.rpc("confirm_order_payment", {
        p_order_id: merchantOrderId,
        p_provider: "PAYMOB",
        p_provider_ref: transactionId,
        p_event_id: `txn_${transactionId}`,
        p_payload: transaction,
      });
      if (error) {
        console.error("confirm_failed", error.message);
        return json({ error: "CONFIRM_FAILED" }, 500);
      }
      return json({ ok: true, result: data });
    }

    if (pending) return json({ ok: true, handled: "PENDING" });

    const { error } = await admin.rpc("fail_order_payment", {
      p_order_id: merchantOrderId,
      p_provider: "PAYMOB",
      p_event_id: `txn_${transactionId}`,
      p_status: "PAYMENT_FAILED",
      p_reason: String(
        (transaction.data as Record<string, unknown> | undefined)?.message ??
          "The payment was declined by the provider.",
      ),
      p_payload: transaction,
    });
    if (error) console.error("fail_order_error", error.message);
    return json({ ok: true, handled: "FAILED" });
  } catch (err) {
    console.error("webhook_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
