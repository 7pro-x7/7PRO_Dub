
-- Backfill: for students that exist in both tables, trust whichever row was
-- edited more recently, so existing mismatches (caused by the missing sync)
-- get fixed immediately instead of waiting for the next edit.
UPDATE public.teacher_students ts
SET subscription_end = sub.next_renewal_date,
    subscription_start = COALESCE(sub.start_date, ts.subscription_start),
    status = (CASE sub.status
      WHEN 'ACTIVE' THEN 'ACTIVE'
      WHEN 'DUE' THEN 'DUE_SOON'
      WHEN 'OVERDUE' THEN 'OVERDUE'
      WHEN 'PAUSED' THEN 'SUSPENDED'
      ELSE ts.status
    END)::public.student_record_status,
    updated_at = now()
FROM public.teacher_subscriptions sub
WHERE ts.teacher_id = sub.teacher_id
  AND ts.student_user_id = sub.student_user_id
  AND sub.approval_status = 'APPROVED'
  AND sub.updated_at > ts.updated_at
  AND (ts.subscription_end IS DISTINCT FROM sub.next_renewal_date
       OR ts.subscription_start IS DISTINCT FROM sub.start_date);
;
