-- ============================================================================
-- CRITICAL — internal money functions were callable by ANY user, even anonymous
--
-- Confirmed against the live database: _record_subscription_earning,
-- _on_subscription_approved and _on_subscription_renewed all had
-- EXECUTE granted to BOTH `anon` and `authenticated`, and PostgREST exposes
-- every public function as an endpoint. So this was a live, unauthenticated
-- request:
--
--     POST /rest/v1/rpc/_record_subscription_earning
--     { "p_subscription_id": "<any id>", "p_period_start": "...", ... }
--
-- _record_subscription_earning is SECURITY DEFINER and contains NO permission
-- check of its own — by design, because it was only ever meant to be called
-- from inside approve/renew, which do check. Reachable directly, it becomes a
-- money printer: each call inserts a teacher_ledger row that immediately
-- counts toward that teacher's withdrawable balance. release_matured_earnings
-- was even granted to PUBLIC on top of that.
--
-- (An earlier migration, 20260906163800_lock_down_internal_subscription_
-- functions, was clearly meant to prevent this. It did not hold: Supabase's
-- default privileges re-grant EXECUTE on new functions to anon/authenticated,
-- and every later CREATE OR REPLACE of these functions silently restored the
-- exposure. That is why this migration also removes the DEFAULT PRIVILEGE
-- itself for the underscore-prefixed internal helpers going forward, instead
-- of only revoking once.)
--
-- Nothing legitimate breaks: these are called with SECURITY DEFINER rights
-- from inside activate_subscription / renew_subscription / approval
-- processing, which continue to work exactly as before. Only the direct REST
-- entry point disappears.
-- ============================================================================

REVOKE ALL ON FUNCTION public._record_subscription_earning(uuid, uuid, date, date, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._on_subscription_approved() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._on_subscription_renewed() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._assert_staff() FROM PUBLIC, anon, authenticated;

-- Scheduled-maintenance functions: driven by the maintenance edge function
-- under service_role. A signed-in user has no business triggering a global
-- sweep of every subscription's status or releasing matured earnings early.
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

-- request_payout() calls release_matured_earnings() internally. That still
-- works after the revoke above because request_payout is SECURITY DEFINER and
-- runs as its owner, not as the calling user.
;
