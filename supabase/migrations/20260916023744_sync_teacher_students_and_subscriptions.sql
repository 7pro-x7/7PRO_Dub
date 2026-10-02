
-- Keep teacher_students (roster, subscription_end) and teacher_subscriptions
-- (billing/renewal, next_renewal_date) in sync whichever one is edited.
-- Root cause of "editing a student's subscription date doesn't update the home banner":
-- the banner reads teacher_subscriptions via my_subscription_state(), but admin/teacher
-- edit screens can write directly to teacher_students, and vice versa, with nothing
-- keeping the two in sync.

CREATE OR REPLACE FUNCTION public._sync_teacher_student_from_subscription()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_mapped_status public.student_record_status;
BEGIN
  IF NEW.student_user_id IS NULL OR NEW.approval_status <> 'APPROVED' THEN
    RETURN NEW;
  END IF;

  v_mapped_status := CASE NEW.status
    WHEN 'ACTIVE' THEN 'ACTIVE'
    WHEN 'DUE' THEN 'DUE_SOON'
    WHEN 'OVERDUE' THEN 'OVERDUE'
    WHEN 'PAUSED' THEN 'SUSPENDED'
    ELSE 'ACTIVE'
  END::public.student_record_status;

  UPDATE public.teacher_students
  SET subscription_end = NEW.next_renewal_date,
      subscription_start = COALESCE(NEW.start_date, subscription_start),
      status = v_mapped_status,
      amount = COALESCE(NEW.monthly_amount, amount),
      currency = COALESCE(NEW.currency, currency),
      updated_at = now()
  WHERE teacher_id = NEW.teacher_id
    AND student_user_id = NEW.student_user_id
    AND (subscription_end IS DISTINCT FROM NEW.next_renewal_date
         OR subscription_start IS DISTINCT FROM NEW.start_date
         OR status IS DISTINCT FROM v_mapped_status);

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_teacher_student_from_subscription ON public.teacher_subscriptions;
CREATE TRIGGER trg_sync_teacher_student_from_subscription
AFTER INSERT OR UPDATE OF next_renewal_date, start_date, status, approval_status, monthly_amount, currency
ON public.teacher_subscriptions
FOR EACH ROW EXECUTE FUNCTION public._sync_teacher_student_from_subscription();

CREATE OR REPLACE FUNCTION public._sync_subscription_from_teacher_student()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_mapped_status text;
BEGIN
  IF NEW.student_user_id IS NULL OR NEW.subscription_end IS NULL THEN
    RETURN NEW;
  END IF;

  v_mapped_status := CASE NEW.status
    WHEN 'ACTIVE' THEN 'ACTIVE'
    WHEN 'DUE_SOON' THEN 'DUE'
    WHEN 'OVERDUE' THEN 'OVERDUE'
    WHEN 'SUSPENDED' THEN 'PAUSED'
    WHEN 'EXPIRED' THEN 'OVERDUE'
    ELSE NULL
  END;

  UPDATE public.teacher_subscriptions
  SET next_renewal_date = NEW.subscription_end,
      start_date = COALESCE(NEW.subscription_start, start_date),
      status = COALESCE(v_mapped_status, status),
      updated_at = now()
  WHERE teacher_id = NEW.teacher_id
    AND student_user_id = NEW.student_user_id
    AND approval_status = 'APPROVED'
    AND (next_renewal_date IS DISTINCT FROM NEW.subscription_end
         OR start_date IS DISTINCT FROM NEW.subscription_start
         OR (v_mapped_status IS NOT NULL AND status IS DISTINCT FROM v_mapped_status));

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_subscription_from_teacher_student ON public.teacher_students;
CREATE TRIGGER trg_sync_subscription_from_teacher_student
AFTER UPDATE OF subscription_end, subscription_start, status
ON public.teacher_students
FOR EACH ROW EXECUTE FUNCTION public._sync_subscription_from_teacher_student();
;
