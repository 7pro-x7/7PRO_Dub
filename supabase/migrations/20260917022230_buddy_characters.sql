CREATE OR REPLACE FUNCTION public.buddy_can_manage()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT public.ai_is_owner() OR public.has_permission('tests.manage');
$$;
REVOKE ALL ON FUNCTION public.buddy_can_manage() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.buddy_can_manage() TO authenticated;

INSERT INTO public.app_settings (key, value, description) VALUES
  ('buddy.enabled', 'false'::jsonb, 'Interactive cartoon characters without AI (scripted scenarios).')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.buddy_characters (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]{2,40}$'),
  name_en text NOT NULL CHECK (char_length(name_en) BETWEEN 1 AND 40),
  name_ar text NOT NULL CHECK (char_length(name_ar) BETWEEN 1 AND 40),
  species text NOT NULL DEFAULT 'rabbit',
  primary_color text NOT NULL DEFAULT '#F5B942' CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
  secondary_color text NOT NULL DEFAULT '#FFF1DE' CHECK (secondary_color ~ '^#[0-9A-Fa-f]{6}$'),
  accent_color text NOT NULL DEFAULT '#E4574B' CHECK (accent_color ~ '^#[0-9A-Fa-f]{6}$'),
  accessory text NOT NULL DEFAULT 'bow',
  voice_en text NOT NULL DEFAULT 'af_sky',
  voice_ar text NOT NULL DEFAULT 'ar_JO-kareem-medium',
  voice_pitch real NOT NULL DEFAULT 3 CHECK (voice_pitch BETWEEN -6 AND 8),
  voice_speed real NOT NULL DEFAULT 0.95 CHECK (voice_speed BETWEEN 0.6 AND 1.6),
  gestures text[] NOT NULL DEFAULT ARRAY['wave','nod','clap','jump','dance','think'],
  is_active boolean NOT NULL DEFAULT true,
  sort_order int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.buddy_scenarios (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title_en text NOT NULL CHECK (char_length(title_en) BETWEEN 1 AND 80),
  title_ar text NOT NULL CHECK (char_length(title_ar) BETWEEN 1 AND 80),
  language text NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'ar')),
  character_id uuid REFERENCES public.buddy_characters(id) ON DELETE SET NULL,
  min_age int NOT NULL DEFAULT 4 CHECK (min_age BETWEEN 2 AND 18),
  max_age int NOT NULL DEFAULT 10 CHECK (max_age BETWEEN 2 AND 18),
  start_node text NOT NULL DEFAULT 'start',
  nodes jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(nodes) = 'array'),
  is_active boolean NOT NULL DEFAULT true,
  sort_order int NOT NULL DEFAULT 0,
  created_by uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (min_age <= max_age),
  CHECK (pg_column_size(nodes) < 200000)
);

