-- ============================================================================
-- classroom_send_start_reminders() is meant to run only via pg_cron (or any
-- other job that executes as postgres/service_role), never called directly
-- by the app. Supabase's default schema privileges grant EXECUTE to
-- `authenticated` on every new function even after `REVOKE ... FROM PUBLIC`,
-- so it must be revoked from `authenticated` explicitly too.
-- ============================================================================
REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM authenticated;
REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM anon;
REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM PUBLIC;
