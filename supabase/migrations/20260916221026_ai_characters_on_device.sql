CREATE OR REPLACE FUNCTION public.ai_set_characters_system(p_enabled boolean, p_server_url text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_url text := btrim(coalesce(p_server_url, ''));
BEGIN
  IF NOT public.ai_is_owner() THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF v_url <> '' AND v_url !~* '^wss?://' THEN RAISE EXCEPTION 'INVALID_URL'; END IF;
  INSERT INTO app_settings (key, value, updated_by, updated_at) VALUES
    ('ai_characters.enabled', to_jsonb(coalesce(p_enabled, false)), auth.uid(), now()),
    ('ai_characters.server_url', to_jsonb(v_url), auth.uid(), now())
  ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;
  PERFORM public.write_audit('settings.update', 'setting', 'ai_characters',
    jsonb_build_object('enabled', coalesce(p_enabled, false), 'server_url', v_url));
  RETURN jsonb_build_object('ok', true);
END $$;

UPDATE public.app_settings
SET description = 'Optional wss:// address of a self-hosted AI tutor server. Empty = run on the learner''s phone.'
WHERE key = 'ai_characters.server_url';;
