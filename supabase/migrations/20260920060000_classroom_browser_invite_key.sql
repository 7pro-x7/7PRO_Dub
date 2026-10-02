-- ============================================================================
-- Virtual Classroom — browser invite key WITHOUT joining the session
--
-- Why: phones below Android 8 cannot run the in-app video (the Jitsi SDK needs
-- API 26), so the app sends them to the web classroom instead, straight into
-- the session via an invite link (?session=<id>&key=<room password>), which the
-- page accepts through classroom_guest_join().
--
-- The only place the room password used to come from was classroom_join(),
-- which also marks the caller JOINED and writes a JOIN attendance event. Using
-- it just to build a link made the student count as present under their own
-- account while they were really in the browser as an anonymous guest — i.e. the
-- same person twice in the teacher's participant list.
--
-- This returns the key and nothing else: no participant row is created or
-- changed, no attendance event, no status flip. Same authorization as joining:
--   * the session's teacher, or staff with classroom.manage  -> allowed
--   * a participant invited to the session and not kicked     -> allowed
--   * anyone else                                              -> NOT_AUTHORIZED
-- A session that is ENDED / CANCELED / EXPIRED gives nothing back.
--
-- is_moderator tells the app to open the plain sign-in page for a teacher/staff
-- member instead of an invite link, which would admit them as an ordinary guest.
-- ============================================================================
CREATE OR REPLACE FUNCTION classroom_browser_invite_key(p_session_id UUID)
RETURNS TABLE (room_password TEXT, is_moderator BOOLEAN)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
#variable_conflict use_column
DECLARE
  v_session RECORD;
  v_manager BOOLEAN;
  v_participant RECORD;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  SELECT * INTO v_session FROM classroom_sessions WHERE id = p_session_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SESSION_NOT_FOUND';
  END IF;

  IF v_session.status IN ('CANCELED', 'ENDED', 'EXPIRED') THEN
    RAISE EXCEPTION 'SESSION_NOT_AVAILABLE';
  END IF;

  v_manager := classroom_can_manage(p_session_id);

  IF NOT v_manager THEN
    SELECT * INTO v_participant
    FROM classroom_participants
    WHERE session_id = p_session_id AND user_id = auth.uid();

    IF NOT FOUND OR v_participant.status = 'KICKED' THEN
      RAISE EXCEPTION 'NOT_AUTHORIZED';
    END IF;
  END IF;

  RETURN QUERY
  SELECT v_session.room_password,
         (v_session.teacher_id = auth.uid() OR v_manager);
END;
$$;

-- Supabase grants EXECUTE on new functions to anon directly; REVOKE ... FROM PUBLIC does not
-- strip that, so anon is revoked explicitly (same rule every classroom_* function follows).
REVOKE ALL ON FUNCTION classroom_browser_invite_key(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_browser_invite_key(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_browser_invite_key(UUID) TO authenticated;
