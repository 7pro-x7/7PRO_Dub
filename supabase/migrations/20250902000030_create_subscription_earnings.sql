-- ============================================================================
-- Subscription Earnings System
-- ============================================================================

-- ============================================================================
-- 1. TEACHER SUBSCRIPTION RATE (locked per teacher, set by Owner)
-- ============================================================================
CREATE TABLE IF NOT EXISTS teacher_subscription_rates (
  teacher_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  percentage NUMERIC(5,2) NOT NULL CHECK (percentage >= 0 AND percentage <= 100),
  set_by UUID NOT NULL REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_teacher_subscription_rates_teacher ON teacher_subscription_rates(teacher_id);
-- ============================================================================
-- 2. SUBSCRIPTION EARNINGS (one row per renewal event)
-- ============================================================================
CREATE TABLE IF NOT EXISTS subscription_earnings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  subscription_id UUID NOT NULL REFERENCES teacher_subscriptions(id) ON DELETE CASCADE,
  renewal_id UUID REFERENCES subscription_renewals(id) ON DELETE SET NULL,
  group_name TEXT NOT NULL,
  monthly_amount NUMERIC(10,2) NOT NULL,
  currency TEXT NOT NULL DEFAULT 'EGP',
  teacher_percentage NUMERIC(5,2) NOT NULL,
  teacher_earning NUMERIC(10,2) NOT NULL,
  owner_earning NUMERIC(10,2) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscription_earnings_teacher ON subscription_earnings(teacher_id);
CREATE INDEX idx_subscription_earnings_subscription ON subscription_earnings(subscription_id);
CREATE INDEX idx_subscription_earnings_period ON subscription_earnings(period_start, period_end);
CREATE INDEX idx_subscription_earnings_created ON subscription_earnings(created_at DESC);
-- ============================================================================
-- 3. MONTHLY EARNINGS REPORTS (saved snapshots)
-- ============================================================================
CREATE TABLE IF NOT EXISTS subscription_earnings_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  year INT NOT NULL,
  month INT NOT NULL CHECK (month >= 1 AND month <= 12),
  total_earned NUMERIC(12,2) NOT NULL DEFAULT 0,
  owner_share NUMERIC(12,2) NOT NULL DEFAULT 0,
  currency TEXT NOT NULL DEFAULT 'EGP',
  subscription_count INT NOT NULL DEFAULT 0,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE(teacher_id, year, month)
);
CREATE INDEX idx_subscription_earnings_reports_teacher ON subscription_earnings_reports(teacher_id);
CREATE INDEX idx_subscription_earnings_reports_year_month ON subscription_earnings_reports(year DESC, month DESC);
-- ============================================================================
-- 4. RLS POLICIES
-- ============================================================================
ALTER TABLE teacher_subscription_rates ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_earnings ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_earnings_reports ENABLE ROW LEVEL SECURITY;
-- teacher_subscription_rates: owner/admin full access
CREATE POLICY "staff_full_access_sub_rates"
  ON teacher_subscription_rates FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- teacher_subscription_rates: teachers can read their own rate
CREATE POLICY "teachers_read_own_sub_rate"
  ON teacher_subscription_rates FOR SELECT
  USING (auth.uid() = teacher_id);
-- subscription_earnings: owner/admin full access
CREATE POLICY "staff_full_access_sub_earnings"
  ON subscription_earnings FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- subscription_earnings: teachers read own
CREATE POLICY "teachers_read_own_sub_earnings"
  ON subscription_earnings FOR SELECT
  USING (auth.uid() = teacher_id);
-- subscription_earnings_reports: owner/admin full access
CREATE POLICY "staff_full_access_sub_reports"
  ON subscription_earnings_reports FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- subscription_earnings_reports: teachers read own
CREATE POLICY "teachers_read_own_sub_reports"
  ON subscription_earnings_reports FOR SELECT
  USING (auth.uid() = teacher_id);
