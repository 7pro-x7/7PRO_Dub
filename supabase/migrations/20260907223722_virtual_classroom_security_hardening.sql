CREATE OR REPLACE FUNCTION classroom_search_students(p_query TEXT)
RETURNS TABLE (id UUID, full_name TEXT, email TEXT, avatar_url TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF COALESCE(classroom_my_role(), '') NOT IN ('TEACHER', 'OWNER', 'ADMIN') THEN
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

CREATE OR REPLACE FUNCTION classroom_lower_hand(p_session_id UUID, p_user_id UUID DEFAULT NULL)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_target UUID := COALESCE(p_user_id, auth.uid());
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF v_target IS DISTINCT FROM auth.uid() AND NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_participants
  SET hand_raised = false
  WHERE session_id = p_session_id AND user_id = v_target;

  INSERT INTO classroom_hand_raise_events (session_id, user_id, action, actor_id)
  VALUES (p_session_id, v_target, 'LOWER', auth.uid());
END;
$$;

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
    max_participants, recording_enabled, created_by
  ) VALUES (
    v_teacher, p_title, NULLIF(p_description, ''), p_scheduled_start, p_scheduled_end,
    GREATEST(1, LEAST(200, p_max_participants)), p_recording_enabled, auth.uid()
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
  v_is_manager BOOLEAN;
  v_live_count INTEGER;
  v_name TEXT;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  v_is_manager := classroom_can_manage(p_session_id);

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

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS sig
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname LIKE 'classroom\_%'
  LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM anon', r.sig);
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', r.sig);
  END LOOP;
END $$;

GRANT EXECUTE ON FUNCTION classroom_create_session(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, UUID, INTEGER, BOOLEAN, UUID[]) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_invite_students(UUID, UUID[]) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_remove_participant(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_search_students(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_join(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_leave(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_end_session(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_cancel_session(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_raise_hand(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_lower_hand(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_start_recording(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_set_recording_url(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_ensure_whiteboard(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION classroom_participants_guard_self_update()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF classroom_can_manage(NEW.session_id) THEN
    RETURN NEW;
  END IF;

  IF NEW.user_id != auth.uid()
     OR NEW.session_id != OLD.session_id
     OR NEW.role_in_session != OLD.role_in_session
     OR NEW.status != OLD.status
     OR NEW.invited_by IS DISTINCT FROM OLD.invited_by
     OR NEW.joined_at IS DISTINCT FROM OLD.joined_at
     OR NEW.left_at IS DISTINCT FROM OLD.left_at
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  NEW.hand_raised_at := CASE WHEN NEW.hand_raised THEN now() ELSE NEW.hand_raised_at END;
  RETURN NEW;
END;
$$;
;
