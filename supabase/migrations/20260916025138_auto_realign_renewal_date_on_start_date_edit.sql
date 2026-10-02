
-- When the start date is edited (and the renewal date isn't touched in the
-- same edit), auto-recalculate the renewal date as start_date + 1 billing
-- cycle (monthly by default), the same way it's already done on first
-- creation. This makes "تاريخ الاشتراك" edits behave like a real monthly
-- subscription instead of leaving a stale renewal date behind.

CREATE OR REPLACE FUNCTION public._realign_subscription_renewal_on_start_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  NEW.next_renewal_date := NEW.start_date + CASE
    WHEN NEW.billing_cycle = 'WEEKLY' THEN INTERVAL '1 week'
    ELSE INTERVAL '1 month'
  END;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_realign_subscription_renewal_on_start_change ON public.teacher_subscriptions;
CREATE TRIGGER trg_realign_subscription_renewal_on_start_change
BEFORE UPDATE OF start_date ON public.teacher_subscriptions
FOR EACH ROW
WHEN (NEW.start_date IS DISTINCT FROM OLD.start_date
      AND NEW.next_renewal_date IS NOT DISTINCT FROM OLD.next_renewal_date)
EXECUTE FUNCTION public._realign_subscription_renewal_on_start_change();

-- Same behaviour on the roster table (teacher_students), monthly by default
-- since it has no billing_cycle column of its own.
CREATE OR REPLACE FUNCTION public._realign_teacher_student_end_on_start_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  NEW.subscription_end := NEW.subscription_start + INTERVAL '1 month';
  NEW.next_payment_date := NEW.subscription_end::date;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_realign_teacher_student_end_on_start_change ON public.teacher_students;
CREATE TRIGGER trg_realign_teacher_student_end_on_start_change
BEFORE UPDATE OF subscription_start ON public.teacher_students
FOR EACH ROW
WHEN (NEW.subscription_start IS DISTINCT FROM OLD.subscription_start
      AND NEW.subscription_end IS NOT DISTINCT FROM OLD.subscription_end)
EXECUTE FUNCTION public._realign_teacher_student_end_on_start_change();

-- Fix AhmedSadat's row right now: 17 Aug 2026 + 1 month = 17 Sep 2026.
UPDATE public.teacher_subscriptions
SET next_renewal_date = '2026-08-17'::date + INTERVAL '1 month'
WHERE id = '153778b7-6505-4a48-8b4c-6c3ad2505fd6';
;
