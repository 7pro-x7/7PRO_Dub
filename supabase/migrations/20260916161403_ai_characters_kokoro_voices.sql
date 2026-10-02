ALTER TABLE public.ai_characters ALTER COLUMN voice_id SET DEFAULT 'af_heart';

UPDATE public.ai_characters SET voice_id = v.voice, voice_pitch = v.pitch
FROM (VALUES
  ('pip-fox', 'af_sky', 4.0), ('bubba-bear', 'am_puck', 1.5), ('olive-owl', 'bf_emma', 2.5),
  ('coach-maya', 'af_heart', 0.0), ('coach-adam', 'am_michael', 0.0)
) AS v(slug, voice, pitch)
WHERE ai_characters.slug = v.slug AND ai_characters.voice_id !~ '^[ab][fm]_';

UPDATE public.ai_characters SET voice_id = 'af_heart' WHERE voice_id !~ '^[ab][fm]_[a-z]+$';

ALTER TABLE public.ai_characters DROP CONSTRAINT IF EXISTS ai_characters_voice_id_check;
ALTER TABLE public.ai_characters ADD CONSTRAINT ai_characters_voice_id_check
  CHECK (voice_id ~ '^[ab][fm]_[a-z]{2,20}$');;
