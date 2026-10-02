-- Live Voice Translation inside the classroom meeting.
--
-- Two owner-controlled settings (off by default, so nothing changes until the owner turns it on):
--   classroom.live_translation_enabled     boolean  master switch
--   classroom.live_translation_server_url  text     wss:// address of the self-hosted open-source
--                                                   translation server (translation-server/)
-- Every signed-in user (anonymous browser guests included) can already READ app_settings, which
-- is how the app and the web page decide whether to show the feature. WRITING these two keys is
-- restricted to the OWNER through classroom_set_live_translation below.
--
-- classroom_translation_authorize() is what the translation server calls, with the caller's own
-- Supabase JWT, before it lets a socket join a room: the same "can this user see this session"
-- rule the rest of the classroom uses, plus the teacher's mic lock, so a locked student cannot
-- talk through the translation channel either.

INSERT INTO public.app_settings (key, value, description) VALUES
  ('classroom.live_translation_enabled', 'false'::jsonb,
   'Live Voice Translation inside the classroom meeting (owner switch).'),
  ('classroom.live_translation_server_url', '""'::jsonb,
   'wss:// address of the self-hosted open-source live translation server.')
ON CONFLICT (key) DO NOTHING;

CREATE OR REPLACE FUNCTION public.classroom_set_live_translation(
  p_enabled BOOLEAN,
  p_server_url TEXT
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_url TEXT := btrim(coalesce(p_server_url, ''));
BEGIN
  IF NOT public.classroom_is_owner() THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF v_url <> '' AND v_url !~* '^wss?://' THEN
    RAISE EXCEPTION 'INVALID_URL';
  END IF;
  IF p_enabled AND v_url = '' THEN
    RAISE EXCEPTION 'SERVER_URL_REQUIRED';
  END IF;

  INSERT INTO public.app_settings (key, value, updated_by, updated_at)
  VALUES ('classroom.live_translation_enabled', to_jsonb(coalesce(p_enabled, false)), auth.uid(), now()),
         ('classroom.live_translation_server_url', to_jsonb(v_url), auth.uid(), now())
  ON CONFLICT (key) DO UPDATE
    SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;

  PERFORM public.write_audit('settings.update', 'setting', 'classroom.live_translation',
    jsonb_build_object('enabled', coalesce(p_enabled, false), 'server_url', v_url));
  RETURN jsonb_build_object('ok', true);
END;
$$;

REVOKE ALL ON FUNCTION public.classroom_set_live_translation(BOOLEAN, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.classroom_set_live_translation(BOOLEAN, TEXT) TO authenticated;

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
GRANT EXECUTE ON FUNCTION public.classroom_translation_authorize(UUID) TO authenticated;
