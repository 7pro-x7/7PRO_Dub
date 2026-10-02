CREATE OR REPLACE FUNCTION public.classroom_translation_authorize(p_session_id UUID)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_enabled BOOLEAN;
  v_status TEXT;
  v_mic_locked BOOLEAN;
  v_name TEXT;
BEGIN
  IF auth.uid() IS NULL THEN
    RETURN jsonb_build_object('allowed', false, 'reason', 'UNAUTHORIZED');
  END IF;

  SELECT coalesce((value #>> '{}')::boolean, false) INTO v_enabled
  FROM public.app_settings WHERE key = 'classroom.live_translation_enabled';
  IF NOT coalesce(v_enabled, false) THEN
    RETURN jsonb_build_object('allowed', false, 'reason', 'FEATURE_DISABLED');
  END IF;

  SELECT status INTO v_status FROM public.classroom_sessions WHERE id = p_session_id;
  IF v_status IS NULL OR v_status NOT IN ('SCHEDULED', 'LIVE') THEN
    RETURN jsonb_build_object('allowed', false, 'reason', 'SESSION_NOT_AVAILABLE');
  END IF;

  IF NOT public.classroom_can_view(p_session_id) THEN
    RETURN jsonb_build_object('allowed', false, 'reason', 'NOT_AUTHORIZED');
  END IF;

  SELECT mic_locked INTO v_mic_locked
  FROM public.classroom_participants
  WHERE session_id = p_session_id AND user_id = auth.uid();

  SELECT nullif(btrim(full_name), '') INTO v_name FROM public.profiles WHERE id = auth.uid();

  RETURN jsonb_build_object(
    'allowed', true,
    'user_id', auth.uid(),
    'display_name', coalesce(v_name, 'Learner'),
    'is_moderator', public.classroom_is_teacher_of(p_session_id) OR public.classroom_is_staff_with('classroom.manage'),
    'mic_locked', coalesce(v_mic_locked, false)
  );
END;
$$;
REVOKE ALL ON FUNCTION public.classroom_translation_authorize(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.classroom_translation_authorize(UUID) TO authenticated;;
