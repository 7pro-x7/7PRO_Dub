-- A teacher can already read the ATTEMPTS on an exercise they own (attempts_own), but the
-- matching policy on test_answers only allowed the student themself and tests.manage holders.
-- A teacher opening a student's attempt got the score and an EMPTY answer list — no error, just
-- silently nothing, which reads as a broken screen. This adds the same owner clause.
-- APPLIED DIRECTLY to the live 7pro-x7 project on 2026-09-18 via the Supabase MCP tools.
DROP POLICY IF EXISTS answers_own ON public.test_answers;
CREATE POLICY answers_own ON public.test_answers
FOR SELECT
USING (
  EXISTS (
    SELECT 1 FROM test_attempts a
    WHERE a.id = test_answers.attempt_id
      AND (
        a.user_id = auth.uid()
        OR has_permission('tests.manage'::text)
        OR EXISTS (SELECT 1 FROM placement_tests t WHERE t.id = a.test_id AND t.owner_id = auth.uid())
      )
  )
);
