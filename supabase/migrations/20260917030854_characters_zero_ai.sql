CREATE OR REPLACE FUNCTION public.buddy_touch_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := now(); RETURN NEW; END $$;

DROP TRIGGER IF EXISTS buddy_characters_touch ON public.buddy_characters;
DROP TRIGGER IF EXISTS buddy_scenarios_touch ON public.buddy_scenarios;
DROP TRIGGER IF EXISTS exercise_buddy_touch ON public.exercise_buddy_configs;
CREATE TRIGGER buddy_characters_touch BEFORE UPDATE ON public.buddy_characters
  FOR EACH ROW EXECUTE FUNCTION public.buddy_touch_updated_at();
CREATE TRIGGER buddy_scenarios_touch BEFORE UPDATE ON public.buddy_scenarios
  FOR EACH ROW EXECUTE FUNCTION public.buddy_touch_updated_at();
CREATE TRIGGER exercise_buddy_touch BEFORE UPDATE ON public.exercise_buddy_configs
  FOR EACH ROW EXECUTE FUNCTION public.buddy_touch_updated_at();

CREATE OR REPLACE FUNCTION public.buddy_can_manage()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'OWNER')
      OR public.has_permission('tests.manage');
$$;

DROP FUNCTION IF EXISTS public.buddy_set_system(boolean, boolean);
DELETE FROM public.app_settings WHERE key = 'buddy.voice_listening';

CREATE OR REPLACE FUNCTION public.buddy_set_enabled(p_enabled boolean)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.buddy_can_manage() THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  INSERT INTO app_settings (key, value, updated_by, updated_at)
  VALUES ('buddy.enabled', to_jsonb(coalesce(p_enabled, false)), auth.uid(), now())
  ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;
  PERFORM public.write_audit('settings.update', 'setting', 'buddy.enabled',
    jsonb_build_object('enabled', coalesce(p_enabled, false)));
  RETURN jsonb_build_object('ok', true);
END $$;
REVOKE ALL ON FUNCTION public.buddy_set_enabled(boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.buddy_set_enabled(boolean) TO authenticated;

UPDATE public.buddy_scenarios
SET nodes = (SELECT coalesce(jsonb_agg(n - 'show_choices'), '[]'::jsonb) FROM jsonb_array_elements(nodes) n)
WHERE nodes::text LIKE '%show_choices%';

DROP TABLE IF EXISTS public.ai_practice_turns CASCADE;
DROP TABLE IF EXISTS public.ai_practice_sessions CASCADE;
DROP TABLE IF EXISTS public.exercise_ai_configs CASCADE;
DROP TABLE IF EXISTS public.ai_learner_profiles CASCADE;
DROP TABLE IF EXISTS public.ai_scenarios CASCADE;
DROP TABLE IF EXISTS public.ai_characters CASCADE;

DROP FUNCTION IF EXISTS public.ai_practice_finish(uuid);
DROP FUNCTION IF EXISTS public.ai_practice_log_turn(uuid, text, text, jsonb, jsonb, integer, text[], boolean);
DROP FUNCTION IF EXISTS public.ai_practice_preview(uuid, uuid);
DROP FUNCTION IF EXISTS public.ai_practice_start(uuid);
DROP FUNCTION IF EXISTS public.ai_set_characters_system(boolean, text);
DROP FUNCTION IF EXISTS public.ai_set_learner_age(integer);
DROP FUNCTION IF EXISTS public.ai_system_enabled();
DROP FUNCTION IF EXISTS public.ai_touch_updated_at();
DROP FUNCTION IF EXISTS public.ai_is_owner();

DELETE FROM public.app_settings WHERE key IN ('ai_characters.enabled', 'ai_characters.server_url');;
