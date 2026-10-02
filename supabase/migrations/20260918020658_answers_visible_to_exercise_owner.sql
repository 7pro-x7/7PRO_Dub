-- A teacher can already read the ATTEMPTS on an exercise they own (see attempts_own), but the
-- matching policy on test_answers only ever allowed the student themself and tests.manage
-- holders. So a teacher opening a student's attempt would get the score and an EMPTY list of
-- answers — no error, no denial, just silently nothing, which reads as a broken screen.
--
-- This adds the same owner clause the attempts policy already has, so the two agree.
DROP POLICY IF EXISTS answers_own ON public.test_answers;

CREATE POLICY answers_own ON public.test_answers
FOR SELECT
USING (
  EXISTS (
    SELECT 1
    FROM test_attempts a
    WHERE a.id = test_answers.attempt_id
      AND (
        a.user_id = auth.uid()
        OR has_permission('tests.manage'::text)
        OR EXISTS (
          SELECT 1 FROM placement_tests t
          WHERE t.id = a.test_id AND t.owner_id = auth.uid()
        )
      )
  )
);
;
