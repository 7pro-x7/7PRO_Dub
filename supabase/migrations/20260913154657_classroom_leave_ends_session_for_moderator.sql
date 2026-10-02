-- Fix: clicking "leave" (مغادرة) did not end the meeting for the other side.
--
-- classroom_leave only logged that the calling user left and flipped THEIR
-- OWN classroom_participants row to LEFT. It never touched
-- classroom_sessions.status. Every client listens for an UPDATE on
-- classroom_sessions to know the class is over and auto-leave, so when the
-- status never changed, a teacher leaving left students sitting in an empty
-- call with no signal to disconnect.
--
-- Fix: when the user calling classroom_leave is a moderator for the session
-- (classroom_can_manage — teacher or staff with classroom.manage), leaving
-- now ends the session for everyone, same as classroom_end_session. A
-- student leaving still only affects their own participant row, as before.
-- The pre-existing set_config('app.classroom_join_bypass', ...) call is kept
-- unchanged.

CREATE OR REPLACE FUNCTION classroom_leave(p_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_is_manager BOOLEAN := classroom_can_manage(p_session_id);
BEGIN
  PERFORM set_config('app.classroom_join_bypass', p_session_id::text, true);

  UPDATE classroom_participants
  SET status = CASE WHEN status = 'JOINED' THEN 'LEFT' ELSE status END,
      left_at = now()
  WHERE session_id = p_session_id AND user_id = auth.uid();

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  SELECT p_session_id, auth.uid(), 'LEAVE'
  WHERE classroom_is_participant(p_session_id)
     OR v_is_manager;

  -- A moderator leaving ends the class for everyone still in it.
  IF v_is_manager THEN
    UPDATE classroom_sessions
    SET status = 'ENDED', ended_at = now()
    WHERE id = p_session_id AND status IN ('LIVE', 'SCHEDULED');

    UPDATE classroom_participants
    SET status = 'LEFT', left_at = now()
    WHERE session_id = p_session_id AND status = 'JOINED';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION classroom_leave(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_leave(UUID) TO authenticated;
;
