CREATE OR REPLACE FUNCTION public.classroom_guest_join(
  p_session_id UUID,
  p_access_key TEXT
)
RETURNS TABLE (
  room_name TEXT, room_password TEXT, jitsi_domain TEXT, role_in_session TEXT,
  is_moderator BOOLEAN, display_name TEXT, title TEXT, status TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
#variable_conflict use_column
DECLARE
  v_session RECORD;
  v_existing RECORD;
  v_has_existing BOOLEAN := false;
  v_live_count INTEGER;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  SELECT * INTO v_session FROM public.classroom_sessions cs WHERE cs.id = p_session_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SESSION_NOT_FOUND';
  END IF;

  IF v_session.status IN ('CANCELED', 'ENDED', 'EXPIRED') THEN
    RAISE EXCEPTION 'SESSION_NOT_AVAILABLE';
  END IF;

  IF p_access_key IS NULL OR p_access_key = '' OR p_access_key <> v_session.room_password THEN
    RAISE EXCEPTION 'INVALID_INVITE';
  END IF;

  IF now() < v_session.scheduled_start - INTERVAL '10 minutes' THEN
    RAISE EXCEPTION 'TOO_EARLY';
  END IF;

  IF now() > v_session.scheduled_end + INTERVAL '30 minutes' AND v_session.status <> 'LIVE' THEN
    RAISE EXCEPTION 'SESSION_WINDOW_PASSED';
  END IF;

  SELECT * INTO v_existing FROM public.classroom_participants cp
  WHERE cp.session_id = p_session_id AND cp.user_id = auth.uid();
  v_has_existing := FOUND;

  IF v_has_existing AND v_existing.status = 'KICKED' THEN
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  SELECT count(*) INTO v_live_count FROM public.classroom_participants cp
  WHERE cp.session_id = p_session_id AND cp.status = 'JOINED';

  IF NOT v_has_existing AND v_live_count >= v_session.max_participants THEN
    RAISE EXCEPTION 'SESSION_FULL';
  END IF;

  INSERT INTO public.classroom_participants AS cp
    (session_id, user_id, role_in_session, status, invited_by, joined_at)
  VALUES
    (p_session_id, auth.uid(), 'STUDENT', 'JOINED', v_session.teacher_id, now())
  ON CONFLICT (session_id, user_id) DO UPDATE
    SET status = 'JOINED', joined_at = now(), left_at = NULL;

  INSERT INTO public.classroom_attendance_events (session_id, user_id, event)
  VALUES (p_session_id, auth.uid(), 'JOIN');

  IF v_session.status = 'SCHEDULED' THEN
    UPDATE public.classroom_sessions cs SET status = 'LIVE', started_at = now() WHERE cs.id = p_session_id;
  END IF;

  RETURN QUERY
  SELECT v_session.room_name::TEXT, v_session.room_password::TEXT, v_session.jitsi_domain::TEXT,
         'STUDENT'::TEXT, false, 'Learner'::TEXT, v_session.title::TEXT,
         (CASE WHEN v_session.status = 'SCHEDULED' THEN 'LIVE' ELSE v_session.status END)::TEXT;
END;
$$;

REVOKE ALL ON FUNCTION public.classroom_guest_join(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.classroom_guest_join(UUID, TEXT) TO anon, authenticated;;
