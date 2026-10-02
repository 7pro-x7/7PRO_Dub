-- ============================================================================
-- Subscription system — integrity fixes found by auditing the LIVE database
--
-- BUG 1 (money can be paid twice)
--   subscription_earnings had NO uniqueness guarantee at all — only a primary
--   key on the surrogate id. The migration history shows repeated
--   application-level patches for exactly this ("fix_duplicate_earning_
--   triggers", "fix_approval_double_counting", "fix_comprehensive_
--   subscription_issues", ...), each fixing one code path while leaving the
--   table itself willing to accept the same period twice. Any future trigger
--   change, retry, or concurrent approve+renew re-creates the bug and pays a
--   teacher twice for one month. A unique index makes that structurally
--   impossible rather than something the next patch has to remember.
--
--   Verified before applying: zero duplicate (subscription_id, period_start,
--   period_end) rows exist today, so the index builds cleanly.
--
-- BUG 2 (the earnings hold period never applied to subscriptions)
--   _record_subscription_earning() wrote ledger rows as
--       kind = 'CREDIT', status = 'AVAILABLE', available_at = NULL
--   while release_matured_earnings() — which request_payout() calls before
--   every withdrawal — only ever looks at
--       kind = 'EARNING' AND status = 'PENDING' AND available_at <= now()
--   The two never matched, so for subscription income the whole
--   pending -> matured -> available safety mechanism was dead code: every
--   subscription earning was instantly withdrawable. Live data confirms it:
--   17 CREDIT/AVAILABLE rows totalling 5,180 with available_at NULL on every
--   single one, versus 7 EARNING/PENDING rows that all carry available_at.
--   That means if a subscription is later rejected or reversed, the money may
--   already be gone.
--
--   Fixed so subscription earnings flow through the SAME mechanism as course
--   earnings. The hold length is read from the existing settings table as
--   'subscription.hold_days' and DEFAULTS TO 0, which reproduces today's
--   behaviour exactly — no teacher's current balance moves because of this
--   migration. The owner can then introduce a real hold whenever they choose
--   with a single settings update, no code change:
--       select set_setting('subscription.hold_days', '14');
--
--   Existing rows are deliberately NOT rewritten: retroactively moving 5,180
--   of already-available balance into PENDING would be a business decision
--   about money teachers can see today, not a bug fix.
-- ============================================================================

-- ---------------------------------------------------------------- BUG 1
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_earnings_period
  ON public.subscription_earnings (subscription_id, period_start, period_end);

-- ---------------------------------------------------------------- BUG 2
CREATE OR REPLACE FUNCTION public._record_subscription_earning(
  p_subscription_id uuid,
  p_renewal_id uuid,
  p_period_start date,
  p_period_end date,
  p_note text
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $function$
DECLARE
  v_sub RECORD;
  v_rate NUMERIC;
  v_teacher_share NUMERIC;
  v_owner_share NUMERIC;
  v_hold_days NUMERIC;
  v_available_at TIMESTAMPTZ;
  v_status ledger_status;
BEGIN
  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = p_subscription_id;
  IF NOT FOUND THEN RETURN; END IF;

  v_rate := public.get_teacher_subscription_rate(v_sub.teacher_id);
  v_teacher_share := round(v_sub.monthly_amount * v_rate / 100, 2);
  v_owner_share := v_sub.monthly_amount - v_teacher_share;

  -- The unique index added above is the real guard against paying the same
  -- period twice. ON CONFLICT turns that from an error the caller has to
  -- handle into a clean no-op, so an approve/renew retry is idempotent
  -- instead of failing loudly — and, critically, the ledger insert below is
  -- then skipped too, because it only runs when a NEW earning row was
  -- actually created.
  INSERT INTO public.subscription_earnings (
    teacher_id, subscription_id, renewal_id, group_name, monthly_amount, currency,
    teacher_percentage, teacher_earning, owner_earning, period_start, period_end
  ) VALUES (
    v_sub.teacher_id, p_subscription_id, p_renewal_id, v_sub.group_name, v_sub.monthly_amount, v_sub.currency,
    v_rate, v_teacher_share, v_owner_share, p_period_start, p_period_end
  )
  ON CONFLICT (subscription_id, period_start, period_end) DO NOTHING;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  v_hold_days := public.setting_num('subscription.hold_days', 0);
  v_available_at := now() + (v_hold_days || ' days')::INTERVAL;

  -- kind = 'EARNING' (not the old 'CREDIT') so release_matured_earnings()
  -- actually sees these rows. With hold_days = 0 the row is created already
  -- AVAILABLE, which is byte-for-byte the current behaviour; with any
  -- positive hold it now correctly waits and matures like course earnings.
  v_status := CASE WHEN v_hold_days > 0 THEN 'PENDING'::ledger_status ELSE 'AVAILABLE'::ledger_status END;

  INSERT INTO public.teacher_ledger (teacher_id, kind, amount, currency, status, available_at, note)
  VALUES (v_sub.teacher_id, 'EARNING', v_teacher_share, v_sub.currency, v_status, v_available_at, p_note);
END;
$function$;
;