CREATE TABLE IF NOT EXISTS public.exercise_buddy_configs (
  exercise_id uuid PRIMARY KEY REFERENCES public.placement_tests(id) ON DELETE CASCADE,
  scenario_id uuid NOT NULL REFERENCES public.buddy_scenarios(id) ON DELETE CASCADE,
  updated_by uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.buddy_results (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  scenario_id uuid NOT NULL REFERENCES public.buddy_scenarios(id) ON DELETE CASCADE,
  exercise_id uuid REFERENCES public.placement_tests(id) ON DELETE SET NULL,
  correct int NOT NULL DEFAULT 0 CHECK (correct >= 0),
  attempts int NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  completed boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS buddy_results_user_idx ON public.buddy_results (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS buddy_results_exercise_idx ON public.buddy_results (exercise_id);

ALTER TABLE public.buddy_characters ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.buddy_scenarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exercise_buddy_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.buddy_results ENABLE ROW LEVEL SECURITY;

CREATE POLICY buddy_characters_read ON public.buddy_characters FOR SELECT TO authenticated
  USING (is_active OR public.buddy_can_manage());
CREATE POLICY buddy_characters_write ON public.buddy_characters FOR ALL TO authenticated
  USING (public.buddy_can_manage()) WITH CHECK (public.buddy_can_manage());

CREATE POLICY buddy_scenarios_read ON public.buddy_scenarios FOR SELECT TO authenticated
  USING (is_active OR public.buddy_can_manage());
CREATE POLICY buddy_scenarios_write ON public.buddy_scenarios FOR ALL TO authenticated
  USING (public.buddy_can_manage()) WITH CHECK (public.buddy_can_manage());

CREATE POLICY exercise_buddy_read ON public.exercise_buddy_configs FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.placement_tests t WHERE t.id = exercise_id));
CREATE POLICY exercise_buddy_write ON public.exercise_buddy_configs FOR ALL TO authenticated
  USING (
    public.has_permission('tests.manage')
    OR EXISTS (SELECT 1 FROM public.placement_tests t
               WHERE t.id = exercise_id AND t.kind = 'EXERCISE' AND t.owner_id = auth.uid()
                 AND public.is_approved_teacher(auth.uid()))
  )
  WITH CHECK (
    public.has_permission('tests.manage')
    OR EXISTS (SELECT 1 FROM public.placement_tests t
               WHERE t.id = exercise_id AND t.kind = 'EXERCISE' AND t.owner_id = auth.uid()
                 AND public.is_approved_teacher(auth.uid()))
  );

CREATE POLICY buddy_results_read ON public.buddy_results FOR SELECT TO authenticated
  USING (
    user_id = auth.uid() OR public.buddy_can_manage()
    OR EXISTS (SELECT 1 FROM public.placement_tests t WHERE t.id = exercise_id AND t.owner_id = auth.uid())
  );

REVOKE ALL ON public.buddy_characters, public.buddy_scenarios, public.exercise_buddy_configs, public.buddy_results FROM anon;
REVOKE INSERT, UPDATE, DELETE ON public.buddy_results FROM authenticated;

CREATE TRIGGER buddy_characters_touch BEFORE UPDATE ON public.buddy_characters FOR EACH ROW EXECUTE FUNCTION public.ai_touch_updated_at();
CREATE TRIGGER buddy_scenarios_touch BEFORE UPDATE ON public.buddy_scenarios FOR EACH ROW EXECUTE FUNCTION public.ai_touch_updated_at();
CREATE TRIGGER exercise_buddy_touch BEFORE UPDATE ON public.exercise_buddy_configs FOR EACH ROW EXECUTE FUNCTION public.ai_touch_updated_at();

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

CREATE OR REPLACE FUNCTION public.buddy_log_result(
  p_scenario_id uuid, p_exercise_id uuid, p_correct int, p_attempts int, p_completed boolean
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_id uuid;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
  IF NOT EXISTS (SELECT 1 FROM buddy_scenarios WHERE id = p_scenario_id) THEN RAISE EXCEPTION 'NOT_FOUND'; END IF;
  IF p_exercise_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM exercise_buddy_configs c WHERE c.exercise_id = p_exercise_id AND c.scenario_id = p_scenario_id
  ) THEN RAISE EXCEPTION 'NOT_FOUND'; END IF;
  IF EXISTS (SELECT 1 FROM buddy_results WHERE user_id = auth.uid() AND scenario_id = p_scenario_id
             AND created_at > now() - interval '10 seconds') THEN
    RETURN NULL;
  END IF;
  INSERT INTO buddy_results (user_id, scenario_id, exercise_id, correct, attempts, completed)
  VALUES (auth.uid(), p_scenario_id, p_exercise_id,
          least(greatest(coalesce(p_correct, 0), 0), 500),
          least(greatest(coalesce(p_attempts, 0), 0), 1000),
          coalesce(p_completed, false))
  RETURNING id INTO v_id;
  RETURN v_id;
END $$;

REVOKE ALL ON FUNCTION public.buddy_set_enabled(boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.buddy_log_result(uuid, uuid, int, int, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.buddy_set_enabled(boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.buddy_log_result(uuid, uuid, int, int, boolean) TO authenticated;

CREATE POLICY buddy_audio_upload ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'course-media'
    AND (storage.foldername(name))[1] = auth.uid()::text
    AND (storage.foldername(name))[2] = 'buddy-audio'
    AND public.buddy_can_manage()
  );

INSERT INTO public.buddy_characters (slug, name_en, name_ar, species, primary_color, secondary_color, accent_color, accessory, voice_en, voice_ar, voice_pitch, voice_speed, sort_order)
VALUES
  ('lulu-bunny', 'Lulu', 'لولو', 'rabbit', '#EC7FA9', '#FFF1DE', '#E4574B', 'bow', 'af_sky', 'ar_JO-kareem-medium', 4, 0.95, 1),
  ('momo-cat', 'Momo', 'مومو', 'cat', '#F5B942', '#FFF1DE', '#3A86C8', 'scarf', 'am_puck', 'ar_JO-kareem-medium', 2, 1.0, 2)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO public.buddy_scenarios (title_en, title_ar, language, character_id, min_age, max_age, start_node, nodes, sort_order)
SELECT 'Colours game', 'لعبة الألوان', 'en', c.id, 4, 9, 'start', $json$[
  {"id":"start","say":"Hi! I'm Lulu! Let's play a colour game. What colour is a banana?","emotion":"excited","gesture":"wave","listen":true,"show_choices":true,
   "choices":[{"answers":["yellow"],"reply":"Yes! Yellow! Great job!","next":"q2","correct":true},
              {"answers":["red","blue","green"],"reply":"Hmm, not quite. Bananas are yellow!","next":"q2","correct":false}],
   "retry_say":"Can you say it again? What colour is a banana?","retries":2,"fallback_next":"q2"},
  {"id":"q2","say":"What colour is the sky?","emotion":"happy","gesture":"point","listen":true,"show_choices":true,
   "choices":[{"answers":["blue"],"reply":"Blue! You are amazing!","next":"end","correct":true},
              {"answers":["yellow","red","green"],"reply":"Look up! The sky is blue!","next":"end","correct":false}],
   "retry_say":"Try again! What colour is the sky?","retries":2,"fallback_next":"end"},
  {"id":"end","say":"Yay! You finished the colour game! Bye bye!","emotion":"excited","gesture":"dance","listen":false}
]$json$::jsonb, 1
FROM public.buddy_characters c WHERE c.slug = 'lulu-bunny'
AND NOT EXISTS (SELECT 1 FROM public.buddy_scenarios WHERE title_en = 'Colours game');

INSERT INTO public.buddy_scenarios (title_en, title_ar, language, character_id, min_age, max_age, start_node, nodes, sort_order)
SELECT 'Counting with Momo', 'نعدّ مع مومو', 'ar', c.id, 4, 8, 'start', $json$[
  {"id":"start","say":"أهلاً! أنا مومو. تعالى نعدّ سوا! كام رجل عند القطة؟","emotion":"happy","gesture":"wave","listen":true,"show_choices":true,
   "choices":[{"answers":["4","أربعة","اربعه","أربع"],"reply":"برافو! القطة عندها أربع رجلين!","next":"q2","correct":true},
              {"answers":["2","3","5","اتنين","تلاتة","خمسة"],"reply":"قربت! عدّ تاني… القطة عندها أربعة.","next":"q2","correct":false}],
   "retry_say":"ممكن تقولها تاني؟ كام رجل عند القطة؟","retries":2,"fallback_next":"q2"},
  {"id":"q2","say":"وكام عين عندك؟","emotion":"thinking","gesture":"think","listen":true,"show_choices":true,
   "choices":[{"answers":["2","اتنين","اثنين","اثنان"],"reply":"صح! عندك عينين حلوين!","next":"end","correct":true},
              {"answers":["1","3","4","واحد","تلاتة","أربعة"],"reply":"لأ يا بطل، عندنا عينين اتنين.","next":"end","correct":false}],
   "retry_say":"جرّب تاني… كام عين عندك؟","retries":2,"fallback_next":"end"},
  {"id":"end","say":"شاطر جداً! خلصنا اللعبة. باي باي!","emotion":"excited","gesture":"jump","listen":false}
]$json$::jsonb, 2
FROM public.buddy_characters c WHERE c.slug = 'momo-cat'
AND NOT EXISTS (SELECT 1 FROM public.buddy_scenarios WHERE title_en = 'Counting with Momo');;
