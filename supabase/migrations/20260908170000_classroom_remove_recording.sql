-- ============================================================================
-- 7PRO Virtual Classroom — remove the recording feature entirely
--
-- Product decision: the recording feature is removed from the app, not
-- deferred. Real capture-to-file always required a Jibri-capable self-hosted
-- Jitsi deployment (or an external recorder) that this academy does not run
-- and does not plan to add, so the feature could only ever be a
-- start/stop control layer with no video file ever produced — kept, this
-- would train users to expect a recording that never exists. Verified zero
-- rows exist in classroom_recordings before writing this migration, so
-- nothing of value is lost by dropping it.
--
-- Removes, in dependency order:
--   1. classroom_create_session — re-created without p_recording_enabled
--      (DROP+CREATE, not CREATE OR REPLACE, because removing a parameter
--      changes the function's identity signature in Postgres).
--   2. classroom_end_session — re-created without touching
--      classroom_recordings/active_recording_id.
--   3. classroom_start_recording / classroom_set_recording_url — dropped.
--      (classroom_stop_recording never shipped — it was only ever added and
--      then removed again within this same change, so there is nothing to
--      drop for it.)
--   4. classroom_recordings table — dropped (0 rows, confirmed live).
--   5. classroom_sessions recording_* columns — dropped.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. classroom_create_session without the recording toggle.
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS classroom_create_session(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, UUID, INTEGER, BOOLEAN, UUID[]);

CREATE FUNCTION classroom_create_session(
  p_title TEXT,
  p_description TEXT,
  p_scheduled_start TIMESTAMPTZ,
  p_scheduled_end TIMESTAMPTZ,
  p_teacher_id UUID DEFAULT NULL,
  p_max_participants INTEGER DEFAULT 30,
  p_student_ids UUID[] DEFAULT '{}'
)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_role TEXT := classroom_my_role();
  v_is_staff BOOLEAN := classroom_is_staff_with('classroom.manage');
  v_teacher UUID;
  v_session_id UUID;
  v_student UUID;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF p_scheduled_end <= p_scheduled_start THEN
    RAISE EXCEPTION 'INVALID_WINDOW';
  END IF;

  IF v_is_staff THEN
    v_teacher := COALESCE(p_teacher_id, auth.uid());
  ELSIF v_role = 'TEACHER' THEN
    v_teacher := auth.uid();
  ELSE
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  INSERT INTO classroom_sessions (
    teacher_id, title, description, scheduled_start, scheduled_end,
    max_participants, created_by
  ) VALUES (
    v_teacher, p_title, NULLIF(p_description, ''), p_scheduled_start, p_scheduled_end,
    GREATEST(1, LEAST(200, p_max_participants)), auth.uid()
  )
  RETURNING id INTO v_session_id;

  INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, invited_by)
  VALUES (v_session_id, v_teacher, 'TEACHER', 'INVITED', auth.uid())
  ON CONFLICT (session_id, user_id) DO NOTHING;

  FOREACH v_student IN ARRAY COALESCE(p_student_ids, '{}') LOOP
    IF v_student IS NULL OR v_student = v_teacher THEN
      CONTINUE;
    END IF;

    INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, invited_by)
    VALUES (v_session_id, v_student, 'STUDENT', 'INVITED', auth.uid())
    ON CONFLICT (session_id, user_id) DO NOTHING;

    INSERT INTO notifications (user_id, kind, title, body, data)
    VALUES (
      v_student, 'CLASSROOM_INVITE', 'دعوة لحصة افتراضية', p_title,
      jsonb_build_object('classroom_session_id', v_session_id)
    );
  END LOOP;

  RETURN v_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_create_session(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, UUID, INTEGER, UUID[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_create_session(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, UUID, INTEGER, UUID[]) TO authenticated;

-- ----------------------------------------------------------------------------
-- 2. classroom_end_session without any classroom_recordings reference.
--    Same identity signature as before (p_session_id UUID) -> CREATE OR
--    REPLACE is fine here, no DROP needed.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_end_session(p_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_sessions SET status = 'ENDED', ended_at = now() WHERE id = p_session_id;

  UPDATE classroom_participants
  SET status = 'LEFT', left_at = now()
  WHERE session_id = p_session_id AND status = 'JOINED';
END;
$$;

REVOKE ALL ON FUNCTION classroom_end_session(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_end_session(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- 3 & 4 & 5. Drop the recording functions, table, and columns.
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS classroom_start_recording(UUID);
DROP FUNCTION IF EXISTS classroom_set_recording_url(UUID, TEXT);
DROP FUNCTION IF EXISTS classroom_stop_recording(UUID);

DROP TABLE IF EXISTS classroom_recordings CASCADE;

ALTER TABLE classroom_sessions DROP COLUMN IF EXISTS active_recording_id;
ALTER TABLE classroom_sessions DROP COLUMN IF EXISTS recording_enabled;
ALTER TABLE classroom_sessions DROP COLUMN IF EXISTS recording_url;
ALTER TABLE classroom_sessions DROP COLUMN IF EXISTS recording_started_at;
ALTER TABLE classroom_sessions DROP COLUMN IF EXISTS recording_ended_at;
