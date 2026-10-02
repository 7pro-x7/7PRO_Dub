INSERT INTO public.app_settings (key, value, description) VALUES
  ('buddy.voice_listening', 'true'::jsonb, 'Buddy characters listen to the child''s voice (needs an open-source speech recogniser). Off = answers by tapping only, no model at all.')
ON CONFLICT (key) DO NOTHING;

CREATE OR REPLACE FUNCTION public.buddy_set_system(p_enabled boolean, p_voice_listening boolean)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.buddy_can_manage() THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  INSERT INTO app_settings (key, value, updated_by, updated_at) VALUES
    ('buddy.enabled', to_jsonb(coalesce(p_enabled, false)), auth.uid(), now()),
    ('buddy.voice_listening', to_jsonb(coalesce(p_voice_listening, true)), auth.uid(), now())
  ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;
  PERFORM public.write_audit('settings.update', 'setting', 'buddy',
    jsonb_build_object('enabled', coalesce(p_enabled, false), 'voice_listening', coalesce(p_voice_listening, true)));
  RETURN jsonb_build_object('ok', true);
END $$;
REVOKE ALL ON FUNCTION public.buddy_set_system(boolean, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.buddy_set_system(boolean, boolean) TO authenticated;
DROP FUNCTION IF EXISTS public.buddy_set_enabled(boolean);;
