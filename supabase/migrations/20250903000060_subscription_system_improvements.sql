-- ============================================================================
-- Subscription System Improvements
-- ============================================================================

-- ============================================================================
-- #1 Auto-refresh subscription statuses
-- Marks DUE (due within 7 days) and OVERDUE (past due) subscriptions
-- ============================================================================

CREATE OR REPLACE FUNCTION public.refresh_subscription_statuses()
RETURNS VOID AS $$
BEGIN
  -- Mark subscriptions as DUE (due within 7 days)
  UPDATE public.teacher_subscriptions
  SET status = 'DUE', updated_at = now()
  WHERE status = 'ACTIVE'
    AND next_renewal_date <= current_date + INTERVAL '7 days'
    AND next_renewal_date >= current_date;

  -- Mark subscriptions as OVERDUE (past due date)
  UPDATE public.teacher_subscriptions
  SET status = 'OVERDUE', updated_at = now()
  WHERE status IN ('ACTIVE', 'DUE')
    AND next_renewal_date < current_date;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public;
-- Grant execute to authenticated
GRANT EXECUTE ON FUNCTION public.refresh_subscription_statuses() TO authenticated;
-- ============================================================================
-- #2 Date validation CHECK constraints
-- ============================================================================

-- Add CHECK constraint: start_date must be <= next_renewal_date
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_subscription_dates'
    AND conrelid = 'public.teacher_subscriptions'::regclass
  ) THEN
    ALTER TABLE public.teacher_subscriptions
      ADD CONSTRAINT chk_subscription_dates CHECK (start_date <= next_renewal_date);
  END IF;
END $$;
-- ============================================================================
-- #4 Record earnings on first subscription creation (server trigger)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.record_initial_subscription_earning()
RETURNS TRIGGER AS $$
DECLARE
  v_rate NUMERIC(5,2);
  v_teacher_earning NUMERIC(12,2);
  v_owner_earning NUMERIC(12,2);
  v_period_start DATE;
  v_period_end DATE;
BEGIN
  -- Get teacher's subscription rate (default 70% to teacher)
  SELECT COALESCE(percentage, 70) INTO v_rate
  FROM public.teacher_subscription_rates
  WHERE teacher_id = NEW.teacher_id;

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
    COALESCE(v_rate, 70), v_teacher_earning, v_owner_earning,
    v_period_start, v_period_end
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public;
-- Create trigger (only fires on INSERT, not UPDATE)
DROP TRIGGER IF EXISTS trg_record_initial_earning ON public.teacher_subscriptions;
CREATE TRIGGER trg_record_initial_earning
  AFTER INSERT ON public.teacher_subscriptions
  FOR EACH ROW
  WHEN (NEW.status != 'PAUSED')
  EXECUTE FUNCTION public.record_initial_subscription_earning();
GRANT EXECUTE ON FUNCTION public.record_initial_subscription_earning() TO authenticated;
-- ============================================================================
-- #5 Add ledger entries for subscription renewals
-- ============================================================================

CREATE OR REPLACE FUNCTION public.record_subscription_ledger_entry()
RETURNS TRIGGER AS $$
DECLARE
  v_sub RECORD;
