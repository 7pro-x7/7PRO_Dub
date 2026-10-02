DROP POLICY IF EXISTS "Teachers view exercise results" ON public.student_results;

CREATE POLICY "Teachers view exercise results"
  ON public.student_results FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.exercises
      WHERE exercises.id = student_results.exercise_id
        AND exercises.teacher_id = auth.uid()
    )
    AND EXISTS (
      SELECT 1 FROM public.teacher_subscriptions ts
      WHERE ts.teacher_id = auth.uid()
        AND ts.student_user_id = student_results.student_id
        AND ts.approval_status = 'APPROVED'
    )
  );;
