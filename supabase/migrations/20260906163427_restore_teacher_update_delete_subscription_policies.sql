-- The clean rebuild (20260906163132) intentionally rewrote INSERT/SELECT
-- policies but accidentally dropped teachers_update_own_subscriptions and
-- teachers_delete_own_subscriptions without recreating them. The Android app
-- (TeacherRepository.saveSubscription update-path, deleteSubscription,
-- deleteGroup) relies on teachers being able to UPDATE/DELETE their own rows
-- directly (unconditionally, same as the pre-rebuild system) — restoring
-- exact parity here.
DROP POLICY IF EXISTS "teachers_update_own_subscriptions" ON public.teacher_subscriptions;
CREATE POLICY "teachers_update_own_subscriptions"
  ON public.teacher_subscriptions FOR UPDATE
  USING (auth.uid() = teacher_id);

DROP POLICY IF EXISTS "teachers_delete_own_subscriptions" ON public.teacher_subscriptions;
CREATE POLICY "teachers_delete_own_subscriptions"
  ON public.teacher_subscriptions FOR DELETE
  USING (auth.uid() = teacher_id);
;
