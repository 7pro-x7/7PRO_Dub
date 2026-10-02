-- subscription_stats(p_teacher) is SECURITY DEFINER with no auth check on
-- p_teacher: any authenticated user (including a student) could pass another
-- teacher's id and read their subscription/financial stats. Guard it the same
-- way teacher_balance() already is: only the teacher themselves, or staff.
CREATE OR REPLACE FUNCTION public.subscription_stats(p_teacher UUID DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
BEGIN
  IF p_teacher IS NOT NULL AND p_teacher <> auth.uid() THEN
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role IN ('OWNER', 'ADMIN')) THEN
      RAISE EXCEPTION 'FORBIDDEN';
    END IF;
  END IF;

  PERFORM public.refresh_subscription_statuses();

  SELECT jsonb_build_object(
    'total_subscriptions', count(*),
    'due_this_week', count(*) FILTER (WHERE status IN ('ACTIVE', 'DUE') AND next_renewal_date <= current_date + INTERVAL '7 days'),
    'overdue', count(*) FILTER (WHERE status = 'OVERDUE'),
    'pending_approval', count(*) FILTER (WHERE approval_status = 'PENDING'),
    'total_monthly_value', COALESCE(sum(monthly_amount) FILTER (WHERE status != 'PAUSED'), 0)
  ) INTO result
  FROM public.teacher_subscriptions
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND approval_status = 'APPROVED';

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- subscription_stats(NULL) (the "all teachers" aggregate) must stay
-- staff-only too -- a plain teacher calling it with no argument would
-- otherwise get every teacher's combined totals.
CREATE OR REPLACE FUNCTION public.subscription_stats_all()
RETURNS JSONB AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role IN ('OWNER', 'ADMIN')) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  RETURN public.subscription_stats(NULL);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
;
