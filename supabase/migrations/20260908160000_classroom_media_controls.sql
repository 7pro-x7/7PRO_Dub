-- ============================================================================
-- 7PRO Virtual Classroom — teacher control of student mic/camera
--
-- Per-student mic/camera lock state, plus a session-wide "lock all mics"
-- switch, enforced server-side (only the teacher/staff with classroom.manage
-- may set them) and pushed to every client via the classroom_participants /
-- classroom_sessions Realtime publication that already exists
-- (20260908120300) — no new subscription needed.
--
-- IMPORTANT — what this migration does and does NOT give you:
-- This is the authorization + state layer: who is allowed to lock/unlock
-- whom, and a durable, Realtime-synced record of the current lock state that
-- survives reconnects and late joins. It is not, by itself, a guarantee that
-- a locked student's microphone is physically silent at the media level —
-- enforcement of the lock happens in the Android client (forcing its own
-- mic off and disabling the unmute button while locked), the same trust
-- model every classroom app built on public/self-hosted Jitsi without its
-- own SFU-level moderation extension uses. See the accompanying client
-- changes for exactly where that enforcement happens.
--
-- (Note: an earlier draft of this migration also added a recording
-- start/stop control layer. The recording feature was removed from the
-- product entirely — see 20260908170000_classroom_remove_recording.sql —
-- before this migration was ever applied, so it was never included here.)
-- ============================================================================

ALTER TABLE classroom_participants ADD COLUMN IF NOT EXISTS mic_locked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE classroom_participants ADD COLUMN IF NOT EXISTS camera_locked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE classroom_sessions ADD COLUMN IF NOT EXISTS all_mics_locked BOOLEAN NOT NULL DEFAULT false;

-- Lock/unlock a single student's mic and/or camera. Pass NULL for whichever
-- one you are not changing. Only the teacher/staff who can manage the
-- session may call this, and only against a STUDENT row — a teacher can
-- never accidentally lock a co-teacher/moderator out of the call this way.
CREATE OR REPLACE FUNCTION classroom_set_participant_media(
  p_session_id UUID,
  p_user_id UUID,
  p_mic_locked BOOLEAN DEFAULT NULL,
  p_camera_locked BOOLEAN DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_role TEXT;
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT role_in_session INTO v_role
  FROM classroom_participants
  WHERE session_id = p_session_id AND user_id = p_user_id;

  IF v_role IS NULL THEN
    RAISE EXCEPTION 'PARTICIPANT_NOT_FOUND';
  END IF;
  IF v_role <> 'STUDENT' THEN
    RAISE EXCEPTION 'CANNOT_LOCK_STAFF';
  END IF;

  UPDATE classroom_participants
  SET
    mic_locked = COALESCE(p_mic_locked, mic_locked),
    camera_locked = COALESCE(p_camera_locked, camera_locked)
  WHERE session_id = p_session_id AND user_id = p_user_id;
END;
$$;

-- Session-wide switch: lock or unlock every current STUDENT's mic at once.
-- Applies going forward too — see the trigger below — so a student who
-- joins after "lock all" is switched on still starts muted, matching what
-- a teacher expects "lock all mics" to mean.
CREATE OR REPLACE FUNCTION classroom_set_all_mics_locked(p_session_id UUID, p_locked BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_sessions SET all_mics_locked = p_locked WHERE id = p_session_id;

  UPDATE classroom_participants
  SET mic_locked = p_locked
  WHERE session_id = p_session_id AND role_in_session = 'STUDENT';
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_participant_media(UUID, UUID, BOOLEAN, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_set_all_mics_locked(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_set_participant_media(UUID, UUID, BOOLEAN, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_set_all_mics_locked(UUID, BOOLEAN) TO authenticated;

-- A student's first-ever join (a genuine INSERT into classroom_participants —
-- see classroom_join()'s ON CONFLICT DO UPDATE, which is the path an actual
-- reconnect takes and which correctly leaves an existing lock state alone)
-- should start locked if "lock all mics" is already on, instead of always
-- defaulting to unlocked regardless of the teacher's standing setting.
CREATE OR REPLACE FUNCTION classroom_apply_session_mic_lock()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.role_in_session = 'STUDENT' THEN
    SELECT all_mics_locked INTO NEW.mic_locked FROM classroom_sessions WHERE id = NEW.session_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_classroom_participants_mic_lock ON classroom_participants;
CREATE TRIGGER trg_classroom_participants_mic_lock
  BEFORE INSERT ON classroom_participants
  FOR EACH ROW EXECUTE FUNCTION classroom_apply_session_mic_lock();
