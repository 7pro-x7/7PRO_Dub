-- ============================================================================
-- 7PRO Virtual Classroom — disable the session-wide "lock all mics" feature
--
-- Per-student mic/camera lock (classroom_set_participant_media) is UNCHANGED:
-- a teacher can still lock/unlock one student's mic, and that student can
-- still request/toggle it the normal way.
--
-- What this migration removes is only the bulk "lock every student's mic at
-- once" switch introduced in 20260908160000_classroom_media_controls.sql:
--   - classroom_set_all_mics_locked(...) can no longer lock anyone. It still
--     exists (so old clients calling it don't hard-crash) but now only ever
--     clears the lock — passing p_locked = true is silently treated as
--     "unlock everyone" too, matching "mics stay open" going forward.
--   - The trigger that made every newly-joining student inherit the
--     session's all_mics_locked flag is dropped, so a fresh join always
--     starts unlocked regardless of that flag.
--   - Every session's all_mics_locked flag is reset to false, and every
--     participant's mic_locked that came from the bulk switch is cleared so
--     current sessions actually reflect "open" right away.
-- ============================================================================

-- Stop new joiners from inheriting the session-wide lock flag.
DROP TRIGGER IF EXISTS trg_classroom_participants_mic_lock ON classroom_participants;
DROP FUNCTION IF EXISTS classroom_apply_session_mic_lock();

-- Neuter the bulk switch: it can only ever unlock now, never lock.
CREATE OR REPLACE FUNCTION classroom_set_all_mics_locked(p_session_id UUID, p_locked BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  -- p_locked is intentionally ignored: this switch no longer locks anyone.
  UPDATE classroom_sessions SET all_mics_locked = false WHERE id = p_session_id;

  UPDATE classroom_participants
  SET mic_locked = false
  WHERE session_id = p_session_id AND role_in_session = 'STUDENT';
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_all_mics_locked(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_set_all_mics_locked(UUID, BOOLEAN) TO authenticated;

-- Clear out any lock currently in effect from the bulk switch, right now,
-- for every existing session/participant.
UPDATE classroom_sessions SET all_mics_locked = false WHERE all_mics_locked = true;

UPDATE classroom_participants
SET mic_locked = false
WHERE role_in_session = 'STUDENT' AND mic_locked = true;
;
