-- CRITICAL — internal money functions were callable by ANY user, even anonymous.
-- (Applied to project 7pro-x7 and verified.)
--
-- Confirmed live: _record_subscription_earning, _on_subscription_approved and
-- _on_subscription_renewed all had EXECUTE granted to BOTH anon and
-- authenticated, and PostgREST exposes every public function as an endpoint.
-- So this was a live, unauthenticated request:
--
--     POST /rest/v1/rpc/_record_subscription_earning
--     { "p_subscription_id": "<any id>", "p_period_start": "...", ... }
--
-- _record_subscription_earning is SECURITY DEFINER and has NO permission check
-- of its own -- by design, since it was only meant to be called from inside
-- approve/renew, which do check. Reachable directly it is a money printer:
-- each call inserts a teacher_ledger row that counts toward that teacher's
-- withdrawable balance. release_matured_earnings was granted to PUBLIC too.
--
-- An earlier migration (20260906163800_lock_down_internal_subscription_
-- functions) was meant to prevent this but did not hold: Supabase default
-- privileges re-grant EXECUTE to anon/authenticated, and every later
-- CREATE OR REPLACE of these functions silently restored the exposure.
--
-- Nothing legitimate breaks: these are invoked with SECURITY DEFINER rights
-- from activate_subscription / renew_subscription / approval processing, which
-- keep working. Only the direct REST entry point disappears.

REVOKE ALL ON FUNCTION public._record_subscription_earning(uuid, uuid, date, date, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._on_subscription_approved() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._on_subscription_renewed() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._assert_staff() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.release_matured_earnings() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.expire_subscriptions() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.refresh_subscription_statuses() FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public._record_subscription_earning(uuid, uuid, date, date, text) TO service_role;
GRANT EXECUTE ON FUNCTION public._on_subscription_approved() TO service_role;
GRANT EXECUTE ON FUNCTION public._on_subscription_renewed() TO service_role;
GRANT EXECUTE ON FUNCTION public._assert_staff() TO service_role;
GRANT EXECUTE ON FUNCTION public.release_matured_earnings() TO service_role;
GRANT EXECUTE ON FUNCTION public.expire_subscriptions() TO service_role;
GRANT EXECUTE ON FUNCTION public.refresh_subscription_statuses() TO service_role;
