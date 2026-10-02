
-- Create the missing roster (teacher_students) rows for approved subscriptions
-- that had none, so these students actually show up for their teacher/admin
-- and stay in sync going forward.
INSERT INTO public.teacher_students
  (teacher_id, student_user_id, full_name, email, phone, source,
   subscription_start, subscription_end, next_payment_date, amount, currency, status)
SELECT
  b.teacher_id, b.student_user_id, COALESCE(b.student_name, 'Student'),
  p.email, p.phone, 'MANUAL',
  b.start_date, b.next_renewal_date, b.next_renewal_date, b.monthly_amount, b.currency,
  (CASE b.status
     WHEN 'ACTIVE' THEN 'ACTIVE'
     WHEN 'DUE' THEN 'DUE_SOON'
     WHEN 'OVERDUE' THEN 'OVERDUE'
     WHEN 'PAUSED' THEN 'SUSPENDED'
     ELSE 'ACTIVE'
   END)::public.student_record_status
FROM public.teacher_subscriptions b
LEFT JOIN public.profiles p ON p.id = b.student_user_id
WHERE b.student_user_id IS NOT NULL
  AND b.approval_status = 'APPROVED'
  AND NOT EXISTS (
    SELECT 1 FROM public.teacher_students a
    WHERE a.teacher_id = b.teacher_id AND a.student_user_id = b.student_user_id
  );

-- Prevent this drift from happening again: one roster row per teacher+student.
ALTER TABLE public.teacher_students
  ADD CONSTRAINT teacher_students_teacher_student_unique UNIQUE (teacher_id, student_user_id);

-- Extend the sync trigger so a brand-new approved subscription also creates
-- its roster row automatically, not just updates an existing one.
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

  INSERT INTO public.teacher_students
    (teacher_id, student_user_id, full_name, source,
     subscription_start, subscription_end, next_payment_date, amount, currency, status)
  SELECT NEW.teacher_id, NEW.student_user_id, COALESCE(NEW.student_name, 'Student'), 'MANUAL',
         NEW.start_date, NEW.next_renewal_date, NEW.next_renewal_date, NEW.monthly_amount, NEW.currency,
         v_mapped_status
  ON CONFLICT (teacher_id, student_user_id) DO UPDATE SET
    subscription_end = EXCLUDED.subscription_end,
    subscription_start = COALESCE(EXCLUDED.subscription_start, public.teacher_students.subscription_start),
    status = EXCLUDED.status,
    amount = COALESCE(EXCLUDED.amount, public.teacher_students.amount),
    currency = COALESCE(EXCLUDED.currency, public.teacher_students.currency),
    updated_at = now()
  WHERE public.teacher_students.subscription_end IS DISTINCT FROM EXCLUDED.subscription_end
     OR public.teacher_students.subscription_start IS DISTINCT FROM EXCLUDED.subscription_start
     OR public.teacher_students.status IS DISTINCT FROM EXCLUDED.status;

  RETURN NEW;
END;
$$;
;
