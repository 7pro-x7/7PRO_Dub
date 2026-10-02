-- NOTE: the speech-recognition parts of this migration (STT settings, conversation_norm and the
-- keyword scoring) were replaced by 20260917130000_cartoon_conversation_no_ai.sql — no AI is used.

-- AI Character conversation questions ("CONVERSATION" kind).
--
-- A teacher (in their exercises) or the owner/admin (in the placement test) writes a short
-- scripted dialogue. In the app an original cartoon character says each line, the learner
-- answers by voice, and the character reacts. Speech recognition and speech output both run
-- on the learner's phone, so no AI server or paid API is involved.
--
-- test_questions.dialogue (jsonb):
--   {
--     "character": "CAT" | "BUNNY" | "BEAR" | "ROBOT",
--     "name": "Mimo",
--     "scene": "PARK" | "CLASSROOM" | "HOME" | "SPACE",
--     "lang": "en" | "ar",
--     "praise": "Great job!",        -- optional, said after a good answer
--     "retry": "Can you say it again?", -- optional, said after a miss
--     "closing": "Bye! See you soon!",  -- optional, said at the end
--     "max_tries": 2,
--     "turns": [
--       { "say": "Hi! What are you doing at the park today?",
--         "expect": ["playing", "play"],          -- any of these words counts; empty = any answer
--         "hint": "I'm playing with my friend.",   -- sample answer the learner can reveal
--         "audio": "https://…/line1.mp3" }         -- optional recorded voice for this line
--     ]
--   }
--
-- The learner's answer is sent in p_text as a JSON array of what they said, one entry per turn.
-- Grading is partial: earned = points × weight × (matched turns / all turns); the question counts
-- as "correct" once at least 60% of the turns were answered with an expected word.

ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS dialogue jsonb;

INSERT INTO public.app_settings (key, value, description) VALUES
  ('ai_character.stt_model_url_en',
   '"https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"'::jsonb,
   'AI Characters: zip of the on-device English speech model (Vosk, Apache-2.0). Downloaded once to the phone.'),
  ('ai_character.stt_model_url_ar', '""'::jsonb,
   'AI Characters: zip of an on-device Arabic Vosk model. Empty = Arabic dialogues are answered by typing.')
ON CONFLICT (key) DO NOTHING;

