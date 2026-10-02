-- Per request: drop the "no duplicate options" validation from
-- validate_test_question_payload(). Everything else (min 2 options required,
-- correct_indexes required/in range, TEXT/CONVERSATION checks) stays as-is.

CREATE OR REPLACE FUNCTION public.validate_test_question_payload()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_options text[];
  v_correct integer;
  v_turn jsonb;
  v_turns jsonb;
BEGIN
  IF NEW.kind IN ('SINGLE','MULTI','TRUE_FALSE') THEN
    v_options := ARRAY(SELECT jsonb_array_elements_text(COALESCE(NEW.options, '[]'::jsonb)));
    IF COALESCE(array_length(v_options, 1), 0) < 2 THEN
      RAISE EXCEPTION 'QUESTION_OPTIONS_REQUIRED';
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
$function$;;
