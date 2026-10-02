-- Feature: replace manually-typed "next renewal date" with a MONTHLY/WEEKLY
-- billing cycle choice. The renewal date is now always computed by the
-- server (never trusted from the client), and a subscription can only be
-- renewed on or after its actual due date -- no more renewing early.
--
-- 1. New column: billing_cycle. Defaults to MONTHLY so every existing row
--    keeps behaving exactly as it does today (renew_subscription already
--    added +1 month unconditionally).
-- 2. BEFORE INSERT trigger: computes next_renewal_date = start_date + cycle,
--    unconditionally overriding whatever the client sent (or didn't send --
--    the column is NOT NULL, but Postgres checks NOT NULL only after BEFORE
--    ROW triggers run, so leaving it out of the insert entirely is fine).
-- 3. renew_subscription(): new_date now advances by the subscription's own
--    cycle instead of a hardcoded month, and refuses to run before
--    next_renewal_date (RAISE EXCEPTION 'NOT_DUE_YET:<date>').

ALTER TABLE public.teacher_subscriptions
  ADD COLUMN IF NOT EXISTS billing_cycle TEXT NOT NULL DEFAULT 'MONTHLY'
    CHECK (billing_cycle IN ('MONTHLY', 'WEEKLY'));

CREATE OR REPLACE FUNCTION public._set_subscription_next_renewal_date()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
BEGIN
  NEW.next_renewal_date := NEW.start_date + CASE
    WHEN NEW.billing_cycle = 'WEEKLY' THEN INTERVAL '1 week'
    ELSE INTERVAL '1 month'
  END;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_set_subscription_next_renewal_date ON public.teacher_subscriptions;
CREATE TRIGGER trg_set_subscription_next_renewal_date
  BEFORE INSERT ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public._set_subscription_next_renewal_date();

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

  IF current_date < v_sub.next_renewal_date THEN
    RAISE EXCEPTION 'NOT_DUE_YET:%', v_sub.next_renewal_date;
  END IF;

  v_new_date := v_sub.next_renewal_date + CASE
    WHEN v_sub.billing_cycle = 'WEEKLY' THEN INTERVAL '1 week'
    ELSE INTERVAL '1 month'
  END;

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
