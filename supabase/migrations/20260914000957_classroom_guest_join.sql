-- ============================================================================
-- classroom_guest_join — lets a visitor who opened a
-- .../classroom?session=<id> link join THAT session directly, with no
-- sign-in form, via a Supabase Anonymous Auth session created client-side.
--
-- Scope, on purpose:
--   * Only usable by an anonymous auth session (auth.jwt() ->> 'is_anonymous'
--     = 'true'). A normal signed-in account still goes through classroom_join
--     and still needs a real invite — this function does not weaken that path
--     at all.
--   * Only ever adds the caller as STUDENT to the ONE session_id passed in —
--     never TEACHER/MODERATOR, and it cannot be used to browse or list other
--     sessions (classroom_sessions_select RLS still applies for everything
--     else; this RPC only ever touches the single row it's given).
--   * Once this runs, the guest has a real classroom_participants row, so
--     every existing RLS policy (classroom_can_view / classroom_is_participant)
--     treats them exactly like an invited participant for chat, whiteboard,
--     and presence — no other policy anywhere had to change.
--   * Same session-status / time-window / capacity checks as classroom_join,
--     so a canceled/ended/full/expired session still can't be joined by guests.
-- ============================================================================
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
GRANT EXECUTE ON FUNCTION classroom_guest_join(UUID) TO authenticated;
;
