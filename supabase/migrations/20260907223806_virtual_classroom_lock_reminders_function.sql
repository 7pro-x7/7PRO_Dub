REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM authenticated;
REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM anon;
REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM PUBLIC;
-- Intentionally left with no grants except to postgres/service_role (the
-- owner), which is what a pg_cron job runs as.;
