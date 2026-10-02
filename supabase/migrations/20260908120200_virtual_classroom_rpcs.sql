-- ============================================================================
-- 7PRO Virtual Classroom — RPC functions
--
-- Every write that needs a permission check beyond plain RLS (creating a
-- session with invited students in one transaction, generating a room name,
-- validating a join, logging attendance, etc.) goes through one of these
-- SECURITY DEFINER functions instead of a raw insert from the client. This is
-- the "كل التحقق Server-Side" requirement: the Kotlin app never decides who is
-- allowed to do what, it only calls these and surfaces the error they raise.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- classroom_create_session
--   Creates a session and (optionally) invites a list of student ids in one
--   transaction. Only a TEACHER (for themselves) or staff with
--   'classroom.manage' may call this.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_create_session(
  p_title TEXT,
  p_description TEXT,
  p_scheduled_start TIMESTAMPTZ,
  p_scheduled_end TIMESTAMPTZ,
  p_teacher_id UUID DEFAULT NULL,
  p_max_participants INTEGER DEFAULT 30,
  p_recording_enabled BOOLEAN DEFAULT false,
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
  IF p_scheduled_end <= p_scheduled_start THEN
    RAISE EXCEPTION 'INVALID_WINDOW';
  END IF;

  IF v_is_staff THEN
    -- Staff may create on behalf of any teacher; defaults to themselves.
    v_teacher := COALESCE(p_teacher_id, auth.uid());
  ELSIF v_role = 'TEACHER' THEN
    v_teacher := auth.uid();
  ELSE
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  INSERT INTO classroom_sessions (
    teacher_id, title, description, scheduled_start, scheduled_end,
    max_participants, recording_enabled, created_by
  ) VALUES (
    v_teacher, p_title, NULLIF(p_description, ''), p_scheduled_start, p_scheduled_end,
    GREATEST(1, LEAST(200, p_max_participants)), p_recording_enabled, auth.uid()
  )
  RETURNING id INTO v_session_id;

  -- The teacher is always a participant with the TEACHER role.
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
      v_student,
      'CLASSROOM_INVITE',
      'دعوة لحصة افتراضية',
      p_title,
      jsonb_build_object('classroom_session_id', v_session_id)
    );
  END LOOP;

  RETURN v_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_create_session(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, UUID, INTEGER, BOOLEAN, UUID[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_create_session(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, UUID, INTEGER, BOOLEAN, UUID[]) TO authenticated;

-- ----------------------------------------------------------------------------
-- classroom_invite_students — add more students to an existing session.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_invite_students(p_session_id UUID, p_student_ids UUID[])
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_student UUID;
  v_title TEXT;
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT title INTO v_title FROM classroom_sessions WHERE id = p_session_id;

  FOREACH v_student IN ARRAY COALESCE(p_student_ids, '{}') LOOP
    IF v_student IS NULL THEN
      CONTINUE;
    END IF;

    INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, invited_by)
    VALUES (p_session_id, v_student, 'STUDENT', 'INVITED', auth.uid())
    ON CONFLICT (session_id, user_id) DO NOTHING;

    INSERT INTO notifications (user_id, kind, title, body, data)
    VALUES (
      v_student, 'CLASSROOM_INVITE', 'دعوة لحصة افتراضية', v_title,
      jsonb_build_object('classroom_session_id', p_session_id)
    );
  END LOOP;
END;
$$;

REVOKE ALL ON FUNCTION classroom_invite_students(UUID, UUID[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_invite_students(UUID, UUID[]) TO authenticated;

-- ----------------------------------------------------------------------------
-- classroom_remove_participant
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_remove_participant(p_session_id UUID, p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_participants
  SET status = 'KICKED'
  WHERE session_id = p_session_id AND user_id = p_user_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_remove_participant(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_remove_participant(UUID, UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- classroom_search_students — lets a TEACHER/staff search STUDENT accounts to
-- invite, without needing a broad SELECT policy on `profiles`.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_search_students(p_query TEXT)
RETURNS TABLE (id UUID, full_name TEXT, email TEXT, avatar_url TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF classroom_my_role() NOT IN ('TEACHER', 'OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT p.id, p.full_name, p.email, p.avatar_url
  FROM profiles p
  WHERE p.role = 'STUDENT'
    AND (
      p_query IS NULL OR p_query = '' OR
      p.full_name ILIKE '%' || p_query || '%' OR
      p.email ILIKE '%' || p_query || '%'
    )
  ORDER BY p.full_name NULLS LAST
  LIMIT 30;
END;
$$;

REVOKE ALL ON FUNCTION classroom_search_students(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_search_students(TEXT) TO authenticated;

-- ----------------------------------------------------------------------------
-- classroom_join
--   The ONLY place room_name/room_password are ever handed to a client.
--   Re-validates authorization, the scheduling window, session status and
--   the max-participants cap, then flips the session to LIVE the first time
--   the teacher joins, logs an attendance JOIN event and returns everything
--   the app needs to open the Jitsi conference.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_join(p_session_id UUID)
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
  v_is_manager BOOLEAN := classroom_can_manage(p_session_id);
  v_live_count INTEGER;
  v_name TEXT;
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
  -- joining a session they did not create (observer access).
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

  RETURN QUERY
  SELECT
    v_session.room_name,
    v_session.room_password,
    v_session.jitsi_domain,
    CASE WHEN v_session.teacher_id = auth.uid() THEN 'TEACHER'
         WHEN v_is_manager THEN 'MODERATOR'
         ELSE 'STUDENT' END,
    (v_session.teacher_id = auth.uid() OR v_is_manager),
    v_name;
END;
$$;

REVOKE ALL ON FUNCTION classroom_join(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_join(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- classroom_leave — logs a LEAVE event. Safe to call multiple times (e.g. a
-- flaky connection dropping and the app calling it again on cleanup).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_leave(p_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE classroom_participants
  SET status = CASE WHEN status = 'JOINED' THEN 'LEFT' ELSE status END,
      left_at = now()
  WHERE session_id = p_session_id AND user_id = auth.uid();

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  SELECT p_session_id, auth.uid(), 'LEAVE'
  WHERE classroom_is_participant(p_session_id)
     OR classroom_can_manage(p_session_id);
END;
$$;

REVOKE ALL ON FUNCTION classroom_leave(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_leave(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- classroom_start / classroom_end
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

  UPDATE classroom_recordings
  SET ended_at = now()
  WHERE session_id = p_session_id AND ended_at IS NULL;
END;
$$;

REVOKE ALL ON FUNCTION classroom_end_session(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_end_session(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION classroom_cancel_session(p_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_sessions SET status = 'CANCELED' WHERE id = p_session_id AND status = 'SCHEDULED';

  INSERT INTO notifications (user_id, kind, title, body, data)
  SELECT cp.user_id, 'CLASSROOM_CANCELED', 'تم إلغاء الحصة', cs.title,
         jsonb_build_object('classroom_session_id', cs.id)
  FROM classroom_participants cp
  JOIN classroom_sessions cs ON cs.id = cp.session_id
  WHERE cp.session_id = p_session_id AND cp.user_id != auth.uid();
END;
$$;

REVOKE ALL ON FUNCTION classroom_cancel_session(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_cancel_session(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- Hand raise / lower — kept as RPCs (rather than a raw update) so every
-- change is also written to the audit trail in one transaction.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_raise_hand(p_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_is_participant(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_participants
  SET hand_raised = true, hand_raised_at = now()
  WHERE session_id = p_session_id AND user_id = auth.uid();

  INSERT INTO classroom_hand_raise_events (session_id, user_id, action, actor_id)
  VALUES (p_session_id, auth.uid(), 'RAISE', auth.uid());
END;
$$;

CREATE OR REPLACE FUNCTION classroom_lower_hand(p_session_id UUID, p_user_id UUID DEFAULT NULL)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_target UUID := COALESCE(p_user_id, auth.uid());
BEGIN
  IF v_target != auth.uid() AND NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_participants
  SET hand_raised = false
  WHERE session_id = p_session_id AND user_id = v_target;

  INSERT INTO classroom_hand_raise_events (session_id, user_id, action, actor_id)
  VALUES (p_session_id, v_target, 'LOWER', auth.uid());
END;
$$;

REVOKE ALL ON FUNCTION classroom_raise_hand(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_lower_hand(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_raise_hand(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_lower_hand(UUID, UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- Recording metadata (see notes in the migration file about Jibri).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_start_recording(p_session_id UUID)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF NOT (SELECT recording_enabled FROM classroom_sessions WHERE id = p_session_id) THEN
    RAISE EXCEPTION 'RECORDING_NOT_ENABLED';
  END IF;

  INSERT INTO classroom_recordings (session_id, started_by)
  VALUES (p_session_id, auth.uid())
  RETURNING id INTO v_id;

  UPDATE classroom_sessions SET recording_started_at = now() WHERE id = p_session_id;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION classroom_set_recording_url(p_recording_id UUID, p_url TEXT)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_session UUID;
BEGIN
  SELECT session_id INTO v_session FROM classroom_recordings WHERE id = p_recording_id;
  IF v_session IS NULL OR NOT classroom_can_manage(v_session) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_recordings SET url = p_url, ended_at = COALESCE(ended_at, now()) WHERE id = p_recording_id;
  UPDATE classroom_sessions SET recording_url = p_url, recording_ended_at = now() WHERE id = v_session;
END;
$$;

REVOKE ALL ON FUNCTION classroom_start_recording(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_set_recording_url(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_start_recording(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_set_recording_url(UUID, TEXT) TO authenticated;

-- ----------------------------------------------------------------------------
-- Whiteboard board upsert (background image/PDF) — strokes are plain inserts
-- under RLS above, but the board row itself is created lazily on first use.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_ensure_whiteboard(p_session_id UUID)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT classroom_can_view(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT id INTO v_id FROM classroom_whiteboard_boards WHERE session_id = p_session_id;
  IF v_id IS NOT NULL THEN
    RETURN v_id;
  END IF;

  INSERT INTO classroom_whiteboard_boards (session_id, created_by)
  VALUES (p_session_id, auth.uid())
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_ensure_whiteboard(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_ensure_whiteboard(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION classroom_set_whiteboard_background(
  p_session_id UUID, p_url TEXT, p_type TEXT, p_page INTEGER DEFAULT 0
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF p_type NOT IN ('NONE', 'IMAGE', 'PDF') THEN
    RAISE EXCEPTION 'INVALID_TYPE';
  END IF;

  PERFORM classroom_ensure_whiteboard(p_session_id);

  UPDATE classroom_whiteboard_boards
  SET background_url = p_url, background_type = p_type, background_page = p_page
  WHERE session_id = p_session_id;

  INSERT INTO classroom_whiteboard_strokes (board_id, session_id, user_id, action, stroke)
  SELECT id, p_session_id, auth.uid(), 'CLEAR', NULL
  FROM classroom_whiteboard_boards WHERE session_id = p_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) TO authenticated;

-- ----------------------------------------------------------------------------
-- Reminder sweep — call periodically (pg_cron, or a Supabase scheduled edge
-- function hitting an RPC wrapper) to notify participants of sessions
-- starting soon. Not wired to a scheduler by this migration: enable pg_cron
-- on the project and add
--   SELECT cron.schedule('classroom-reminders', '*/5 * * * *',
--     $$SELECT classroom_send_start_reminders()$$);
-- if you want it to run automatically.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_send_start_reminders()
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  INSERT INTO notifications (user_id, kind, title, body, data)
  SELECT cp.user_id, 'CLASSROOM_STARTING_SOON', 'الحصة تبدأ قريبًا', cs.title,
         jsonb_build_object('classroom_session_id', cs.id)
  FROM classroom_sessions cs
  JOIN classroom_participants cp ON cp.session_id = cs.id AND cp.status != 'KICKED'
  WHERE cs.status = 'SCHEDULED'
    AND cs.scheduled_start BETWEEN now() + INTERVAL '9 minutes' AND now() + INTERVAL '11 minutes'
    AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = cp.user_id
        AND n.kind = 'CLASSROOM_STARTING_SOON'
        AND n.data->>'classroom_session_id' = cs.id::text
    );
END;
$$;

REVOKE ALL ON FUNCTION classroom_send_start_reminders() FROM PUBLIC;
