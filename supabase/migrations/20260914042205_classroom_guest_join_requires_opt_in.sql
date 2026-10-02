-- ============================================================================
-- 7PRO Virtual Classroom — gate guest join behind a per-session opt-in
--
-- classroom_guest_join() (20260914000957) lets any Anonymous-Auth session
-- join whichever session_id it is given, with no opt-in check at all: the
-- link alone was enough for ANY session, live or scheduled, mic/camera and
-- all. That was the missing piece — the teacher/admin never got a say in
-- whether their session accepts guests. This migration:
--   1. Adds classroom_sessions.allow_guests (default false — guests are
--      opt-in, not opt-out; every existing session stays invite-only until
--      its teacher turns this on).
--   2. Makes classroom_guest_join() require it.
--   3. Adds classroom_set_guest_access(), the same shape as the existing
--      classroom_set_all_mics_locked() toggle, so only the teacher/staff who
--      can manage the session may turn it on or off.
--   4. Explicitly revokes classroom_guest_join/classroom_set_guest_access
--      from `anon` — Supabase grants EXECUTE on every new function to `anon`
--      directly, and REVOKE ALL FROM PUBLIC does not strip that (see
--      20260908120400's own note). classroom_guest_join already checks
--      auth.jwt()->>'is_anonymous', which is NULL/false for a truly signed-out
--      `anon` caller, so this is defense in depth, not a functional fix —
--      but it matches the "every classroom_* function must be explicitly
--      revoked from anon" rule the rest of this feature follows.
-- ============================================================================

ALTER TABLE classroom_sessions ADD COLUMN IF NOT EXISTS allow_guests BOOLEAN NOT NULL DEFAULT false;

CREATE OR REPLACE FUNCTION classroom_guest_join(p_session_id UUID)
RETURNS TABLE (
  room_name TEXT,
  room_password TEXT,
  jitsi_domain TEXT,
  role_in_session TEXT,
  is_moderator BOOLEAN,
  display_name TEXT
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_session RECORD;
  v_participant RECORD;
  v_live_count INTEGER;
BEGIN
  IF COALESCE((auth.jwt() ->> 'is_anonymous')::boolean, false) IS NOT TRUE THEN
    RAISE EXCEPTION 'GUEST_JOIN_REQUIRES_ANONYMOUS_SESSION';
  END IF;

  SELECT * INTO v_session FROM classroom_sessions WHERE id = p_session_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SESSION_NOT_FOUND';
  END IF;

  IF NOT v_session.allow_guests THEN
    RAISE EXCEPTION 'GUEST_ACCESS_NOT_ENABLED';
  END IF;

  IF v_session.status = 'CANCELED' THEN
    RAISE EXCEPTION 'SESSION_CANCELED';
  END IF;
  IF v_session.status = 'ENDED' THEN
    RAISE EXCEPTION 'SESSION_ENDED';
  END IF;

  IF now() < v_session.scheduled_start - INTERVAL '10 minutes' THEN
    RAISE EXCEPTION 'TOO_EARLY';
  END IF;
  IF now() > v_session.scheduled_end + INTERVAL '30 minutes' AND v_session.status != 'LIVE' THEN
    RAISE EXCEPTION 'SESSION_WINDOW_PASSED';
  END IF;

  SELECT * INTO v_participant
  FROM classroom_participants
  WHERE session_id = p_session_id AND user_id = auth.uid();

  IF FOUND AND v_participant.status = 'KICKED' THEN
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  SELECT count(*) INTO v_live_count
  FROM classroom_participants
  WHERE session_id = p_session_id AND status = 'JOINED';

  IF v_live_count >= v_session.max_participants THEN
    RAISE EXCEPTION 'SESSION_FULL';
  END IF;

  INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, invited_by, joined_at)
  VALUES (p_session_id, auth.uid(), 'STUDENT', 'JOINED', v_session.teacher_id, now())
  ON CONFLICT (session_id, user_id) DO UPDATE
    SET status = 'JOINED', joined_at = now(), left_at = NULL;

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  VALUES (p_session_id, auth.uid(), 'JOIN');

  IF v_session.status = 'SCHEDULED' THEN
    UPDATE classroom_sessions SET status = 'LIVE', started_at = now() WHERE id = p_session_id;
  END IF;

  RETURN QUERY
  SELECT
    v_session.room_name,
    v_session.room_password,
    v_session.jitsi_domain,
    'STUDENT'::TEXT,
    false,
    'زائر'::TEXT;
END;
$$;

REVOKE ALL ON FUNCTION classroom_guest_join(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_guest_join(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_guest_join(UUID) TO authenticated;

-- Toggle used by the teacher/staff who manages the session — same shape as
-- classroom_set_all_mics_locked().
CREATE OR REPLACE FUNCTION classroom_set_guest_access(p_session_id UUID, p_allow BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_sessions SET allow_guests = p_allow WHERE id = p_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_guest_access(UUID, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_set_guest_access(UUID, BOOLEAN) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_set_guest_access(UUID, BOOLEAN) TO authenticated;
;
