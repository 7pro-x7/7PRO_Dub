-- Production validation for scripted cartoon conversations and placement questions.
-- Client validation is mirrored here so malformed data cannot be saved by another client.
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
  v_seen text[] := '{}';
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
      END IF;
    END LOOP;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_test_question_payload ON public.test_questions;
CREATE TRIGGER trg_validate_test_question_payload
BEFORE INSERT OR UPDATE ON public.test_questions
FOR EACH ROW EXECUTE FUNCTION public.validate_test_question_payload();

CREATE UNIQUE INDEX IF NOT EXISTS test_answers_attempt_question_unique
  ON public.test_answers(attempt_id, question_id);
;
