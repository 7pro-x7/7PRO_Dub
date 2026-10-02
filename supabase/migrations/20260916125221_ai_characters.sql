CREATE OR REPLACE FUNCTION public.ai_is_owner()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'OWNER');
$$;

CREATE OR REPLACE FUNCTION public.ai_system_enabled()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT coalesce((SELECT (value #>> '{}')::boolean FROM app_settings WHERE key = 'ai_characters.enabled'), false);
$$;

INSERT INTO public.app_settings (key, value, description) VALUES
  ('ai_characters.enabled', 'false'::jsonb, 'AI Characters in exercises (owner switch).'),
  ('ai_characters.server_url', '""'::jsonb, 'wss:// address of the self-hosted open-source AI tutor server.')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.ai_characters (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug            text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]{2,40}$'),
  name_en         text NOT NULL CHECK (length(btrim(name_en)) BETWEEN 1 AND 40),
  name_ar         text NOT NULL CHECK (length(btrim(name_ar)) BETWEEN 1 AND 40),
  audience        text NOT NULL CHECK (audience IN ('KIDS', 'ADULTS')),
  species         text NOT NULL DEFAULT 'fox'
                  CHECK (species IN ('fox','bear','cat','owl','rabbit','panda','lion','penguin','coach_woman','coach_man')),
  primary_color   text NOT NULL DEFAULT '#F28C38' CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
  secondary_color text NOT NULL DEFAULT '#FFF1DE' CHECK (secondary_color ~ '^#[0-9A-Fa-f]{6}$'),
  accent_color    text NOT NULL DEFAULT '#2E7D6B' CHECK (accent_color ~ '^#[0-9A-Fa-f]{6}$'),
  accessory       text NOT NULL DEFAULT 'none' CHECK (accessory IN ('none','glasses','bow','cap','headset','scarf','tie')),
  voice_id        text NOT NULL DEFAULT 'en_US-lessac-medium' CHECK (voice_id ~ '^[A-Za-z0-9_.-]{3,60}$'),
  voice_pitch     real NOT NULL DEFAULT 0 CHECK (voice_pitch BETWEEN -6 AND 8),
  voice_speed     real NOT NULL DEFAULT 1 CHECK (voice_speed BETWEEN 0.6 AND 1.6),
  personality     text NOT NULL DEFAULT '' CHECK (length(personality) <= 1500),
  speaking_style  text NOT NULL DEFAULT '' CHECK (length(speaking_style) <= 1000),
  greeting        text NOT NULL DEFAULT '' CHECK (length(greeting) <= 300),
  gestures        text[] NOT NULL DEFAULT ARRAY['wave','nod','clap','think','point','jump'],
  min_age         int NOT NULL DEFAULT 4 CHECK (min_age BETWEEN 3 AND 99),
  max_age         int NOT NULL DEFAULT 99 CHECK (max_age BETWEEN 3 AND 120),
  levels          text[] NOT NULL DEFAULT ARRAY['A1','A2','B1','B2','C1','C2'],
  is_active       boolean NOT NULL DEFAULT true,
  sort_order      int NOT NULL DEFAULT 0,
  created_by      uuid REFERENCES auth.users(id) ON DELETE SET NULL DEFAULT auth.uid(),
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (min_age <= max_age),
  CHECK (gestures <@ ARRAY['wave','nod','clap','think','point','jump','dance','shrug']),
  CHECK (levels <@ ARRAY['A1','A2','B1','B2','C1','C2'])
);

CREATE TABLE IF NOT EXISTS public.ai_scenarios (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title_en      text NOT NULL CHECK (length(btrim(title_en)) BETWEEN 1 AND 80),
  title_ar      text NOT NULL CHECK (length(btrim(title_ar)) BETWEEN 1 AND 80),
  audience      text NOT NULL CHECK (audience IN ('KIDS', 'ADULTS', 'ALL')),
  kind          text NOT NULL DEFAULT 'CONVERSATION'
                CHECK (kind IN ('CONVERSATION','ROLEPLAY','GAME','PRONUNCIATION','VOCABULARY','GRAMMAR')),
  character_id  uuid REFERENCES public.ai_characters(id) ON DELETE SET NULL,
  situation     text NOT NULL DEFAULT '' CHECK (length(situation) <= 2000),
  ai_role       text NOT NULL DEFAULT '' CHECK (length(ai_role) <= 200),
  learner_role  text NOT NULL DEFAULT '' CHECK (length(learner_role) <= 200),
  opening_line  text NOT NULL DEFAULT '' CHECK (length(opening_line) <= 300),
  levels        text[] NOT NULL DEFAULT ARRAY['A1','A2','B1','B2','C1','C2'] CHECK (levels <@ ARRAY['A1','A2','B1','B2','C1','C2']),
  min_age       int NOT NULL DEFAULT 4 CHECK (min_age BETWEEN 3 AND 99),
  max_age       int NOT NULL DEFAULT 99 CHECK (max_age BETWEEN 3 AND 120),
  is_active     boolean NOT NULL DEFAULT true,
  sort_order    int NOT NULL DEFAULT 0,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.exercise_ai_configs (
  exercise_id      uuid PRIMARY KEY REFERENCES public.placement_tests(id) ON DELETE CASCADE,
  character_id     uuid NOT NULL REFERENCES public.ai_characters(id) ON DELETE CASCADE,
  scenario_id      uuid REFERENCES public.ai_scenarios(id) ON DELETE SET NULL,
  goal             text NOT NULL DEFAULT '' CHECK (length(goal) <= 500),
  target_words     text[] NOT NULL DEFAULT '{}' CHECK (cardinality(target_words) <= 40),
  target_sentences text[] NOT NULL DEFAULT '{}' CHECK (cardinality(target_sentences) <= 15),
  mode             text NOT NULL DEFAULT 'ALONGSIDE' CHECK (mode IN ('ALONGSIDE', 'CONVERSATION_ONLY')),
  max_turns        int NOT NULL DEFAULT 12 CHECK (max_turns BETWEEN 3 AND 40),
  updated_by       uuid REFERENCES auth.users(id) ON DELETE SET NULL DEFAULT auth.uid(),
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS exercise_ai_configs_character_idx ON public.exercise_ai_configs(character_id);

CREATE TABLE IF NOT EXISTS public.ai_learner_profiles (
  user_id    uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  age_years  int NOT NULL CHECK (age_years BETWEEN 3 AND 99),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.ai_practice_sessions (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  exercise_id       uuid REFERENCES public.placement_tests(id) ON DELETE CASCADE,
  character_id      uuid REFERENCES public.ai_characters(id) ON DELETE SET NULL,
  scenario_id       uuid REFERENCES public.ai_scenarios(id) ON DELETE SET NULL,
  status            text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','ENDED')),
  turns             int NOT NULL DEFAULT 0,
  goal_progress     int NOT NULL DEFAULT 0 CHECK (goal_progress BETWEEN 0 AND 100),
  pronunciation_avg real,
  words_used        text[] NOT NULL DEFAULT '{}',
  started_at        timestamptz NOT NULL DEFAULT now(),
  ended_at          timestamptz
);
CREATE INDEX IF NOT EXISTS ai_practice_sessions_user_idx ON public.ai_practice_sessions(user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS ai_practice_sessions_exercise_idx ON public.ai_practice_sessions(exercise_id);

CREATE TABLE IF NOT EXISTS public.ai_practice_turns (
  id            bigserial PRIMARY KEY,
  session_id    uuid NOT NULL REFERENCES public.ai_practice_sessions(id) ON DELETE CASCADE,
  role          text NOT NULL CHECK (role IN ('LEARNER','CHARACTER')),
  body          text NOT NULL CHECK (length(body) <= 2000),
  corrections   jsonb NOT NULL DEFAULT '[]',
  pronunciation jsonb NOT NULL DEFAULT '[]',
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ai_practice_turns_session_idx ON public.ai_practice_turns(session_id, id);

ALTER TABLE public.ai_characters        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_scenarios         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exercise_ai_configs  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_learner_profiles  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_practice_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_practice_turns    ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ai_characters_read ON public.ai_characters;
CREATE POLICY ai_characters_read ON public.ai_characters FOR SELECT TO authenticated
  USING (is_active OR public.ai_is_owner());
DROP POLICY IF EXISTS ai_characters_owner ON public.ai_characters;
CREATE POLICY ai_characters_owner ON public.ai_characters FOR ALL TO authenticated
  USING (public.ai_is_owner()) WITH CHECK (public.ai_is_owner());

DROP POLICY IF EXISTS ai_scenarios_read ON public.ai_scenarios;
CREATE POLICY ai_scenarios_read ON public.ai_scenarios FOR SELECT TO authenticated
  USING (is_active OR public.ai_is_owner());
DROP POLICY IF EXISTS ai_scenarios_owner ON public.ai_scenarios;
CREATE POLICY ai_scenarios_owner ON public.ai_scenarios FOR ALL TO authenticated
  USING (public.ai_is_owner()) WITH CHECK (public.ai_is_owner());

DROP POLICY IF EXISTS exercise_ai_configs_read ON public.exercise_ai_configs;
CREATE POLICY exercise_ai_configs_read ON public.exercise_ai_configs FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.placement_tests t WHERE t.id = exercise_id));
DROP POLICY IF EXISTS exercise_ai_configs_write ON public.exercise_ai_configs;
CREATE POLICY exercise_ai_configs_write ON public.exercise_ai_configs FOR ALL TO authenticated
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

DROP POLICY IF EXISTS ai_learner_profiles_self ON public.ai_learner_profiles;
CREATE POLICY ai_learner_profiles_self ON public.ai_learner_profiles FOR ALL TO authenticated
  USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS ai_practice_sessions_read ON public.ai_practice_sessions;
CREATE POLICY ai_practice_sessions_read ON public.ai_practice_sessions FOR SELECT TO authenticated
  USING (
    user_id = auth.uid() OR public.ai_is_owner() OR public.has_permission('tests.manage')
    OR EXISTS (SELECT 1 FROM public.placement_tests t WHERE t.id = exercise_id AND t.owner_id = auth.uid())
  );
DROP POLICY IF EXISTS ai_practice_turns_read ON public.ai_practice_turns;
CREATE POLICY ai_practice_turns_read ON public.ai_practice_turns FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.ai_practice_sessions s WHERE s.id = session_id));

REVOKE ALL ON public.ai_practice_sessions, public.ai_practice_turns FROM anon;
REVOKE INSERT, UPDATE, DELETE ON public.ai_practice_sessions, public.ai_practice_turns FROM authenticated;
REVOKE ALL ON public.ai_characters, public.ai_scenarios, public.exercise_ai_configs, public.ai_learner_profiles FROM anon;

CREATE OR REPLACE FUNCTION public.ai_touch_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := now(); RETURN NEW; END $$;
DROP TRIGGER IF EXISTS ai_characters_touch ON public.ai_characters;
CREATE TRIGGER ai_characters_touch BEFORE UPDATE ON public.ai_characters FOR EACH ROW EXECUTE FUNCTION public.ai_touch_updated_at();
DROP TRIGGER IF EXISTS ai_scenarios_touch ON public.ai_scenarios;
CREATE TRIGGER ai_scenarios_touch BEFORE UPDATE ON public.ai_scenarios FOR EACH ROW EXECUTE FUNCTION public.ai_touch_updated_at();
DROP TRIGGER IF EXISTS exercise_ai_configs_touch ON public.exercise_ai_configs;
CREATE TRIGGER exercise_ai_configs_touch BEFORE UPDATE ON public.exercise_ai_configs FOR EACH ROW EXECUTE FUNCTION public.ai_touch_updated_at();

CREATE OR REPLACE FUNCTION public.ai_set_characters_system(p_enabled boolean, p_server_url text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_url text := btrim(coalesce(p_server_url, ''));
BEGIN
  IF NOT public.ai_is_owner() THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF v_url <> '' AND v_url !~* '^wss?://' THEN RAISE EXCEPTION 'INVALID_URL'; END IF;
  IF p_enabled AND v_url = '' THEN RAISE EXCEPTION 'SERVER_URL_REQUIRED'; END IF;
  INSERT INTO app_settings (key, value, updated_by, updated_at) VALUES
    ('ai_characters.enabled', to_jsonb(coalesce(p_enabled, false)), auth.uid(), now()),
    ('ai_characters.server_url', to_jsonb(v_url), auth.uid(), now())
  ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;
  PERFORM public.write_audit('settings.update', 'setting', 'ai_characters',
    jsonb_build_object('enabled', coalesce(p_enabled, false), 'server_url', v_url));
  RETURN jsonb_build_object('ok', true);
END $$;

CREATE OR REPLACE FUNCTION public.ai_set_learner_age(p_age int)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
  IF p_age IS NULL OR p_age < 3 OR p_age > 99 THEN RAISE EXCEPTION 'INVALID_AGE'; END IF;
  INSERT INTO ai_learner_profiles (user_id, age_years, updated_at) VALUES (auth.uid(), p_age, now())
  ON CONFLICT (user_id) DO UPDATE SET age_years = excluded.age_years, updated_at = now();
END $$;

CREATE OR REPLACE FUNCTION public.ai_character_payload(p_character public.ai_characters)
RETURNS jsonb LANGUAGE sql STABLE AS $$
  SELECT jsonb_build_object(
    'id', p_character.id, 'slug', p_character.slug, 'name_en', p_character.name_en, 'name_ar', p_character.name_ar,
    'audience', p_character.audience, 'species', p_character.species,
    'primary_color', p_character.primary_color, 'secondary_color', p_character.secondary_color,
    'accent_color', p_character.accent_color, 'accessory', p_character.accessory,
    'voice_id', p_character.voice_id, 'voice_pitch', p_character.voice_pitch, 'voice_speed', p_character.voice_speed,
    'personality', p_character.personality, 'speaking_style', p_character.speaking_style,
    'greeting', p_character.greeting, 'gestures', to_jsonb(p_character.gestures),
    'min_age', p_character.min_age, 'max_age', p_character.max_age, 'levels', to_jsonb(p_character.levels));
$$;

CREATE OR REPLACE FUNCTION public.ai_scenario_payload(p_scenario public.ai_scenarios)
RETURNS jsonb LANGUAGE sql STABLE AS $$
  SELECT CASE WHEN p_scenario.id IS NULL THEN NULL ELSE jsonb_build_object(
    'id', p_scenario.id, 'title_en', p_scenario.title_en, 'title_ar', p_scenario.title_ar,
    'kind', p_scenario.kind, 'situation', p_scenario.situation, 'ai_role', p_scenario.ai_role,
    'learner_role', p_scenario.learner_role, 'opening_line', p_scenario.opening_line) END;
$$;

CREATE OR REPLACE FUNCTION public.ai_practice_start(p_exercise_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_test placement_tests;
  v_cfg exercise_ai_configs;
  v_char ai_characters;
  v_scn ai_scenarios;
  v_prof profiles;
  v_age int;
  v_session uuid;
  v_questions jsonb;
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('allowed', false, 'reason', 'UNAUTHORIZED'); END IF;
  IF NOT ai_system_enabled() THEN RETURN jsonb_build_object('allowed', false, 'reason', 'FEATURE_DISABLED'); END IF;

  SELECT * INTO v_test FROM placement_tests WHERE id = p_exercise_id AND kind = 'EXERCISE';
  IF v_test.id IS NULL THEN RETURN jsonb_build_object('allowed', false, 'reason', 'NOT_FOUND'); END IF;
  IF v_test.status::text <> 'PUBLISHED' AND v_test.owner_id <> v_uid AND NOT has_permission('tests.manage') THEN
    RETURN jsonb_build_object('allowed', false, 'reason', 'NOT_AUTHORIZED');
  END IF;

  SELECT * INTO v_cfg FROM exercise_ai_configs WHERE exercise_id = p_exercise_id;
  IF v_cfg.exercise_id IS NULL THEN RETURN jsonb_build_object('allowed', false, 'reason', 'NO_CHARACTER'); END IF;
  SELECT * INTO v_char FROM ai_characters WHERE id = v_cfg.character_id AND is_active;
  IF v_char.id IS NULL THEN RETURN jsonb_build_object('allowed', false, 'reason', 'CHARACTER_DISABLED'); END IF;
  IF v_cfg.scenario_id IS NOT NULL THEN
    SELECT * INTO v_scn FROM ai_scenarios WHERE id = v_cfg.scenario_id AND is_active;
  END IF;

  SELECT * INTO v_prof FROM profiles WHERE id = v_uid;
  SELECT age_years INTO v_age FROM ai_learner_profiles WHERE user_id = v_uid;

  SELECT coalesce(jsonb_agg(q.prompt ORDER BY q.sort_order), '[]'::jsonb) INTO v_questions
  FROM (SELECT prompt, sort_order FROM test_questions WHERE test_id = p_exercise_id ORDER BY sort_order LIMIT 30) q;

  SELECT id INTO v_session FROM ai_practice_sessions
  WHERE user_id = v_uid AND exercise_id = p_exercise_id AND status = 'ACTIVE' AND started_at > now() - interval '2 hours'
  ORDER BY started_at DESC LIMIT 1;
  IF v_session IS NULL THEN
    INSERT INTO ai_practice_sessions (user_id, exercise_id, character_id, scenario_id)
    VALUES (v_uid, p_exercise_id, v_char.id, v_scn.id) RETURNING id INTO v_session;
  END IF;

  RETURN jsonb_build_object(
    'allowed', true,
    'session_id', v_session,
    'character', ai_character_payload(v_char),
    'scenario', ai_scenario_payload(v_scn),
    'config', jsonb_build_object('goal', v_cfg.goal, 'target_words', to_jsonb(v_cfg.target_words),
      'target_sentences', to_jsonb(v_cfg.target_sentences), 'mode', v_cfg.mode, 'max_turns', v_cfg.max_turns),
    'exercise', jsonb_build_object('id', v_test.id,
      'title', coalesce(nullif(v_test.title_en, ''), v_test.title),
      'description', coalesce(nullif(v_test.description_en, ''), v_test.description, ''),
      'level', v_test.target_level, 'questions', v_questions),
    'learner', jsonb_build_object('first_name', split_part(coalesce(v_prof.full_name, ''), ' ', 1),
      'level', v_prof.current_level, 'age', v_age, 'locale', v_prof.locale),
    'history', coalesce((SELECT jsonb_agg(jsonb_build_object('role', t.role, 'text', t.body) ORDER BY t.id)
       FROM (SELECT * FROM ai_practice_turns WHERE session_id = v_session ORDER BY id DESC LIMIT 12) t), '[]'::jsonb)
  );
END $$;

CREATE OR REPLACE FUNCTION public.ai_practice_preview(p_character_id uuid, p_scenario_id uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_char ai_characters; v_scn ai_scenarios;
BEGIN
  IF NOT ai_is_owner() THEN RETURN jsonb_build_object('allowed', false, 'reason', 'NOT_AUTHORIZED'); END IF;
  SELECT * INTO v_char FROM ai_characters WHERE id = p_character_id;
  IF v_char.id IS NULL THEN RETURN jsonb_build_object('allowed', false, 'reason', 'NOT_FOUND'); END IF;
  IF p_scenario_id IS NOT NULL THEN SELECT * INTO v_scn FROM ai_scenarios WHERE id = p_scenario_id; END IF;
  RETURN jsonb_build_object(
    'allowed', true, 'session_id', NULL,
    'character', ai_character_payload(v_char),
    'scenario', ai_scenario_payload(v_scn),
    'config', jsonb_build_object('goal', 'Owner preview: show your personality and teaching style.',
      'target_words', '[]'::jsonb, 'target_sentences', '[]'::jsonb, 'mode', 'CONVERSATION_ONLY', 'max_turns', 40),
    'exercise', jsonb_build_object('id', NULL, 'title', 'Preview', 'description', '', 'level', NULL, 'questions', '[]'::jsonb),
    'learner', jsonb_build_object('first_name', '', 'level', NULL,
      'age', CASE WHEN v_char.audience = 'KIDS' THEN greatest(v_char.min_age, 7) ELSE NULL END, 'locale', 'en'),
    'history', '[]'::jsonb);
END $$;

CREATE OR REPLACE FUNCTION public.ai_practice_log_turn(
  p_session_id uuid, p_role text, p_body text,
  p_corrections jsonb DEFAULT '[]', p_pronunciation jsonb DEFAULT '[]',
  p_goal_progress int DEFAULT NULL, p_words_used text[] DEFAULT NULL, p_completed boolean DEFAULT false
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_s ai_practice_sessions; v_avg real;
BEGIN
  SELECT * INTO v_s FROM ai_practice_sessions WHERE id = p_session_id FOR UPDATE;
  IF v_s.id IS NULL OR v_s.user_id <> auth.uid() THEN RAISE EXCEPTION 'NOT_AUTHORIZED'; END IF;
  IF v_s.status <> 'ACTIVE' THEN RETURN; END IF;
  IF p_role NOT IN ('LEARNER', 'CHARACTER') THEN RAISE EXCEPTION 'INVALID_ROLE'; END IF;

  INSERT INTO ai_practice_turns (session_id, role, body, corrections, pronunciation)
  VALUES (p_session_id, p_role, left(coalesce(p_body, ''), 2000),
          CASE WHEN jsonb_typeof(p_corrections) = 'array' THEN p_corrections ELSE '[]' END,
          CASE WHEN jsonb_typeof(p_pronunciation) = 'array' THEN p_pronunciation ELSE '[]' END);

  SELECT avg((e->>'score')::real) INTO v_avg
  FROM ai_practice_turns t, jsonb_array_elements(t.pronunciation) e
  WHERE t.session_id = p_session_id AND e ? 'score';

  UPDATE ai_practice_sessions SET
    turns = turns + CASE WHEN p_role = 'LEARNER' THEN 1 ELSE 0 END,
    goal_progress = greatest(goal_progress, least(100, greatest(0, coalesce(p_goal_progress, goal_progress)))),
    pronunciation_avg = v_avg,
    words_used = (SELECT coalesce(array_agg(DISTINCT w), '{}') FROM unnest(words_used || coalesce(p_words_used, '{}')) w),
    status = CASE WHEN p_completed THEN 'COMPLETED' ELSE status END,
    ended_at = CASE WHEN p_completed THEN now() ELSE ended_at END
  WHERE id = p_session_id;
END $$;

CREATE OR REPLACE FUNCTION public.ai_practice_finish(p_session_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE ai_practice_sessions SET status = 'ENDED', ended_at = now()
  WHERE id = p_session_id AND user_id = auth.uid() AND status = 'ACTIVE';
END $$;

REVOKE ALL ON FUNCTION public.ai_set_characters_system(boolean, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_set_learner_age(int) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_practice_start(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_practice_preview(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_practice_log_turn(uuid, text, text, jsonb, jsonb, int, text[], boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.ai_practice_finish(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ai_set_characters_system(boolean, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_set_learner_age(int) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_practice_start(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_practice_preview(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_practice_log_turn(uuid, text, text, jsonb, jsonb, int, text[], boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ai_practice_finish(uuid) TO authenticated;

INSERT INTO public.ai_characters
  (slug, name_en, name_ar, audience, species, primary_color, secondary_color, accent_color, accessory,
   voice_id, voice_pitch, voice_speed, personality, speaking_style, greeting, min_age, max_age, levels, sort_order)
VALUES
  ('pip-fox', 'Pip', 'بيب', 'KIDS', 'fox', '#F28C38', '#FFF1DE', '#2E7D6B', 'scarf',
   'en_US-amy-medium', 4, 0.9,
   'A cheerful, curious little fox who loves games, songs and treasure hunts. Endlessly patient, celebrates every try.',
   'Very short sentences. Simple words. Lots of praise and playful sounds like "Yay!" and "Ooh!". Asks one question at a time.',
   'Hi hi! I am Pip the fox! Do you want to play an English game with me?', 4, 10, ARRAY['A1','A2'], 1),
  ('bubba-bear', 'Bubba', 'بَبّا', 'KIDS', 'bear', '#8D5A3B', '#F3DFC8', '#E4574B', 'bow',
   'en_US-ryan-medium', 2, 0.88,
   'A big, gentle, funny bear who loves snacks and stories and sometimes makes silly mistakes for the child to fix.',
   'Slow, warm, silly. Uses role play and pretend adventures. Repeats new words twice.',
   'Hello, little friend! I am Bubba the bear. Shall we go on an adventure?', 5, 12, ARRAY['A1','A2','B1'], 2),
  ('olive-owl', 'Olive', 'أوليف', 'KIDS', 'owl', '#6C5BA7', '#EDE7F6', '#F5B942', 'glasses',
   'en_GB-alba-medium', 3, 0.92,
   'A wise, encouraging owl who loves words, riddles and spelling games.',
   'Clear and bright. Gives hints before answers. Turns vocabulary into riddles.',
   'Hoo-hoo! I am Olive the owl. Can you guess my riddle?', 6, 13, ARRAY['A1','A2','B1'], 3),
  ('coach-maya', 'Maya', 'مايا', 'ADULTS', 'coach_woman', '#2E7D6B', '#E0F2EE', '#F28C38', 'headset',
   'en_US-lessac-medium', 0, 1.0,
   'A friendly, professional English coach. Encouraging but honest; focuses on real-life fluency.',
   'Natural, conversational English matched to the learner''s level. Corrects gently with a better way to say it.',
   'Hi, I''m Maya, your English coach. What would you like to practise today?', 14, 99, ARRAY['A2','B1','B2','C1','C2'], 10),
  ('coach-adam', 'Adam', 'آدم', 'ADULTS', 'coach_man', '#34495E', '#E6ECF2', '#F5B942', 'tie',
   'en_US-joel-medium', 0, 1.0,
   'A calm business-English coach who prepares learners for interviews, meetings and travel.',
   'Professional and concise. Plays realistic roles. Gives one clear improvement per turn.',
   'Hello, I''m Adam. Ready to practise for a real situation?', 16, 99, ARRAY['A2','B1','B2','C1','C2'], 11)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO public.ai_scenarios (title_en, title_ar, audience, kind, situation, ai_role, learner_role, opening_line, levels, min_age, max_age, sort_order)
SELECT * FROM (VALUES
  ('Animal guessing game', 'لعبة تخمين الحيوانات', 'KIDS', 'GAME',
   'Play a guessing game: describe an animal with 2 simple clues and let the child guess. Swap turns. Count points.',
   'Game host', 'Player', 'Let''s play! I am thinking of an animal. It is big and grey. What is it?', ARRAY['A1','A2'], 4, 10, 1),
  ('At the fruit shop', 'في محل الفاكهة', 'KIDS', 'ROLEPLAY',
   'Pretend shop. The child buys fruit, says colours and numbers, and says please and thank you.',
   'Shopkeeper', 'Customer', 'Welcome to my fruit shop! What would you like today?', ARRAY['A1','A2'], 4, 11, 2),
  ('Say it like me', 'قولها زيّي', 'KIDS', 'PRONUNCIATION',
   'Pronunciation practice: say one target word or short sentence, ask the child to repeat, and help with any sound they miss.',
   'Coach', 'Learner', 'Listen and say it after me!', ARRAY['A1','A2','B1'], 4, 13, 3),
  ('Hotel check-in', 'تسجيل الدخول في الفندق', 'ADULTS', 'ROLEPLAY',
   'Realistic hotel check-in abroad: booking name, dates, room preferences, breakfast, a small problem to solve.',
   'Hotel receptionist', 'Guest', 'Good evening, welcome to the Grand Hotel. Do you have a reservation?', ARRAY['A2','B1','B2'], 14, 99, 10),
  ('Job interview', 'مقابلة عمل', 'ADULTS', 'ROLEPLAY',
   'A realistic job interview: introduction, strengths, experience, a situational question, and questions for the interviewer.',
   'Hiring manager', 'Candidate', 'Thanks for coming in today. Could you start by telling me a little about yourself?', ARRAY['B1','B2','C1','C2'], 16, 99, 11),
  ('Team meeting', 'اجتماع فريق', 'ADULTS', 'ROLEPLAY',
   'A weekly team meeting: give a status update, agree on next steps, politely disagree and ask for clarification.',
   'Team lead', 'Team member', 'Morning everyone. Let''s start with your update — how is your project going?', ARRAY['B1','B2','C1'], 16, 99, 12),
  ('Everyday conversation', 'محادثة يومية', 'ALL', 'CONVERSATION',
   'Free, friendly conversation about the learner''s day, hobbies and plans, practising the exercise target language.',
   'Friend', 'Friend', '', ARRAY['A1','A2','B1','B2','C1','C2'], 4, 99, 20)
) AS v(title_en, title_ar, audience, kind, situation, ai_role, learner_role, opening_line, levels, min_age, max_age, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM public.ai_scenarios);;
