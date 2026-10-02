-- ============================================================================
-- classroom_join must tell the caller whether their mic/camera is already
-- locked (from a standing "lock all mics" or a per-student lock from an
-- earlier session) BEFORE the app ever opens the Jitsi conference.
--
-- Without this, a locked student's device could only find out and react
-- *after* joining unmuted — fetching classroom_participants asynchronously,
-- then sending a mute command that races against the Jitsi SDK's own
-- internal command receiver being ready. In the worst case that race is
-- lost and the "lock" silently does nothing; even when it wins, the
-- student's mic is briefly live the instant they join. Returning the lock
-- state as part of classroom_join lets the app pass it straight into the
-- conference's own startAudioMuted/startVideoMuted options, so a locked
-- participant simply never joins unmuted in the first place.
--
-- The return shape is changing (two new columns), which Postgres does not
-- allow via CREATE OR REPLACE — the function has to be dropped and recreated.
-- ============================================================================

DROP FUNCTION IF EXISTS classroom_join(UUID);

CREATE FUNCTION classroom_join(p_session_id UUID)
RETURNS TABLE (
  room_name TEXT,
  room_password TEXT,
  jitsi_domain TEXT,
  role_in_session TEXT,
  is_moderator BOOLEAN,
  display_name TEXT,
  mic_locked BOOLEAN,
  camera_locked BOOLEAN
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_session RECORD;
  v_participant RECORD;
  v_is_manager BOOLEAN := classroom_can_manage(p_session_id);
  v_live_count INTEGER;
  v_name TEXT;
  v_mic_locked BOOLEAN;
  v_camera_locked BOOLEAN;
BEGIN
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

  IF NOT v_is_manager THEN
    SELECT * INTO v_participant
    FROM classroom_participants
    WHERE session_id = p_session_id AND user_id = auth.uid();

    IF NOT FOUND OR v_participant.status = 'KICKED' THEN
      RAISE EXCEPTION 'NOT_AUTHORIZED';
    END IF;

    -- Allow joining from 10 minutes before the scheduled start until the
    -- scheduled end, so a slightly early arrival is not blocked but a stale
    -- link cannot be reused indefinitely.
    IF now() < v_session.scheduled_start - INTERVAL '10 minutes' THEN
      RAISE EXCEPTION 'TOO_EARLY';
    END IF;
    IF now() > v_session.scheduled_end + INTERVAL '30 minutes' AND v_session.status != 'LIVE' THEN
      RAISE EXCEPTION 'SESSION_WINDOW_PASSED';
    END IF;

    SELECT count(*) INTO v_live_count
    FROM classroom_participants
    WHERE session_id = p_session_id AND status = 'JOINED';

    IF v_live_count >= v_session.max_participants THEN
      RAISE EXCEPTION 'SESSION_FULL';
    END IF;
  END IF;

  -- Make sure the caller has a participant row even if they are staff
  -- joining a session they did not create (observer access). mic_locked /
  -- camera_locked are deliberately left untouched on conflict — a
  -- reconnecting student keeps whatever lock state they already had rather
  -- than it resetting — and default from the BEFORE INSERT trigger
  -- (classroom_apply_session_mic_lock) on a genuinely fresh row.
  INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, invited_by, joined_at)
  VALUES (
    p_session_id, auth.uid(),
    CASE WHEN v_session.teacher_id = auth.uid() THEN 'TEACHER'
         WHEN v_is_manager THEN 'MODERATOR'
         ELSE 'STUDENT' END,
    'JOINED', auth.uid(), now()
  )
  ON CONFLICT (session_id, user_id) DO UPDATE
    SET status = 'JOINED', joined_at = now(), left_at = NULL;

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  VALUES (p_session_id, auth.uid(), 'JOIN');

  IF v_session.status = 'SCHEDULED' THEN
    UPDATE classroom_sessions SET status = 'LIVE', started_at = now() WHERE id = p_session_id;
  END IF;

  SELECT COALESCE(p.full_name, p.email, 'Learner') INTO v_name FROM profiles p WHERE p.id = auth.uid();

  SELECT cp.mic_locked, cp.camera_locked INTO v_mic_locked, v_camera_locked
  FROM classroom_participants cp
  WHERE cp.session_id = p_session_id AND cp.user_id = auth.uid();

  RETURN QUERY
  SELECT
    v_session.room_name,
    v_session.room_password,
    v_session.jitsi_domain,
    CASE WHEN v_session.teacher_id = auth.uid() THEN 'TEACHER'
         WHEN v_is_manager THEN 'MODERATOR'
         ELSE 'STUDENT' END,
    (v_session.teacher_id = auth.uid() OR v_is_manager),
    v_name,
    COALESCE(v_mic_locked, false),
    COALESCE(v_camera_locked, false);
END;
$$;

REVOKE ALL ON FUNCTION classroom_join(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_join(UUID) TO authenticated;