-- Same normalisation the app uses: lower case, Arabic letter variants folded, punctuation removed.
CREATE OR REPLACE FUNCTION public.conversation_norm(p text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT btrim(regexp_replace(
           regexp_replace(
             translate(lower(coalesce(p, '')), 'أإآىةـ', 'ااايه'),
             '[.,!?;:"''()\[\]{}،؟؛…\-_/\\]+', ' ', 'g'),
           '\s+', ' ', 'g'));
$$;

-- Fraction (0..1) of turns the learner answered with an expected word.
CREATE OR REPLACE FUNCTION public.conversation_score(p_dialogue jsonb, p_answers text)
RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_turns jsonb := CASE WHEN jsonb_typeof(p_dialogue->'turns') = 'array' THEN p_dialogue->'turns' ELSE '[]'::jsonb END;
  v_ans jsonb;
  v_expect jsonb;
  n int := jsonb_array_length(v_turns);
  m int := 0;
  v_a text;
  v_has_keywords boolean;
  v_ok boolean;
BEGIN
  IF n = 0 THEN RETURN 0; END IF;
  BEGIN
    v_ans := coalesce(p_answers, '[]')::jsonb;
  EXCEPTION WHEN others THEN
    v_ans := jsonb_build_array(p_answers);
  END;
  IF jsonb_typeof(v_ans) <> 'array' THEN v_ans := '[]'::jsonb; END IF;

  FOR i IN 0 .. n - 1 LOOP
    v_a := public.conversation_norm(v_ans->>i);
    CONTINUE WHEN v_a = '';
    v_expect := CASE WHEN jsonb_typeof(v_turns->i->'expect') = 'array' THEN v_turns->i->'expect' ELSE '[]'::jsonb END;
    SELECT count(*) > 0 INTO v_has_keywords
      FROM jsonb_array_elements_text(v_expect) e WHERE public.conversation_norm(e) <> '';
    IF NOT v_has_keywords THEN
      v_ok := true;
    ELSE
      SELECT count(*) > 0 INTO v_ok
        FROM jsonb_array_elements_text(v_expect) e
       WHERE public.conversation_norm(e) <> ''
         AND position(public.conversation_norm(e) IN v_a) > 0;
    END IF;
    IF v_ok THEN m := m + 1; END IF;
  END LOOP;
  RETURN round(m::numeric / n, 4);
END;
$$;

CREATE OR REPLACE FUNCTION public.next_test_question(p_attempt_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  a public.test_attempts; t public.placement_tests; q public.test_questions;
  v_available int; v_total int; v_left int; v_pending uuid;
begin
  select * into a from public.test_attempts where id = p_attempt_id and user_id = auth.uid();
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;

  select count(*) into v_available from public.test_questions where test_id = a.test_id and is_active;
  v_total := least(greatest(t.question_count, 1), greatest(v_available, 1));

  if a.status <> 'IN_PROGRESS' then return jsonb_build_object('done', true, 'total', v_total); end if;
  if a.answered_count >= v_total then return jsonb_build_object('done', true, 'total', v_total); end if;

  v_left := t.time_limit_seconds - floor(extract(epoch from (now() - a.started_at)))::int;
  if v_left <= 0 then
    return jsonb_build_object('done', true, 'reason', 'TIME_UP', 'total', v_total);
  end if;

  if coalesce(array_length(a.asked_question_ids, 1), 0) > a.answered_count then
    v_pending := a.asked_question_ids[array_length(a.asked_question_ids, 1)];
    select * into q from public.test_questions where id = v_pending and is_active;
  end if;

  if q.id is null then
    if t.is_adaptive then
      select * into q from public.test_questions
        where test_id = a.test_id and is_active and not (id = any(a.asked_question_ids))
        order by abs(difficulty - a.current_difficulty), random() limit 1;
    else
      select * into q from public.test_questions
        where test_id = a.test_id and is_active and not (id = any(a.asked_question_ids))
        order by case when t.randomize then random() else sort_order end limit 1;
    end if;

    if q.id is null then
      return jsonb_build_object('done', true, 'reason', 'NO_MORE_QUESTIONS', 'total', v_total);
    end if;

    update public.test_attempts set asked_question_ids = array_append(asked_question_ids, q.id)
      where id = a.id;
  end if;

  return jsonb_build_object(
    'done', false,
    'question', jsonb_build_object(
      'id', q.id, 'kind', q.kind, 'skill', q.skill, 'difficulty', q.difficulty,
      'prompt', q.prompt, 'options', coalesce(q.options, '[]'::jsonb),
      'image_url', q.media_image_url, 'audio_url', q.media_audio_url, 'video_url', q.media_video_url,
      'points', q.points,
      'dialogue', case when q.kind = 'CONVERSATION' then q.dialogue else null end
    ),
    'index', a.answered_count + 1,
    'total', v_total,
    'seconds_left', v_left
  );
end
$function$;

CREATE OR REPLACE FUNCTION public.submit_test_answer(p_attempt_id uuid, p_question_id uuid, p_selected integer[], p_text text, p_order integer[], p_match jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  a public.test_attempts; q public.test_questions;
  v_correct boolean := false; v_earned numeric := 0; v_norm text; v_delta int;
  v_gradable boolean; v_existing public.test_answers; v_ratio numeric := 0;
begin
  select * into a from public.test_attempts where id = p_attempt_id and user_id = auth.uid() for update;
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into q from public.test_questions where id = p_question_id and test_id = a.test_id;
  if not found then raise exception 'QUESTION_NOT_FOUND'; end if;

  select * into v_existing from public.test_answers where attempt_id = a.id and question_id = q.id;
  if found then
    return jsonb_build_object('correct', v_existing.is_correct, 'earned', v_existing.earned,
      'explanation', q.explanation, 'graded', true, 'repeat', true);
  end if;

  if a.status <> 'IN_PROGRESS' then raise exception 'ATTEMPT_CLOSED'; end if;

  v_gradable := case
    when q.kind in ('SINGLE','MULTI','TRUE_FALSE') then coalesce(array_length(q.correct_indexes, 1), 0) > 0
    when q.kind = 'TEXT' then coalesce(array_length(q.correct_text, 1), 0) > 0
    when q.kind = 'ORDERING' then coalesce(array_length(q.order_answer, 1), 0) > 0
    when q.kind = 'MATCHING' then coalesce(q.match_pairs, '{}'::jsonb) <> '{}'::jsonb
    when q.kind = 'CONVERSATION' then jsonb_typeof(q.dialogue->'turns') = 'array'
                                      and jsonb_array_length(q.dialogue->'turns') > 0
    else false
  end;
  v_gradable := coalesce(v_gradable, false);

  if v_gradable then
    if q.kind in ('SINGLE','MULTI','TRUE_FALSE') then
      v_correct := coalesce(
        (select array(select distinct unnest(coalesce(p_selected, '{}')) order by 1)
             = array(select distinct unnest(q.correct_indexes) order by 1)), false);
    elsif q.kind = 'TEXT' then
      v_norm := lower(trim(coalesce(p_text, '')));
      v_correct := v_norm <> '' and exists (
        select 1 from unnest(coalesce(q.correct_text, '{}')) ct where lower(trim(ct)) = v_norm);
    elsif q.kind = 'ORDERING' then
      v_correct := coalesce(p_order, '{}') = coalesce(q.order_answer, '{}');
    elsif q.kind = 'MATCHING' then
      v_correct := coalesce(p_match, '{}'::jsonb) = coalesce(q.match_pairs, '{}'::jsonb);
    elsif q.kind = 'CONVERSATION' then
      v_ratio := public.conversation_score(q.dialogue, p_text);
      v_correct := v_ratio >= 0.6;
    end if;
  end if;

  if v_correct then v_earned := q.points * q.weight; end if;
  -- A conversation earns partial credit for every turn answered well.
  if q.kind = 'CONVERSATION' and v_gradable then
    v_earned := round(q.points * q.weight * v_ratio, 2);
  end if;

  insert into public.test_answers(attempt_id, question_id, selected_indexes, text_answer, order_answer, match_answer, is_correct, earned)
  values (a.id, q.id, coalesce(p_selected, '{}'), p_text, p_order, p_match, v_correct, v_earned);

  v_delta := case when not v_gradable then 0 when v_correct then 1 else -1 end;

  update public.test_attempts set
    answered_count = answered_count + 1,
    correct_count = correct_count + case when v_correct then 1 else 0 end,
    raw_score = raw_score + v_earned,
    max_score = max_score + case when v_gradable then q.points * q.weight else 0 end,
    current_difficulty = greatest(1, least(6, current_difficulty + v_delta))
  where id = a.id;

  return jsonb_build_object('correct', v_correct, 'earned', v_earned,
    'explanation', q.explanation, 'graded', v_gradable,
    'ratio', case when q.kind = 'CONVERSATION' then v_ratio else null end);
end
$function$;

CREATE OR REPLACE FUNCTION public.finish_test_attempt(p_attempt_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  a public.test_attempts; t public.placement_tests;
  v_percent numeric; v_level text; v_skills jsonb; v_strengths text[]; v_weak text[];
  v_rank numeric; k text; thr numeric; v_best_level text;
begin
  select * into a from public.test_attempts where id = p_attempt_id and user_id = auth.uid() for update;
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;

  if a.status <> 'IN_PROGRESS' then
    select round(100.0 * (count(*) filter (where percent < a.percent))::numeric / greatest(count(*),1), 0)
      into v_rank from public.test_attempts where test_id = a.test_id and status = 'SUBMITTED';
    return jsonb_build_object('attempt_id', a.id, 'percent', a.percent, 'level', a.level,
      'skills', coalesce(a.skill_breakdown, '{}'::jsonb), 'strengths', coalesce(a.strengths, '{}'),
      'weaknesses', coalesce(a.weaknesses, '{}'), 'better_than_percent', v_rank,
      'passed', a.percent >= t.passing_score, 'status', a.status,
      'answered', a.answered_count, 'already', true);
  end if;

  if a.answered_count = 0 then
    update public.test_attempts set status = 'ABANDONED', submitted_at = now() where id = a.id;
    return jsonb_build_object('attempt_id', a.id, 'percent', 0, 'level', null,
      'skills', '{}'::jsonb, 'strengths', '{}', 'weaknesses', '{}',
      'better_than_percent', null, 'passed', false, 'status', 'ABANDONED', 'answered', 0);
  end if;

  v_percent := case when a.max_score > 0 then round((a.raw_score / a.max_score) * 100, 2) else 0 end;

  select coalesce(jsonb_object_agg(skill, pct), '{}'::jsonb) into v_skills from (
    select q.skill,
           round(case when sum(q.points*q.weight) > 0
                      then sum(ans.earned) / sum(q.points*q.weight) * 100 else 0 end, 0) as pct
    from public.test_answers ans join public.test_questions q on q.id = ans.question_id
    where ans.attempt_id = a.id
      and coalesce(case
            when q.kind in ('SINGLE','MULTI','TRUE_FALSE') then coalesce(array_length(q.correct_indexes, 1), 0) > 0
            when q.kind = 'TEXT' then coalesce(array_length(q.correct_text, 1), 0) > 0
            when q.kind = 'ORDERING' then coalesce(array_length(q.order_answer, 1), 0) > 0
            when q.kind = 'MATCHING' then coalesce(q.match_pairs, '{}'::jsonb) <> '{}'::jsonb
            when q.kind = 'CONVERSATION' then jsonb_typeof(q.dialogue->'turns') = 'array'
                                              and jsonb_array_length(q.dialogue->'turns') > 0
            else false
          end, false)
    group by q.skill
  ) s;

  select coalesce(array_agg(skill order by pct desc), '{}') into v_strengths from (
    select key as skill, (value#>>'{}')::numeric as pct from jsonb_each(v_skills)
    order by (value#>>'{}')::numeric desc limit 2
  ) x;
  select coalesce(array_agg(skill order by pct asc), '{}') into v_weak from (
    select key as skill, (value#>>'{}')::numeric as pct from jsonb_each(v_skills)
    order by (value#>>'{}')::numeric asc limit 2
  ) y;

  v_best_level := coalesce(t.levels[1], 'A1');
  for k, thr in select key, (value#>>'{}')::numeric from jsonb_each(t.level_thresholds) order by (value#>>'{}')::numeric asc loop
    if v_percent >= thr then v_best_level := k; end if;
  end loop;
  v_level := v_best_level;

  update public.test_attempts set status = 'SUBMITTED', submitted_at = now(),
    percent = v_percent, level = v_level, skill_breakdown = v_skills,
    strengths = v_strengths, weaknesses = v_weak
  where id = a.id;

  update public.profiles set current_level = v_level where id = auth.uid();

  select round(100.0 * (count(*) filter (where percent < v_percent))::numeric / greatest(count(*),1), 0)
    into v_rank from public.test_attempts where test_id = a.test_id and status = 'SUBMITTED';

  perform public.push_notification(auth.uid(), 'TEST_RESULT', 'Your level: ' || v_level,
    'You scored ' || v_percent || '% on ' || t.title || '.', jsonb_build_object('attempt_id', a.id, 'level', v_level));

  return jsonb_build_object('attempt_id', a.id, 'percent', v_percent, 'level', v_level,
    'skills', v_skills, 'strengths', v_strengths, 'weaknesses', v_weak,
    'better_than_percent', v_rank, 'passed', v_percent >= t.passing_score,
    'status', 'SUBMITTED', 'answered', a.answered_count);
end
$function$;
