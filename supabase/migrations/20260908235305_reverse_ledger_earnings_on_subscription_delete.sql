-- Fix: profit/balance kept counting students after they were deleted.
--
-- ROOT CAUSE:
-- _record_subscription_earning() inserts TWO rows when a subscription is
-- approved or renewed:
--   1. public.subscription_earnings  -- ON DELETE CASCADE on subscription_id,
--      so it correctly disappears when the subscription/student is deleted.
--   2. public.teacher_ledger         -- NO link at all to the subscription
--      (only a free-text `note`), so it is orphaned the moment the
--      subscription is deleted. teacher_balance() (the teacher's visible
--      profit/balance and payout eligibility) sums this table directly, so
--      money for a deleted student stayed in the balance forever even
--      though subscription_stats() and the student list correctly stopped
--      showing that student as present.
--
-- FIX:
--   1. Add teacher_ledger.subscription_id so earning rows can be traced
--      back to the subscription that generated them.
--   2. _record_subscription_earning() now stamps that column on every new
--      earning row going forward.
--   3. A BEFORE DELETE trigger on teacher_subscriptions reverses (status =
--      'REVERSED', same mechanism review_payout() already uses on its
--      reject path) any of that subscription's ledger earnings that are
--      still PENDING or AVAILABLE, i.e. not yet actually paid out. Money
--      already paid to a teacher (PAYOUT row status = PAID) is a completed
--      transfer and is deliberately left untouched.
--
-- SCOPE NOTE: ledger rows created before this migration have no
-- subscription_id and cannot be safely back-linked from free-text notes,
-- so this applies to subscriptions deleted from now on, not retroactively.

ALTER TABLE public.teacher_ledger
  ADD COLUMN IF NOT EXISTS subscription_id UUID REFERENCES public.teacher_subscriptions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_teacher_ledger_subscription
  ON public.teacher_ledger(subscription_id)
  WHERE subscription_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public._record_subscription_earning(
  p_subscription_id uuid, p_renewal_id uuid, p_period_start date, p_period_end date, p_note text
)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
DECLARE
  v_sub RECORD; v_rate NUMERIC; v_teacher_share NUMERIC; v_owner_share NUMERIC;
  v_hold_days NUMERIC; v_available_at TIMESTAMPTZ; v_status ledger_status;
BEGIN
  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = p_subscription_id;
  IF NOT FOUND THEN RETURN; END IF;

  v_rate := public.get_teacher_subscription_rate(v_sub.teacher_id);
  v_teacher_share := round(v_sub.monthly_amount * v_rate / 100, 2);
  v_owner_share := v_sub.monthly_amount - v_teacher_share;

  INSERT INTO public.subscription_earnings (
    teacher_id, subscription_id, renewal_id, group_name, monthly_amount, currency,
    teacher_percentage, teacher_earning, owner_earning, period_start, period_end
  ) VALUES (
    v_sub.teacher_id, p_subscription_id, p_renewal_id, v_sub.group_name, v_sub.monthly_amount, v_sub.currency,
    v_rate, v_teacher_share, v_owner_share, p_period_start, p_period_end
  )
  ON CONFLICT (subscription_id, period_start, period_end) DO NOTHING;

  IF NOT FOUND THEN RETURN; END IF;

  v_hold_days := public.setting_num('subscription.hold_days', 0);
  v_available_at := now() + (v_hold_days || ' days')::INTERVAL;
  v_status := CASE WHEN v_hold_days > 0 THEN 'PENDING'::ledger_status ELSE 'AVAILABLE'::ledger_status END;

  INSERT INTO public.teacher_ledger (teacher_id, subscription_id, kind, amount, currency, status, available_at, note)
  VALUES (v_sub.teacher_id, p_subscription_id, 'EARNING', v_teacher_share, v_sub.currency, v_status, v_available_at, p_note);
END;
$function$;

CREATE OR REPLACE FUNCTION public._reverse_ledger_earnings_on_subscription_delete()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
BEGIN
  UPDATE public.teacher_ledger
  SET status = 'REVERSED'
  WHERE subscription_id = OLD.id
    AND kind = 'EARNING'
    AND status IN ('PENDING', 'AVAILABLE');
  RETURN OLD;
END;
$function$;

DROP TRIGGER IF EXISTS trg_reverse_ledger_earnings_on_subscription_delete ON public.teacher_subscriptions;
CREATE TRIGGER trg_reverse_ledger_earnings_on_subscription_delete
  BEFORE DELETE ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public._reverse_ledger_earnings_on_subscription_delete();
;
