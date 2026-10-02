-- ============================================================================
-- Subscription system end-to-end fixes
--
-- What changed and why:
--
-- 1. LEDGER CREDITS THE TEACHER'S SHARE ONLY.
--    record_subscription_ledger_entry credited the FULL renewal amount to the
--    teacher's withdrawable balance. With the platform taking a commission the
--    teacher could withdraw the platform's share. The credit is now the
--    teacher's percentage (default 70%) of the renewal amount — exactly the
--    split recorded in subscription_earnings. owner_earning stays in
--    subscription_earnings as the platform's net profit and is never
--    withdrawable.
--
-- 2. INITIAL EARNINGS ALSO CREDIT THE LEDGER.
--    record_initial_subscription_earning fires when a subscription first
--    becomes APPROVED (staff INSERT as APPROVED, or PENDING/REJECTED ->
--    APPROVED). It recorded subscription_earnings but never credited the
--    teacher's ledger, so the first month's fee was invisible in the balance
--    until the first renewal. It now credits the teacher's share too — linking
--    approvals/activation -> earnings -> withdrawals automatically.
--    A PAUSED subscription is excluded from both (never counts).
--
-- 3. set_teacher_subscription_rate ALLOWS ADMIN.
--    The old RPC restricted rate changes to OWNER. Owner AND Admin may now set
--    a teacher's subscription share; the app writes through the same secured
--    function.
-- ============================================================================

-- ============================================================================
-- 1. record_subscription_ledger_entry — teacher's share only
-- ============================================================================
CREATE OR REPLACE FUNCTION public.record_subscription_ledger_entry()
RETURNS TRIGGER AS $$
DECLARE
  v_sub RECORD;
  v_rate NUMERIC(5,2);
BEGIN
  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = NEW.subscription_id;
  IF NOT FOUND THEN RETURN NEW; END IF;

  SELECT COALESCE(percentage, 70) INTO v_rate
  FROM public.teacher_subscription_rates
  WHERE teacher_id = v_sub.teacher_id;
  v_rate := COALESCE(v_rate, 70);

  -- Credit the teacher's share of the renewal, not the full amount.
  INSERT INTO public.teacher_ledger (teacher_id, kind, amount, currency, status, note)
  VALUES (
    v_sub.teacher_id,
    'CREDIT',
    round(NEW.amount * v_rate / 100, 2),
    NEW.currency,
    'AVAILABLE',
    'Subscription renewal: ' || v_sub.group_name || ' - ' || v_sub.student_name
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.record_subscription_ledger_entry() TO authenticated;
-- ============================================================================
-- 2. record_initial_subscription_earning — also credit the ledger
-- ============================================================================
CREATE OR REPLACE FUNCTION public.record_initial_subscription_earning()
RETURNS TRIGGER AS $$
DECLARE
  v_rate NUMERIC(5,2);
  v_teacher_earning NUMERIC(12,2);
  v_owner_earning NUMERIC(12,2);
  v_period_start DATE;
  v_period_end DATE;
  v_old_approval TEXT;
BEGIN
  -- Paused subscriptions never earn or credit, whatever their approval status.
  IF NEW.status = 'PAUSED' THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    v_old_approval := OLD.approval_status;
  ELSE
    v_old_approval := NULL;
  END IF;

  -- Record only when the row first becomes APPROVED (staff INSERT, or a
  -- transition INTO APPROVED from PENDING/REJECTED). Re-fires are no-ops.
  IF NEW.approval_status = 'APPROVED' THEN
    IF TG_OP = 'INSERT' THEN
      NULL; -- record below
    ELSIF TG_OP = 'UPDATE' AND v_old_approval IS DISTINCT FROM 'APPROVED' THEN
      NULL; -- record below
    ELSE
      RETURN NEW;
    END IF;
  ELSE
    RETURN NEW;
  END IF;

  SELECT COALESCE(percentage, 70) INTO v_rate
  FROM public.teacher_subscription_rates
  WHERE teacher_id = NEW.teacher_id;
  v_rate := COALESCE(v_rate, 70);

  v_teacher_earning := round(NEW.monthly_amount * v_rate / 100, 2);
  v_owner_earning := NEW.monthly_amount - v_teacher_earning;
  v_period_start := date_trunc('month', current_date)::date;
  v_period_end := (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date;

  INSERT INTO public.subscription_earnings (
    teacher_id, subscription_id, renewal_id,
    group_name, monthly_amount, currency,
    teacher_percentage, teacher_earning, owner_earning,
    period_start, period_end
  ) VALUES (
    NEW.teacher_id, NEW.id, NULL,
    NEW.group_name, NEW.monthly_amount, NEW.currency,
    v_rate, v_teacher_earning, v_owner_earning,
    v_period_start, v_period_end
  );

  -- The first month's fee is collected at signup: credit the teacher's share
  -- to their withdrawable balance so activation -> earnings -> withdrawals are
  -- linked end to end.
  INSERT INTO public.teacher_ledger (teacher_id, kind, amount, currency, status, note)
  VALUES (
    NEW.teacher_id,
    'CREDIT',
    v_teacher_earning,
    NEW.currency,
    'AVAILABLE',
    'Subscription: ' || NEW.group_name || ' - ' || NEW.student_name
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.record_initial_subscription_earning() TO authenticated;
-- ============================================================================
-- 3. set_teacher_subscription_rate — OWNER or ADMIN
-- ============================================================================
CREATE OR REPLACE FUNCTION public.set_teacher_subscription_rate(
  p_teacher UUID,
  p_percentage NUMERIC
)
RETURNS VOID AS $$
DECLARE
  v_caller_role TEXT;
BEGIN
  SELECT role::text INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  IF v_caller_role NOT IN ('OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'Only owner or admin can set subscription rates';
  END IF;
  IF p_percentage < 0 OR p_percentage > 100 THEN
    RAISE EXCEPTION 'Percentage must be between 0 and 100';
  END IF;
  INSERT INTO public.teacher_subscription_rates (teacher_id, percentage, set_by)
  VALUES (p_teacher, p_percentage, auth.uid())
  ON CONFLICT (teacher_id) DO UPDATE
    SET percentage = EXCLUDED.percentage,
        set_by = EXCLUDED.set_by,
        updated_at = now();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.set_teacher_subscription_rate(UUID, NUMERIC) TO authenticated;
-- Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';
