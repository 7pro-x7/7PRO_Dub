-- ============================================================================
-- Virtual Classroom — scheduled cleanup of old attendance/chat logs
--
-- Deletes rows from classroom_attendance_events and classroom_chat_messages
-- for sessions that are finished (ENDED or CANCELED) and have been finished
-- for at least p_retention_days. Deliberately narrow:
--   - Never touches SCHEDULED or LIVE sessions, no matter how old
--     `scheduled_end` is — only a session that has actually finished counts.
--   - Only clears the two "log" tables (attendance events, chat/Q&A) — the
--     sessions themselves, classroom_participants (who was invited/attended),
--     whiteboard boards/strokes and recordings are left alone.
--
-- service_role only: this is meant to be driven by the existing
-- backend/functions/maintenance endpoint (same MAINTENANCE_SECRET-gated
-- schedule as release_matured_earnings/expire_subscriptions), never by a
-- regular signed-in user — letting any `authenticated` caller wipe a class's
-- chat/attendance history on demand would be a straightforward abuse vector.
-- ============================================================================

CREATE OR REPLACE FUNCTION classroom_cleanup_old_logs(p_retention_days INTEGER DEFAULT 90)
RETURNS TABLE (deleted_attendance_events BIGINT, deleted_chat_messages BIGINT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_cutoff TIMESTAMPTZ;
  v_session_ids UUID[];
  v_attendance BIGINT := 0;
  v_chat BIGINT := 0;
BEGIN
  IF p_retention_days IS NULL OR p_retention_days < 1 THEN
    RAISE EXCEPTION 'INVALID_RETENTION_DAYS';
  END IF;

  v_cutoff := now() - (p_retention_days || ' days')::INTERVAL;

  SELECT array_agg(id) INTO v_session_ids
  FROM classroom_sessions
  WHERE status IN ('ENDED', 'CANCELED')
    AND COALESCE(ended_at, scheduled_end) < v_cutoff;

  IF v_session_ids IS NULL THEN
    RETURN QUERY SELECT 0::BIGINT, 0::BIGINT;
    RETURN;
  END IF;

  DELETE FROM classroom_attendance_events WHERE session_id = ANY(v_session_ids);
  GET DIAGNOSTICS v_attendance = ROW_COUNT;

  DELETE FROM classroom_chat_messages WHERE session_id = ANY(v_session_ids);
  GET DIAGNOSTICS v_chat = ROW_COUNT;

  RETURN QUERY SELECT v_attendance, v_chat;
END;
$$;

REVOKE ALL ON FUNCTION classroom_cleanup_old_logs(INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_cleanup_old_logs(INTEGER) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION classroom_cleanup_old_logs(INTEGER) TO service_role;
;