-- ============================================================================
-- 5. GRANTS
-- ============================================================================
GRANT SELECT, INSERT, UPDATE, DELETE ON teacher_subscription_rates TO authenticated;
GRANT SELECT, INSERT ON subscription_earnings TO authenticated;
GRANT SELECT, INSERT ON subscription_earnings_reports TO authenticated;
-- ============================================================================
-- 6. RPC: Get or create teacher subscription rate
-- ============================================================================
CREATE OR REPLACE FUNCTION get_teacher_subscription_rate(p_teacher UUID)
RETURNS NUMERIC AS $$
  SELECT COALESCE(
    (SELECT percentage FROM teacher_subscription_rates WHERE teacher_id = p_teacher),
    0
  );
$$ LANGUAGE sql SECURITY DEFINER;
-- ============================================================================
-- 7. RPC: Set teacher subscription rate (owner only)
-- ============================================================================
CREATE OR REPLACE FUNCTION set_teacher_subscription_rate(
  p_teacher UUID,
  p_percentage NUMERIC
)
RETURNS VOID AS $$
DECLARE
  v_caller_role TEXT;
BEGIN
  SELECT role INTO v_caller_role FROM profiles WHERE id = auth.uid();
  IF v_caller_role != 'OWNER' THEN
    RAISE EXCEPTION 'Only owner can set subscription rates';
  END IF;
  IF p_percentage < 0 OR p_percentage > 100 THEN
    RAISE EXCEPTION 'Percentage must be between 0 and 100';
  END IF;
  INSERT INTO teacher_subscription_rates (teacher_id, percentage, set_by)
  VALUES (p_teacher, p_percentage, auth.uid())
  ON CONFLICT (teacher_id) DO UPDATE
    SET percentage = EXCLUDED.percentage,
        set_by = EXCLUDED.set_by,
        updated_at = now();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION get_teacher_subscription_rate(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION set_teacher_subscription_rate(UUID, NUMERIC) TO authenticated;
-- ============================================================================
-- 8. RPC: Record earnings from a renewal (called automatically on renew)
-- ============================================================================
CREATE OR REPLACE FUNCTION record_subscription_earning(
  p_subscription_id UUID,
  p_renewal_id UUID
)
RETURNS VOID AS $$
DECLARE
  v_sub RECORD;
  v_rate NUMERIC;
  v_teacher_earning NUMERIC;
  v_owner_earning NUMERIC;
  v_period_start DATE;
  v_period_end DATE;
BEGIN
  SELECT ts.* INTO v_sub
  FROM teacher_subscriptions ts
  WHERE ts.id = p_subscription_id;

  IF v_sub IS NULL THEN
    RAISE EXCEPTION 'Subscription not found';
  END IF;

  SELECT COALESCE(tsr.percentage, 0) INTO v_rate
  FROM teacher_subscription_rates tsr
  WHERE tsr.teacher_id = v_sub.teacher_id;
  v_teacher_earning := ROUND(v_sub.monthly_amount * v_rate / 100, 2);
  v_owner_earning := v_sub.monthly_amount - v_teacher_earning;

  -- Period is the month just completed
  v_period_start := v_sub.start_date;
  v_period_end := v_sub.next_renewal_date;

  INSERT INTO subscription_earnings (
    teacher_id, subscription_id, renewal_id, group_name,
    monthly_amount, currency, teacher_percentage,
    teacher_earning, owner_earning,
    period_start, period_end
  ) VALUES (
    v_sub.teacher_id, p_subscription_id, p_renewal_id, v_sub.group_name,
    v_sub.monthly_amount, v_sub.currency, v_rate,
    v_teacher_earning, v_owner_earning,
    v_period_start, v_period_end
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION record_subscription_earning(UUID, UUID) TO authenticated;
-- ============================================================================
-- 9. RPC: Teacher subscription earnings summary (filtered)
-- ============================================================================
CREATE OR REPLACE FUNCTION subscription_earnings_summary(
  p_teacher UUID DEFAULT NULL,
  p_filter TEXT DEFAULT 'month',
  p_from DATE DEFAULT NULL,
  p_to DATE DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  v_start DATE;
  v_end DATE;
  v_result JSONB;
  v_total_earned NUMERIC;
  v_teacher_share NUMERIC;
  v_owner_share NUMERIC;
  v_sub_count INT;
  v_group_data JSONB;
BEGIN
  -- Determine date range
  IF p_filter = 'today' THEN
    v_start := current_date;
    v_end := current_date;
  ELSIF p_filter = 'week' THEN
    v_start := current_date - INTERVAL '7 days';
    v_end := current_date;
  ELSIF p_filter = 'month' THEN
    v_start := date_trunc('month', current_date)::date;
    v_end := current_date;
  ELSIF p_filter = 'custom' AND p_from IS NOT NULL AND p_to IS NOT NULL THEN
    v_start := p_from;
    v_end := p_to;
  ELSE
    v_start := date_trunc('month', current_date)::date;
    v_end := current_date;
  END IF;

  -- Aggregate earnings
  SELECT
    COALESCE(SUM(monthly_amount), 0),
    COALESCE(SUM(teacher_earning), 0),
    COALESCE(SUM(owner_earning), 0),
    COUNT(DISTINCT subscription_id)
  INTO v_total_earned, v_teacher_share, v_owner_share, v_sub_count
  FROM subscription_earnings
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND created_at::date >= v_start
    AND created_at::date <= v_end;

  -- Group breakdown
  SELECT COALESCE(
    jsonb_agg(jsonb_build_object(
      'group_name', gn,
      'total', total,
      'teacher_share', tshare,
      'owner_share', oshare,
      'count', cnt
    )),
    '[]'::jsonb
  ) INTO v_group_data
  FROM (
    SELECT
      group_name AS gn,
      SUM(monthly_amount) AS total,
      SUM(teacher_earning) AS tshare,
      SUM(owner_earning) AS oshare,
      COUNT(*) AS cnt
    FROM subscription_earnings
    WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
      AND created_at::date >= v_start
      AND created_at::date <= v_end
    GROUP BY group_name
    ORDER BY total DESC
  ) grp;

  v_result := jsonb_build_object(
    'period_start', v_start,
    'period_end', v_end,
    'total_earned', v_total_earned,
    'teacher_share', v_teacher_share,
    'owner_share', v_owner_share,
    'subscription_count', v_sub_count,
    'groups', v_group_data
  );

  RETURN v_result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION subscription_earnings_summary(UUID, TEXT, DATE, DATE) TO authenticated;
-- ============================================================================
-- 10. RPC: Generate & save monthly report
-- ============================================================================
CREATE OR REPLACE FUNCTION generate_subscription_monthly_report(
  p_teacher UUID DEFAULT NULL,
  p_year INT DEFAULT NULL,
  p_month INT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  v_target_teacher UUID;
  v_year INT;
  v_month INT;
  v_start DATE;
  v_end DATE;
  v_total NUMERIC;
  v_teacher_share NUMERIC;
  v_owner_share NUMERIC;
  v_count INT;
  v_data JSONB;
  v_report_id UUID;
  v_teachers RECORD;
BEGIN
  v_year := COALESCE(p_year, EXTRACT(YEAR FROM current_date)::int);
  v_month := COALESCE(p_month, EXTRACT(MONTH FROM current_date)::int);
  v_start := make_date(v_year, v_month, 1);
  v_end := (v_start + INTERVAL '1 month' - INTERVAL '1 day')::date;

  -- If specific teacher, process only that one
  IF p_teacher IS NOT NULL THEN
    SELECT
      COALESCE(SUM(monthly_amount), 0),
      COALESCE(SUM(teacher_earning), 0),
      COALESCE(SUM(owner_earning), 0),
      COUNT(DISTINCT subscription_id)
    INTO v_total, v_teacher_share, v_owner_share, v_count
    FROM subscription_earnings
    WHERE teacher_id = p_teacher
      AND created_at::date >= v_start AND created_at::date <= v_end;

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'group_name', gn, 'total', t, 'teacher_share', ts, 'owner_share', os, 'count', c
    )), '[]'::jsonb) INTO v_data
    FROM (
      SELECT group_name AS gn, SUM(monthly_amount) AS t,
        SUM(teacher_earning) AS ts, SUM(owner_earning) AS os, COUNT(*) AS c
      FROM subscription_earnings
      WHERE teacher_id = p_teacher
        AND created_at::date >= v_start AND created_at::date <= v_end
      GROUP BY group_name ORDER BY t DESC
    ) g;

    INSERT INTO subscription_earnings_reports (teacher_id, year, month, total_earned, owner_share, subscription_count, data)
    VALUES (p_teacher, v_year, v_month, v_total, v_owner_share, v_count, v_data)
    ON CONFLICT (teacher_id, year, month) DO UPDATE
      SET total_earned = EXCLUDED.total_earned,
          owner_share = EXCLUDED.owner_share,
          subscription_count = EXCLUDED.subscription_count,
          data = EXCLUDED.data,
          generated_at = now();

    RETURN jsonb_build_object(
      'teacher_id', p_teacher,
      'year', v_year, 'month', v_month,
      'total_earned', v_total, 'teacher_share', v_teacher_share,
      'owner_share', v_owner_share, 'subscription_count', v_count,
      'groups', v_data
    );
  END IF;

  -- Process all teachers
  FOR v_teachers IN
    SELECT DISTINCT teacher_id FROM subscription_earnings
    WHERE created_at::date >= v_start AND created_at::date <= v_end
  LOOP
    SELECT
      COALESCE(SUM(monthly_amount), 0),
      COALESCE(SUM(teacher_earning), 0),
      COALESCE(SUM(owner_earning), 0),
      COUNT(DISTINCT subscription_id)
    INTO v_total, v_teacher_share, v_owner_share, v_count
    FROM subscription_earnings
    WHERE teacher_id = v_teachers.teacher_id
      AND created_at::date >= v_start AND created_at::date <= v_end;

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'group_name', gn, 'total', t, 'teacher_share', ts, 'owner_share', os, 'count', c
    )), '[]'::jsonb) INTO v_data
    FROM (
      SELECT group_name AS gn, SUM(monthly_amount) AS t,
        SUM(teacher_earning) AS ts, SUM(owner_earning) AS os, COUNT(*) AS c
      FROM subscription_earnings
      WHERE teacher_id = v_teachers.teacher_id
        AND created_at::date >= v_start AND created_at::date <= v_end
      GROUP BY group_name ORDER BY t DESC
    ) g;

    INSERT INTO subscription_earnings_reports (teacher_id, year, month, total_earned, owner_share, subscription_count, data)
    VALUES (v_teachers.teacher_id, v_year, v_month, v_total, v_owner_share, v_count, v_data)
    ON CONFLICT (teacher_id, year, month) DO UPDATE
      SET total_earned = EXCLUDED.total_earned,
          owner_share = EXCLUDED.owner_share,
          subscription_count = EXCLUDED.subscription_count,
          data = EXCLUDED.data,
          generated_at = now();
  END LOOP;

  RETURN jsonb_build_object('ok', true, 'year', v_year, 'month', v_month);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION generate_subscription_monthly_report(UUID, INT, INT) TO authenticated;
-- ============================================================================
-- 11. RPC: Get saved monthly reports
-- ============================================================================
CREATE OR REPLACE FUNCTION subscription_earnings_reports(
  p_teacher UUID DEFAULT NULL
)
RETURNS TABLE (
  id UUID,
  teacher_id UUID,
  year INT,
  month INT,
  total_earned NUMERIC,
  owner_share NUMERIC,
  currency TEXT,
  subscription_count INT,
  generated_at TIMESTAMPTZ,
  data JSONB
) AS $$
BEGIN
  RETURN QUERY
  SELECT r.id, r.teacher_id, r.year, r.month, r.total_earned,
         r.owner_share, r.currency, r.subscription_count, r.generated_at, r.data
  FROM subscription_earnings_reports r
  WHERE (p_teacher IS NULL OR r.teacher_id = p_teacher)
  ORDER BY r.year DESC, r.month DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION subscription_earnings_reports(UUID) TO authenticated;