BEGIN
  -- Get subscription details
  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = NEW.subscription_id;
  IF NOT FOUND THEN RETURN NEW; END IF;

  -- Create a CREDIT entry in the teacher ledger
  INSERT INTO public.teacher_ledger (
    teacher_id, kind, amount, currency, status, note
  ) VALUES (
    v_sub.teacher_id,
    'CREDIT',
    NEW.amount,
    NEW.currency,
    'AVAILABLE',
    'Subscription renewal: ' || v_sub.group_name || ' - ' || v_sub.student_name
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public;
DROP TRIGGER IF EXISTS trg_record_renewal_ledger ON public.subscription_renewals;
CREATE TRIGGER trg_record_renewal_ledger
  AFTER INSERT ON public.subscription_renewals
  FOR EACH ROW
  EXECUTE FUNCTION public.record_subscription_ledger_entry();
GRANT EXECUTE ON FUNCTION public.record_subscription_ledger_entry() TO authenticated;
-- ============================================================================
-- #7 Full-text search index on teacher_subscriptions
-- ============================================================================

-- Add a GIN index for fast full-text search across student_name, parent_name, group_name
CREATE INDEX IF NOT EXISTS idx_teacher_subscriptions_search
  ON public.teacher_subscriptions
  USING gin(to_tsvector('simple', coalesce(student_name,'') || ' ' || coalesce(parent_name,'') || ' ' || coalesce(group_name,'')));
-- ============================================================================
-- #9 Server-side report generation function (replaces client-side logic)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.generate_subscription_monthly_report_v2(
  p_teacher UUID DEFAULT NULL,
  p_year INTEGER DEFAULT NULL,
  p_month INTEGER DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  v_year INTEGER;
  v_month INTEGER;
  v_start DATE;
  v_end DATE;
  v_total NUMERIC(12,2);
  v_owner NUMERIC(12,2);
  v_count INT;
  v_groups JSONB;
  v_teacher UUID;
BEGIN
  v_year := COALESCE(p_year, EXTRACT(YEAR FROM current_date)::int);
  v_month := COALESCE(p_month, EXTRACT(MONTH FROM current_date)::int);
  v_start := make_date(v_year, v_month, 1);
  v_end := (v_start + INTERVAL '1 month - 1 day')::date;

  SELECT
    COALESCE(sum(monthly_amount), 0),
    COALESCE(sum(owner_earning), 0),
    count(DISTINCT subscription_id)
  INTO v_total, v_owner, v_count
  FROM public.subscription_earnings
  WHERE period_start >= v_start AND period_end <= v_end
    AND (p_teacher IS NULL OR teacher_id = p_teacher);

  SELECT COALESCE(jsonb_agg(
    jsonb_build_object(
      'group_name', g.group_name,
      'total', g.total,
      'teacher_share', g.teacher_share,
      'owner_share', g.owner_share,
      'count', g.count
    )
  ), '[]'::jsonb)
  INTO v_groups
  FROM (
    SELECT
      group_name,
      sum(monthly_amount) AS total,
      sum(teacher_earning) AS teacher_share,
      sum(owner_earning) AS owner_share,
      count(*) AS count
    FROM public.subscription_earnings
    WHERE period_start >= v_start AND period_end <= v_end
      AND (p_teacher IS NULL OR teacher_id = p_teacher)
    GROUP BY group_name
  ) g;

  -- Upsert report
  IF p_teacher IS NOT NULL THEN
    DELETE FROM public.subscription_earnings_reports
    WHERE teacher_id = p_teacher AND year = v_year AND month = v_month;

    INSERT INTO public.subscription_earnings_reports (
      teacher_id, year, month, total_earned, owner_share, currency,
      subscription_count, data
    ) VALUES (
      p_teacher, v_year, v_month, v_total, v_owner, 'EGP',
      v_count, v_groups
    );
  ELSE
    DELETE FROM public.subscription_earnings_reports
    WHERE year = v_year AND month = v_month;

    INSERT INTO public.subscription_earnings_reports (
      teacher_id, year, month, total_earned, owner_share, currency,
      subscription_count, data
    )
    SELECT
      t.teacher_id, v_year, v_month,
      COALESCE(sum(t.monthly_amount), 0),
      COALESCE(sum(t.owner_earning), 0),
      'EGP',
      count(DISTINCT t.subscription_id),
      '[]'::jsonb
    FROM public.subscription_earnings t
    WHERE t.period_start >= v_start AND t.period_end <= v_end
    GROUP BY t.teacher_id;
  END IF;

  RETURN jsonb_build_object(
    'total_earned', v_total,
    'owner_share', v_owner,
    'subscription_count', v_count,
    'groups', v_groups
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public;
GRANT EXECUTE ON FUNCTION public.generate_subscription_monthly_report_v2(UUID, INTEGER, INTEGER) TO authenticated;
-- ============================================================================
-- Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
