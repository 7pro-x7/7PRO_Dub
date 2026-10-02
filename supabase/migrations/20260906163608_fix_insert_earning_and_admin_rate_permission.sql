-- Two functional-parity fixes found while auditing the clean rebuild against
-- the actual last-live behavior (20250904000900_subscription_system_e2e_fix):
--
-- 1. _on_subscription_approved was AFTER UPDATE only, so a staff member
--    creating a subscription directly as APPROVED (AdminRepository.
--    adminSaveSubscription, an INSERT) never recorded an earning/ledger
--    credit. Restored to fire on INSERT OR UPDATE, and to skip PAUSED rows
--    (matching the exact previous condition).
-- 2. set_teacher_subscription_rate was OWNER-only in the very first version
--    of this table, but was widened to OWNER-or-ADMIN in the final pre-
--    rebuild migration. The rebuild accidentally reverted to OWNER-only.
--    Restored to OWNER or ADMIN.

DROP TRIGGER IF EXISTS trg_on_subscription_approved ON public.teacher_subscriptions;

CREATE OR REPLACE FUNCTION public._on_subscription_approved()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.status = 'PAUSED' THEN
    RETURN NEW;
  END IF;

  IF NEW.approval_status = 'APPROVED' THEN
    IF TG_OP = 'INSERT' OR OLD.approval_status IS DISTINCT FROM 'APPROVED' THEN
      PERFORM public._record_subscription_earning(
        NEW.id, NULL, NEW.start_date, NEW.next_renewal_date,
        'Subscription approved: ' || NEW.group_name || ' - ' || NEW.student_name
      );
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE TRIGGER trg_on_subscription_approved
  AFTER INSERT OR UPDATE ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public._on_subscription_approved();

CREATE OR REPLACE FUNCTION public.set_teacher_subscription_rate(p_teacher UUID, p_percentage NUMERIC)
RETURNS VOID AS $$
DECLARE
  v_role TEXT;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  IF v_role NOT IN ('OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'Only owner or admin can set a teacher''s subscription rate';
  END IF;
  IF p_percentage < 0 OR p_percentage > 100 THEN
    RAISE EXCEPTION 'percentage must be between 0 and 100';
  END IF;

  INSERT INTO public.teacher_subscription_rates (teacher_id, percentage, set_by)
  VALUES (p_teacher, p_percentage, auth.uid())
  ON CONFLICT (teacher_id) DO UPDATE
    SET percentage = EXCLUDED.percentage, set_by = EXCLUDED.set_by, updated_at = now();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
;
