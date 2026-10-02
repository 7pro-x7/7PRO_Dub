-- Applied on production as "subscription_active_earnings_volatile_fix".
-- subscription_active_earnings() calls refresh_subscription_statuses(), which UPDATEs
-- teacher_subscriptions. PostgREST runs STABLE functions in a READ ONLY transaction, so the RPC
-- always failed (25006) and the app showed "-" for subscription earnings. It writes, so it must
-- be VOLATILE. The definition lives in 20260920002323_teacher_balance_courses_only.sql (now
-- without STABLE); this file records the change that was applied to the live database.
ALTER FUNCTION public.subscription_active_earnings(uuid) VOLATILE;
