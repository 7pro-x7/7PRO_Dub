-- ============================================================================
-- Defense in depth: no anonymous access to subscription/finance endpoints.
--
-- These functions all do check the caller internally (has_permission(...),
-- is_approved_teacher(...), auth.uid() IS NULL -> UNAUTHORIZED), so an
-- anonymous call already failed. But there is no reason for a logged-out
-- visitor to be able to reach a money endpoint at all: leaving the grant in
-- place means one future edit that forgets its permission check turns
-- straight into an unauthenticated hole, which is exactly how
-- _record_subscription_earning became exploitable.
--
-- `authenticated` is intentionally kept — these are the real teacher/admin
-- entry points the app calls; their internal role checks decide who may
-- actually proceed.
-- ============================================================================

REVOKE EXECUTE ON FUNCTION public.activate_subscription(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION public.renew_subscription(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION public.reject_subscription(uuid, text) FROM anon;
REVOKE EXECUTE ON FUNCTION public.activate_group(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION public.set_subscription_paused(uuid, boolean) FROM anon;
REVOKE EXECUTE ON FUNCTION public.create_subscription_request(text, text, text, date, numeric, date, text, text, text, text) FROM anon;
REVOKE EXECUTE ON FUNCTION public.set_teacher_subscription_rate(uuid, numeric) FROM anon;
REVOKE EXECUTE ON FUNCTION public.get_teacher_subscription_rate(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION public.subscription_stats(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION public.subscription_stats_all() FROM anon;
REVOKE EXECUTE ON FUNCTION public.subscription_earnings_summary(uuid, date, date) FROM anon;
REVOKE EXECUTE ON FUNCTION public.generate_subscription_monthly_report(uuid, integer, integer) FROM anon;

REVOKE EXECUTE ON FUNCTION public.process_approval_request(uuid, text, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_approval_requests(text, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.pending_request_counts() FROM PUBLIC, anon;

REVOKE EXECUTE ON FUNCTION public.request_payout(numeric, text, text, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.review_payout(uuid, text, text, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.teacher_balance(uuid) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.process_approval_request(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_approval_requests(text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.pending_request_counts() TO authenticated;
GRANT EXECUTE ON FUNCTION public.request_payout(numeric, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.review_payout(uuid, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.teacher_balance(uuid) TO authenticated;
;
