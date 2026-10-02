-- CRITICAL regression: the rebuild restricted renew_subscription to
-- OWNER/ADMIN only (_assert_staff). But the app's teacher UI
-- (SubscriptionScreens.kt "renew" button -> TeacherRepository.renewSubscription
-- -> RPC renew_subscription) has always let a TEACHER renew their OWN
-- approved subscription directly (see 20250904000200_group_activation_flow:
-- "Direct renewal (no approval) for teachers, admins and owners"). Restoring
-- that exact permission model: teacher may renew only their own row; staff
-- may renew any row.
CREATE OR REPLACE FUNCTION public.renew_subscription(p_subscription_id UUID)
RETURNS VOID AS $$
DECLARE
  v_role TEXT;
  v_sub RECORD;
  v_new_date DATE;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  IF v_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;
  IF v_role NOT IN ('TEACHER', 'ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = p_subscription_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SUBSCRIPTION_NOT_FOUND';
  END IF;

  IF v_sub.teacher_id <> auth.uid() AND v_role NOT IN ('ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  IF v_sub.approval_status != 'APPROVED' THEN
    RAISE EXCEPTION 'CANNOT_RENEW_PENDING';
  END IF;

  v_new_date := v_sub.next_renewal_date + INTERVAL '1 month';

  INSERT INTO public.subscription_renewals (
    subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
  ) VALUES (
    p_subscription_id, auth.uid(), v_sub.next_renewal_date, v_new_date, v_sub.monthly_amount, v_sub.currency
  );

  UPDATE public.teacher_subscriptions
  SET next_renewal_date = v_new_date, status = 'ACTIVE'
  WHERE id = p_subscription_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION public.renew_subscription(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.renew_subscription(UUID) TO authenticated;
;
