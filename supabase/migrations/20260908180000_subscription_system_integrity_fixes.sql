-- Subscription system — integrity fixes found by auditing the LIVE database.
-- (Applied to project 7pro-x7 and verified.)
--
-- BUG 1: subscription_earnings had NO uniqueness guarantee, only a PK on the
--   surrogate id. The migration history shows repeated application-level
--   patches for double counting, each fixing one code path while the table
--   itself still accepted the same period twice. A unique index makes paying
--   a teacher twice for one month structurally impossible.
--
-- BUG 2: _record_subscription_earning wrote ledger rows as
--   kind='CREDIT', status='AVAILABLE', available_at=NULL, while
--   release_matured_earnings() only looks at
--   kind='EARNING' AND status='PENDING' AND available_at<=now().
--   The two never matched, so the pending->matured->available hold mechanism
--   was dead code for subscription income: every earning was instantly
--   withdrawable. Live data confirmed it (17 CREDIT/AVAILABLE rows, 5,180
--   total, available_at NULL on all of them).
--   Fixed to use the same mechanism as course earnings, with the hold length
--   read from settings key 'subscription.hold_days', DEFAULT 0 so current
--   behaviour is unchanged and no teacher's visible balance moves. Turn on a
--   real hold with:  select upsert_setting('subscription.hold_days','14');
--   Existing rows are deliberately not rewritten — that is a business
--   decision about money teachers can already see, not a bug fix.

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_earnings_period
  ON public.subscription_earnings (subscription_id, period_start, period_end);

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

  -- ON CONFLICT makes an approve/renew retry idempotent instead of an error,
  -- and critically skips the ledger insert below when no new earning was made.
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

  INSERT INTO public.teacher_ledger (teacher_id, kind, amount, currency, status, available_at, note)
  VALUES (v_sub.teacher_id, 'EARNING', v_teacher_share, v_sub.currency, v_status, v_available_at, p_note);
END;
$function$;
