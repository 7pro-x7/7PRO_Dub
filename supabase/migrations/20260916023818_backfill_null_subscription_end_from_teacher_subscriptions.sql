
-- Catch the remaining edge case: a teacher_students row whose subscription_end
-- was never set at all (NULL), even though it was touched more recently for
-- an unrelated field. NULL means "no date recorded here", so the other
-- table's real date wins regardless of which row's timestamp is newer.
UPDATE public.teacher_students ts
SET subscription_end = sub.next_renewal_date,
    subscription_start = COALESCE(ts.subscription_start, sub.start_date),
    updated_at = now()
FROM public.teacher_subscriptions sub
WHERE ts.teacher_id = sub.teacher_id
  AND ts.student_user_id = sub.student_user_id
  AND sub.approval_status = 'APPROVED'
  AND ts.subscription_end IS NULL
  AND sub.next_renewal_date IS NOT NULL;
;
