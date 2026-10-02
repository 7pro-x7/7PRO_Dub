-- ============================================================================
-- SECURITY FIX: a teacher could self-approve a group or subscription,
-- skipping the owner/admin approval queue entirely.
--
-- ROOT CAUSE:
--   activate_group() / activate_subscription() (the RPCs the app's "activate"
--   buttons call) already do the right thing: a TEACHER caller is set to
--   PENDING + an approval_requests row is created; only an OWNER/ADMIN caller
--   is set to APPROVED.
--
--   But the table-level RLS policies do not enforce that same rule:
--     - teacher_groups_self_access (20250904000200_group_activation_flow)
--       is FOR ALL USING (auth.uid() = teacher_id) with no column
--       restriction, so a teacher can PATCH their own group row's
--       approval_status straight to 'APPROVED' directly (bypassing
--       activate_group entirely).
--     - teachers_update_own_subscriptions (restored in
--       20260906163427_restore_teacher_update_delete_subscription_policies)
--       has the same gap on teacher_subscriptions.
--
--   Any authenticated request that writes approval_status='APPROVED' on a
--   row it owns -- not just the app's own "activate" button -- succeeds today.
--
-- FIX: BEFORE UPDATE triggers that block *only* the specific transition
--   "approval_status becomes APPROVED" for a caller who is not OWNER/ADMIN.
--   This is intentionally narrower than the RLS policies it sits behind, so:
--     - Teachers keep unconditional direct edit access to every other field
--       (name, level, dates, amounts, notes, resubmitting after REJECTED)
--       exactly as the 20260906163427 restore intended.
--     - activate_group / activate_subscription / process_approval_request
--       keep working unchanged: they only ever set APPROVED when the real
--       caller (auth.uid(), unaffected by SECURITY DEFINER) is OWNER/ADMIN.
--     - The staff_full_access_subscriptions / teacher_groups_staff_full_access
--       policies keep working unchanged for the same reason.
-- ============================================================================

CREATE OR REPLACE FUNCTION public._block_teacher_self_approval()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.approval_status = 'APPROVED' AND OLD.approval_status IS DISTINCT FROM 'APPROVED' THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role IN ('OWNER', 'ADMIN')
    ) THEN
      RAISE EXCEPTION 'FORBIDDEN: only owner or admin can approve this';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_block_teacher_self_approval ON public.teacher_subscriptions;
CREATE TRIGGER trg_block_teacher_self_approval
  BEFORE UPDATE ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public._block_teacher_self_approval();

DROP TRIGGER IF EXISTS trg_block_teacher_self_approval ON public.teacher_groups;
CREATE TRIGGER trg_block_teacher_self_approval
  BEFORE UPDATE ON public.teacher_groups
  FOR EACH ROW
  EXECUTE FUNCTION public._block_teacher_self_approval();

REVOKE ALL ON FUNCTION public._block_teacher_self_approval() FROM PUBLIC, anon, authenticated;

NOTIFY pgrst, 'reload schema';
;
