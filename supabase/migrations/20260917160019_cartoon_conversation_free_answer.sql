-- Cartoon conversations: grade and validate free-answer turns (typed, or said out loud then
-- typed), on top of the existing tap-a-reply and listen-only turns. No AI on either side: the
-- Android app does the text comparison (see FreeAnswerMatch) and only tells the server whether
-- it matched.

CREATE OR REPLACE FUNCTION public.conversation_score(p_dialogue jsonb, p_answers text)
RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_turns jsonb := CASE WHEN jsonb_typeof(p_dialogue->'turns') = 'array' THEN p_dialogue->'turns' ELSE '[]'::jsonb END;
  v_ans jsonb;
  n int := jsonb_array_length(v_turns);
  m int := 0;
  v_pick text;
  v_correct text;
BEGIN
  IF n = 0 THEN RETURN 0; END IF;
  BEGIN
    v_ans := coalesce(p_answers, '[]')::jsonb;
  EXCEPTION WHEN others THEN
    v_ans := '[]'::jsonb;
  END;
  IF jsonb_typeof(v_ans) <> 'array' THEN v_ans := '[]'::jsonb; END IF;

  FOR i IN 0 .. n - 1 LOOP
    v_pick := v_ans->>i;
    v_correct := v_turns->i->>'correct';
    IF jsonb_typeof(v_turns->i->'options') = 'array'
       AND jsonb_array_length(v_turns->i->'options') > 0 THEN
      IF v_pick IS NOT NULL AND v_correct IS NOT NULL AND v_pick = v_correct THEN m := m + 1; END IF;
    ELSIF jsonb_typeof(v_turns->i->'acceptedText') = 'array'
       AND jsonb_array_length(v_turns->i->'acceptedText') > 0 THEN
      IF v_pick = '1' THEN m := m + 1; END IF;
    ELSE
      IF v_pick IS NOT NULL THEN m := m + 1; END IF;
    END IF;
  END LOOP;
  RETURN round(m::numeric / n, 4);
END;
$$;

-- Same defensive shape as the existing options/text checks: a free-answer turn needs at least
-- one non-blank accepted wording, same way a tap-a-reply turn needs at least 2 real options.
CREATE OR REPLACE FUNCTION public.validate_test_question_payload()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_options text[];
  v_norm text[];
  v_correct integer;
  v_turn jsonb;
  v_turns jsonb;
BEGIN
  IF NEW.kind IN ('SINGLE','MULTI','TRUE_FALSE') THEN
    v_options := COALESCE(NEW.options, '{}');
    IF COALESCE(array_length(v_options, 1), 0) < 2 THEN
      RAISE EXCEPTION 'QUESTION_OPTIONS_REQUIRED';
    END IF;
    v_norm := ARRAY(SELECT lower(btrim(x)) FROM unnest(v_options) x);
    IF EXISTS (SELECT 1 FROM unnest(v_norm) x GROUP BY x HAVING count(*) > 1) THEN
      RAISE EXCEPTION 'QUESTION_OPTIONS_DUPLICATE';
    END IF;
    IF COALESCE(array_length(NEW.correct_indexes, 1), 0) = 0
       OR EXISTS (SELECT 1 FROM unnest(NEW.correct_indexes) x WHERE x < 0 OR x >= array_length(v_options,1)) THEN
      RAISE EXCEPTION 'QUESTION_CORRECT_OPTION_INVALID';
    END IF;
  ELSIF NEW.kind = 'TEXT' THEN
    IF COALESCE(array_length(NEW.correct_text, 1), 0) = 0 THEN
      RAISE EXCEPTION 'QUESTION_CORRECT_TEXT_REQUIRED';
    END IF;
  ELSIF NEW.kind = 'CONVERSATION' THEN
    v_turns := CASE WHEN jsonb_typeof(NEW.dialogue->'turns') = 'array' THEN NEW.dialogue->'turns' ELSE '[]'::jsonb END;
    IF jsonb_array_length(v_turns) = 0 THEN
      RAISE EXCEPTION 'CONVERSATION_TURNS_REQUIRED';
    END IF;
    IF (NEW.dialogue->>'character') = 'CUSTOM'
       AND btrim(COALESCE(NEW.dialogue->>'character_image_url', '')) = '' THEN
      RAISE EXCEPTION 'CONVERSATION_CUSTOM_CHARACTER_IMAGE_REQUIRED';
    END IF;
    FOR v_turn IN SELECT value FROM jsonb_array_elements(v_turns) LOOP
      IF btrim(COALESCE(v_turn->>'say','')) = '' THEN
        RAISE EXCEPTION 'CONVERSATION_LINE_REQUIRED';
      END IF;
      IF jsonb_typeof(v_turn->'options') = 'array' AND jsonb_array_length(v_turn->'options') > 0 THEN
        IF jsonb_array_length(v_turn->'options') < 2 THEN
          RAISE EXCEPTION 'CONVERSATION_OPTIONS_REQUIRED';
        END IF;
        IF EXISTS (
          SELECT 1
          FROM (
            SELECT lower(btrim(value #>> '{}')) AS opt
            FROM jsonb_array_elements(v_turn->'options')
          ) q
          GROUP BY opt HAVING count(*) > 1
        ) THEN
          RAISE EXCEPTION 'CONVERSATION_OPTIONS_DUPLICATE';
        END IF;
        v_correct := CASE
          WHEN (v_turn->>'correct') ~ '^-?[0-9]+$' THEN (v_turn->>'correct')::integer
          ELSE NULL
        END;
        IF v_correct IS NULL OR v_correct < 0 OR v_correct >= jsonb_array_length(v_turn->'options') THEN
          RAISE EXCEPTION 'CONVERSATION_CORRECT_OPTION_INVALID';
        END IF;
      ELSIF jsonb_typeof(v_turn->'acceptedText') = 'array' AND jsonb_array_length(v_turn->'acceptedText') > 0 THEN
        IF NOT EXISTS (
          SELECT 1 FROM jsonb_array_elements_text(v_turn->'acceptedText') t WHERE btrim(t) <> ''
        ) THEN
          RAISE EXCEPTION 'CONVERSATION_ACCEPTED_TEXT_REQUIRED';
        END IF;
      END IF;
    END LOOP;
  END IF;
  RETURN NEW;
END;
$$;
;
