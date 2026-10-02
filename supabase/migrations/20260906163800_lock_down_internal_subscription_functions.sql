-- Security advisor flagged: internal helper functions (prefixed with `_`)
-- are SECURITY DEFINER and, by Postgres default, EXECUTE is granted to
-- PUBLIC (which includes the anon/unauthenticated role) unless revoked.
-- These helpers are only ever called internally (via PERFORM from other
-- SECURITY DEFINER functions/triggers) and were never meant to be reachable
-- as a public RPC endpoint. An anonymous caller invoking
-- _record_subscription_earning directly could credit arbitrary money to any
-- teacher's ledger. Revoke public execute on all of them.
REVOKE ALL ON FUNCTION public._assert_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._record_subscription_earning(UUID, UUID, DATE, DATE, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._on_subscription_approved() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._on_subscription_renewed() FROM PUBLIC;

-- Public-facing RPCs should require an authenticated session even though
-- each also checks role internally — defense in depth, and matches what the
-- app actually needs (anon never calls any of these).
REVOKE ALL ON FUNCTION public.activate_subscription(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.activate_subscription(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.reject_subscription(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.reject_subscription(UUID, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.renew_subscription(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.renew_subscription(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.set_subscription_paused(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_subscription_paused(UUID, BOOLEAN) TO authenticated;

REVOKE ALL ON FUNCTION public.set_teacher_subscription_rate(UUID, NUMERIC) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_teacher_subscription_rate(UUID, NUMERIC) TO authenticated;

REVOKE ALL ON FUNCTION public.get_teacher_subscription_rate(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_teacher_subscription_rate(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.create_subscription_request(TEXT, TEXT, TEXT, DATE, NUMERIC, DATE, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_subscription_request(TEXT, TEXT, TEXT, DATE, NUMERIC, DATE, TEXT, TEXT, TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.subscription_earnings_summary(UUID, DATE, DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.subscription_earnings_summary(UUID, DATE, DATE) TO authenticated;

REVOKE ALL ON FUNCTION public.generate_subscription_monthly_report(UUID, INT, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.generate_subscription_monthly_report(UUID, INT, INT) TO authenticated;

REVOKE ALL ON FUNCTION public.refresh_subscription_statuses() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.refresh_subscription_statuses() TO authenticated;

REVOKE ALL ON FUNCTION public.subscription_stats(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.subscription_stats(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.subscription_stats_all() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.subscription_stats_all() TO authenticated;

-- expire_subscriptions is invoked by the maintenance edge function using the
-- service_role key, plus kept available to authenticated for parity with
-- before.
REVOKE ALL ON FUNCTION public.expire_subscriptions() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.expire_subscriptions() TO authenticated, service_role;
;
