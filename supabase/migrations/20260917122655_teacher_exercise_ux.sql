-- 7PRO teacher exercise studio UX data fields.
-- Backward compatible: all new fields are nullable/defaulted.
ALTER TABLE public.placement_tests
  ADD COLUMN IF NOT EXISTS subject text,
  ADD COLUMN IF NOT EXISTS level text,
  ADD COLUMN IF NOT EXISTS image_url text;

ALTER TABLE public.test_questions
  ADD COLUMN IF NOT EXISTS points numeric(8,2) NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_placement_tests_subject ON public.placement_tests(subject);
CREATE INDEX IF NOT EXISTS idx_placement_tests_level ON public.placement_tests(level);

COMMENT ON COLUMN public.placement_tests.subject IS 'Teacher-defined subject/category for the exercise.';
COMMENT ON COLUMN public.placement_tests.level IS 'Teacher-defined learner level for the exercise.';
COMMENT ON COLUMN public.placement_tests.image_url IS 'Optional exercise cover image URL.';
COMMENT ON COLUMN public.test_questions.points IS 'Points awarded for the question.';
;
