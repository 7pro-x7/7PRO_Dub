-- ============================================================================
-- Fix: Remove version checks from ALL subscription-related RPC functions
-- 
-- These functions were checking the app version and returning an error
-- message when the version was too old. This migration replaces them all
-- with clean versions that don't block any build.
-- ============================================================================

-- ============================================================================
-- 1. subscription_stats
-- ============================================================================
DROP FUNCTION IF EXISTS public.subscription_stats(UUID);
CREATE OR REPLACE FUNCTION public.subscription_stats(p_teacher UUID DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
  total_count INT;
  due_this_week INT;
  overdue_count INT;
  total_monthly NUMERIC(12,2);
BEGIN
  -- Refresh statuses first
  PERFORM refresh_subscription_statuses();

  SELECT count(*), count(*) FILTER (WHERE status = 'DUE'), count(*) FILTER (WHERE status = 'OVERDUE'), COALESCE(sum(monthly_amount), 0)
  INTO total_count, due_this_week, overdue_count, total_monthly
  FROM teacher_subscriptions
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND status != 'PAUSED';

  -- Also count those due within 7 days (may not be marked DUE yet if status refresh hasn't run)
  due_this_week := (SELECT count(*) FROM teacher_subscriptions
    WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
      AND status IN ('ACTIVE', 'DUE')
      AND next_renewal_date <= current_date + INTERVAL '7 days'
      AND next_renewal_date >= current_date);

  result := jsonb_build_object(
    'total_subscriptions', total_count,
    'due_this_week', due_this_week,
    'overdue', overdue_count,
    'total_monthly_value', total_monthly
  );

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.subscription_stats(UUID) TO authenticated;
-- ============================================================================
-- 2. subscription_earnings_summary
-- ============================================================================
DROP FUNCTION IF EXISTS public.subscription_earnings_summary(TEXT, DATE, DATE);
DROP FUNCTION IF EXISTS public.subscription_earnings_summary(UUID, TEXT, DATE, DATE);
CREATE OR REPLACE FUNCTION public.subscription_earnings_summary(
  p_teacher UUID DEFAULT NULL,
  p_filter TEXT DEFAULT 'month',
  p_from DATE DEFAULT NULL,
  p_to DATE DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
  v_start DATE;
  v_end DATE;
  total_earned NUMERIC(12,2);
  teacher_sh NUMERIC(12,2);
  owner_sh NUMERIC(12,2);
  sub_count INT;
  groups JSONB;
BEGIN
  -- Determine period
  CASE p_filter
    WHEN 'today' THEN
      v_start := current_date;
      v_end := current_date;
    WHEN 'week' THEN
      v_start := current_date - INTERVAL '7 days';
      v_end := current_date;
    WHEN 'month' THEN
      v_start := date_trunc('month', current_date)::date;
      v_end := current_date;
    WHEN 'custom' THEN
      v_start := COALESCE(p_from, date_trunc('month', current_date)::date);
      v_end := COALESCE(p_to, current_date);
    ELSE
      v_start := date_trunc('month', current_date)::date;
      v_end := current_date;
  END CASE;

  SELECT
    COALESCE(sum(monthly_amount), 0),
    COALESCE(sum(teacher_earning), 0),
    COALESCE(sum(owner_earning), 0),
    count(DISTINCT subscription_id)
  INTO total_earned, teacher_sh, owner_sh, sub_count
  FROM subscription_earnings
  WHERE period_start >= v_start AND period_end <= v_end
    AND (p_teacher IS NULL OR teacher_id = p_teacher);

  SELECT COALESCE(jsonb_agg(
    jsonb_build_object(
      'group_name', g.group_name,
      'total', g.total,
      'teacher_share', g.teacher_share,
      'owner_share', g.owner_share,
      'count', g.count
    ) ORDER BY g.total DESC
  ), '[]'::jsonb)
  INTO groups
  FROM (
    SELECT
      group_name,
      sum(monthly_amount) AS total,
      sum(teacher_earning) AS teacher_share,
      sum(owner_earning) AS owner_share,
      count(*) AS count
    FROM subscription_earnings
    WHERE period_start >= v_start AND period_end <= v_end
      AND (p_teacher IS NULL OR teacher_id = p_teacher)
    GROUP BY group_name
  ) g;

  result := jsonb_build_object(
    'period_start', v_start,
    'period_end', v_end,
    'total_earned', total_earned,
    'teacher_share', teacher_sh,
    'owner_share', owner_sh,
    'subscription_count', sub_count,
    'groups', groups
  );

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.subscription_earnings_summary(UUID, TEXT, DATE, DATE) TO authenticated;
-- ============================================================================
-- 3. subscription_earnings_reports
-- ============================================================================
DROP FUNCTION IF EXISTS public.subscription_earnings_reports(UUID);
CREATE OR REPLACE FUNCTION public.subscription_earnings_reports(
  p_teacher UUID DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
BEGIN
  SELECT COALESCE(jsonb_agg(
    jsonb_build_object(
      'id', r.id,
      'teacher_id', r.teacher_id,
      'year', r.year,
      'month', r.month,
      'total_earned', r.total_earned,
      'owner_share', r.owner_share,
      'currency', r.currency,
      'subscription_count', r.subscription_count,
      'generated_at', r.generated_at,
      'data', COALESCE(r.data, '[]'::jsonb)
    ) ORDER BY r.year DESC, r.month DESC
  ), '[]'::jsonb)
  INTO result
  FROM subscription_earnings_reports r
  WHERE (p_teacher IS NULL OR r.teacher_id = p_teacher);

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.subscription_earnings_reports(UUID) TO authenticated;
-- ============================================================================
-- 4. record_subscription_earning
-- ============================================================================
DROP FUNCTION IF EXISTS public.record_subscription_earning(UUID, UUID);
CREATE OR REPLACE FUNCTION public.record_subscription_earning(
  p_subscription_id UUID,
  p_renewal_id UUID DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
  v_sub RECORD;
  v_teacher UUID;
  v_group TEXT;
  v_amount NUMERIC(12,2);
  v_rate NUMERIC(5,2);
  v_teacher_earning NUMERIC(12,2);
  v_owner_earning NUMERIC(12,2);
  v_period_start DATE;
  v_period_end DATE;
BEGIN
  SELECT * INTO v_sub FROM teacher_subscriptions WHERE id = p_subscription_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SUBSCRIPTION_NOT_FOUND';
  END IF;

  v_teacher := v_sub.teacher_id;
  v_group := v_sub.group_name;
  v_amount := v_sub.monthly_amount;

  -- Get teacher's subscription rate (default 70% to teacher)
  SELECT COALESCE(percentage, 70) INTO v_rate
  FROM teacher_subscription_rates
  WHERE teacher_id = v_teacher;

  v_teacher_earning := round(v_amount * v_rate / 100, 2);
  v_owner_earning := v_amount - v_teacher_earning;
  v_period_start := date_trunc('month', current_date)::date;
  v_period_end := (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date;

  INSERT INTO subscription_earnings (
    teacher_id, subscription_id, renewal_id,
    group_name, monthly_amount, currency,
    teacher_percentage, teacher_earning, owner_earning,
    period_start, period_end
  ) VALUES (
    v_teacher, p_subscription_id, p_renewal_id,
    v_group, v_amount, v_sub.currency,
    v_rate, v_teacher_earning, v_owner_earning,
    v_period_start, v_period_end
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.record_subscription_earning(UUID, UUID) TO authenticated;
-- ============================================================================
-- 5. teacher_balance
-- ============================================================================
DROP FUNCTION IF EXISTS public.teacher_balance(UUID);
CREATE OR REPLACE FUNCTION public.teacher_balance(p_teacher UUID)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
  v_gross NUMERIC(12,2);
  v_paid NUMERIC(12,2);
  v_pending NUMERIC(12,2);
  v_available NUMERIC(12,2);
BEGIN
  SELECT COALESCE(sum(
    CASE WHEN kind IN ('CREDIT', 'ORDER') AND status != 'REFUNDED' THEN amount ELSE 0 END
  ), 0)
  INTO v_gross
  FROM teacher_ledger
  WHERE teacher_id = p_teacher;

  SELECT COALESCE(sum(
    CASE WHEN kind = 'PAYOUT' AND status = 'PAID' THEN amount ELSE 0 END
  ), 0)
  INTO v_paid
  FROM teacher_ledger
  WHERE teacher_id = p_teacher;

  SELECT COALESCE(sum(
    CASE WHEN kind = 'CREDIT' AND status = 'PENDING' THEN amount ELSE 0 END
  ), 0)
  INTO v_pending
  FROM teacher_ledger
  WHERE teacher_id = p_teacher;

  v_available := GREATEST(v_gross - v_paid, 0);

  result := jsonb_build_object(
    'pending', v_pending,
    'available', v_available,
    'paid', v_paid,
    'gross', v_gross,
    'currency', 'EGP',
    'minimum_payout', 1000
  );

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.teacher_balance(UUID) TO authenticated;
-- ============================================================================
-- 6. set_teacher_subscription_rate
-- ============================================================================
DROP FUNCTION IF EXISTS public.set_teacher_subscription_rate(UUID, NUMERIC);
CREATE OR REPLACE FUNCTION public.set_teacher_subscription_rate(
  p_teacher UUID,
  p_percentage NUMERIC
)
RETURNS VOID AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM teacher_subscription_rates WHERE teacher_id = p_teacher) THEN
    UPDATE teacher_subscription_rates
    SET percentage = p_percentage, set_by = auth.uid(), updated_at = now()
    WHERE teacher_id = p_teacher;
  ELSE
    INSERT INTO teacher_subscription_rates (teacher_id, percentage, set_by)
    VALUES (p_teacher, p_percentage, auth.uid());
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.set_teacher_subscription_rate(UUID, NUMERIC) TO authenticated;
-- ============================================================================
-- 7. get_teacher_subscription_rate
-- ============================================================================
DROP FUNCTION IF EXISTS public.get_teacher_subscription_rate(UUID);
CREATE OR REPLACE FUNCTION public.get_teacher_subscription_rate(
  p_teacher UUID
)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
BEGIN
  SELECT COALESCE(
    (SELECT jsonb_build_object('percentage', percentage)
     FROM teacher_subscription_rates WHERE teacher_id = p_teacher),
    jsonb_build_object('percentage', 0)
  ) INTO result;

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.get_teacher_subscription_rate(UUID) TO authenticated;
-- ============================================================================
-- 8. generate_subscription_monthly_report
-- ============================================================================
DROP FUNCTION IF EXISTS public.generate_subscription_monthly_report(UUID, INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION public.generate_subscription_monthly_report(
  p_teacher UUID DEFAULT NULL,
  p_year INTEGER DEFAULT NULL,
  p_month INTEGER DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
  v_year INTEGER;
  v_month INTEGER;
  v_start DATE;
  v_end DATE;
  v_total NUMERIC(12,2);
  v_owner NUMERIC(12,2);
  v_count INT;
  v_groups JSONB;
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
  FROM subscription_earnings
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
    FROM subscription_earnings
    WHERE period_start >= v_start AND period_end <= v_end
      AND (p_teacher IS NULL OR teacher_id = p_teacher)
    GROUP BY group_name
  ) g;

  -- Upsert report (one per teacher/year/month)
  IF p_teacher IS NOT NULL THEN
    DELETE FROM subscription_earnings_reports
    WHERE teacher_id = p_teacher AND year = v_year AND month = v_month;

    INSERT INTO subscription_earnings_reports (
      teacher_id, year, month, total_earned, owner_share, currency,
      subscription_count, data
    ) VALUES (
      p_teacher, v_year, v_month, v_total, v_owner, 'EGP',
      v_count, v_groups
    );
  ELSE
    -- Generate reports for each teacher
    DELETE FROM subscription_earnings_reports
    WHERE year = v_year AND month = v_month;

    INSERT INTO subscription_earnings_reports (
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
    FROM subscription_earnings t
    WHERE t.period_start >= v_start AND t.period_end <= v_end
    GROUP BY t.teacher_id;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.generate_subscription_monthly_report(UUID, INTEGER, INTEGER) TO authenticated;
