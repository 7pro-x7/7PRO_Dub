-- ============================================================================
-- Fix duplicate subscription earnings on group activation
--
-- ROOT CAUSE: Two triggers fire on UPDATE OF approval_status:
--   1. trg_record_initial_earning → record_initial_subscription_earning()
--   2. trg_approve_initial_earning → record_approved_subscription_earning()
--
-- Both insert into subscription_earnings, causing a duplicate/NULL error.
-- Additionally, record_initial_subscription_earning used SELECT INTO without
-- a COALESCE fallback, leaving v_rate NULL when no teacher_subscription_rates
-- row exists, which cascaded NULL into teacher_earning/owner_earning.
--
-- FIX:
--   1. Drop trg_approve_initial_earning and record_approved_subscription_earning
--      (trg_record_initial_earning already handles all approval_status changes)
--   2. Fix record_initial_subscription_earning to always COALESCE v_rate to 70
-- ============================================================================

-- 1. Drop the redundant trigger and function
DROP TRIGGER IF EXISTS trg_approve_initial_earning ON public.teacher_subscriptions;
DROP FUNCTION IF EXISTS public.record_approved_subscription_earning();
-- 2. Fix the initial earning trigger to always have a non-NULL rate
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
  IF TG_OP = 'UPDATE' THEN
    v_old_approval := OLD.approval_status;
  ELSE
    v_old_approval := NULL;
  END IF;

  IF NEW.approval_status = 'APPROVED' THEN
    IF TG_OP = 'INSERT' THEN
      -- New subscription created as APPROVED — record earning
    ELSIF TG_OP = 'UPDATE' AND v_old_approval IS DISTINCT FROM 'APPROVED' THEN
      -- Transitioning from PENDING/REJECTED to APPROVED — record earning
    ELSE
      -- Already was APPROVED, just a field update — skip
      RETURN NEW;
    END IF;
  ELSE
    -- PENDING or REJECTED — no earning
    RETURN NEW;
  END IF;

  -- Get teacher's subscription rate (default 70% to teacher)
  SELECT COALESCE(percentage, 70) INTO v_rate
  FROM public.teacher_subscription_rates
  WHERE teacher_id = NEW.teacher_id;

  -- Ensure v_rate is never NULL (SELECT INTO leaves variable unchanged if no row)
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

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public;
-- Recreate trigger to also fire on UPDATE of approval_status
DROP TRIGGER IF EXISTS trg_record_initial_earning ON public.teacher_subscriptions;
CREATE TRIGGER trg_record_initial_earning
  AFTER INSERT OR UPDATE OF approval_status ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public.record_initial_subscription_earning();
GRANT EXECUTE ON FUNCTION public.record_initial_subscription_earning() TO authenticated;
-- Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';
