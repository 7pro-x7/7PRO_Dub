-- Bug: classroom_leave() ends the session for EVERYONE the instant a moderator's
-- row is marked LEFT, regardless of why that happened. That's correct for a
-- deliberate "Leave"/"End for everyone" tap, but classroom_leave is also the RPC
-- fired automatically when a moderator's connection merely drops for a moment:
--
--   - Android: Jitsi's CONFERENCE_TERMINATED broadcast (network blip, OS
--     reclaiming the background connection) calls vm.leave() unconditionally.
--   - Android: ClassroomCallViewModel.onCleared() calls leave() as a safety net
--     when the Activity is torn down by the system while backgrounded — not by
--     any user action.
--   - Web: the `beforeunload` handler fires classroom_leave via sendBeacon, which
--     some mobile browsers/webviews trigger just from backgrounding a tab.
--
-- In every one of those cases the teacher had not chosen to end the class, but
-- classroom_sessions.status flipped to ENDED anyway — so returning to the app a
-- moment later hit classroom_join()'s SESSION_ENDED guard ("انتهت هذه الحصة
-- بالفعل") even though nobody ended anything.
--
-- Fix: classroom_leave() takes a new p_intentional flag (default TRUE, so every
-- existing single-argument caller keeps today's behavior unchanged). Only a
-- moderator's INTENTIONAL leave ends the session; an automatic one just records
-- the moderator as LEFT and leaves the session LIVE so they (or Jitsi's own
-- reconnect) can rejoin without kicking anyone out. Ending the class for
-- everyone now only ever happens via a deliberate action: the on-screen
-- Leave/End-for-everyone flow (which already funnels through this with
-- p_intentional=true, or through classroom_end_session directly).

DROP FUNCTION IF EXISTS classroom_leave(UUID);

CREATE OR REPLACE FUNCTION classroom_leave(p_session_id UUID, p_intentional BOOLEAN DEFAULT TRUE)
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

  -- Only a deliberate moderator leave ends the class for everyone still in it.
  -- An automatic leave (p_intentional = false) never touches classroom_sessions.
  IF v_is_manager AND p_intentional THEN
    UPDATE classroom_sessions
    SET status = 'ENDED', ended_at = now()
    WHERE id = p_session_id AND status IN ('LIVE', 'SCHEDULED');

    UPDATE classroom_participants
    SET status = 'LEFT', left_at = now()
    WHERE session_id = p_session_id AND status = 'JOINED';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION classroom_leave(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_leave(UUID, BOOLEAN) TO authenticated;
