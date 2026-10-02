-- test_questions was missing created_at, which ExerciseRepository.addQuestion() (Android)
-- relies on to look up the just-inserted row for auto-translation:
--   order("created_at", Order.DESCENDING).limit(1)
-- Without this column, that lookup query always failed with
-- "column test_questions.created_at does not exist", which surfaced as
-- "add question" silently not completing for teachers.
ALTER TABLE public.test_questions
  ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_test_questions_test_id_created_at
  ON public.test_questions(test_id, created_at DESC);

COMMENT ON COLUMN public.test_questions.created_at IS
  'When the question was inserted. Used by the teacher app to find the most recently added question for a test (e.g. to trigger auto-translation).';
;
