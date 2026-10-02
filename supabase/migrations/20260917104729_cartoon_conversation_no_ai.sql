DELETE FROM public.app_settings
 WHERE key IN ('ai_character.stt_model_url_en', 'ai_character.stt_model_url_ar');

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
    ELSE
      IF v_pick IS NOT NULL THEN m := m + 1; END IF;
    END IF;
  END LOOP;
  RETURN round(m::numeric / n, 4);
END;
$$;

DROP FUNCTION IF EXISTS public.conversation_norm(text);;
