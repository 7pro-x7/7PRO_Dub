-- ============================================================================
-- Subscriptions system for Teacher accounts
-- ============================================================================

-- ============================================================================
-- 1. SUBSCRIPTIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS teacher_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  group_name TEXT NOT NULL,
  parent_name TEXT NOT NULL,
  student_name TEXT NOT NULL,
  start_date DATE NOT NULL,
  monthly_amount NUMERIC(10,2) NOT NULL CHECK (monthly_amount >= 0),
  currency TEXT NOT NULL DEFAULT 'EGP',
  next_renewal_date DATE NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DUE', 'OVERDUE', 'PAUSED')),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_teacher_subscriptions_teacher_id ON teacher_subscriptions(teacher_id);
CREATE INDEX idx_teacher_subscriptions_status ON teacher_subscriptions(status);
CREATE INDEX idx_teacher_subscriptions_next_renewal ON teacher_subscriptions(next_renewal_date);
CREATE INDEX idx_teacher_subscriptions_student ON teacher_subscriptions USING gin(to_tsvector('simple', student_name));
CREATE INDEX idx_teacher_subscriptions_parent ON teacher_subscriptions USING gin(to_tsvector('simple', parent_name));
CREATE INDEX idx_teacher_subscriptions_group ON teacher_subscriptions USING gin(to_tsvector('simple', group_name));
-- ============================================================================
-- 2. RENEWAL HISTORY TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS subscription_renewals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id UUID NOT NULL REFERENCES teacher_subscriptions(id) ON DELETE CASCADE,
  renewed_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE SET NULL,
  renewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  previous_renewal_date DATE NOT NULL,
  new_renewal_date DATE NOT NULL,
  amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  currency TEXT NOT NULL DEFAULT 'EGP',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscription_renewals_subscription_id ON subscription_renewals(subscription_id);
CREATE INDEX idx_subscription_renewals_renewed_at ON subscription_renewals(renewed_at DESC);
-- ============================================================================
-- 3. UPDATED_AT TRIGGER
-- ============================================================================
CREATE OR REPLACE FUNCTION update_teacher_subscriptions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS update_teacher_subscriptions_updated_at ON teacher_subscriptions;
CREATE TRIGGER update_teacher_subscriptions_updated_at
  BEFORE UPDATE ON teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION update_teacher_subscriptions_updated_at();
-- ============================================================================
-- 4. AUTO-UPDATE STATUS FUNCTION
--    Marks subscriptions as OVERDUE when next_renewal_date has passed,
--    and DUE when within 7 days of renewal.
-- ============================================================================
CREATE OR REPLACE FUNCTION refresh_subscription_statuses()
RETURNS VOID AS $$
BEGIN
  UPDATE teacher_subscriptions
  SET status = 'OVERDUE'
  WHERE status IN ('ACTIVE', 'DUE')
    AND next_renewal_date < current_date;

  UPDATE teacher_subscriptions
  SET status = 'DUE'
  WHERE status = 'ACTIVE'
    AND next_renewal_date >= current_date
    AND next_renewal_date <= current_date + INTERVAL '7 days';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
-- ============================================================================
-- 5. RLS POLICIES
-- ============================================================================
ALTER TABLE teacher_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_renewals ENABLE ROW LEVEL SECURITY;
-- Teachers can read their own subscriptions
CREATE POLICY "teachers_read_own_subscriptions"
  ON teacher_subscriptions FOR SELECT
  USING (auth.uid() = teacher_id);
-- Teachers can insert their own subscriptions
CREATE POLICY "teachers_insert_own_subscriptions"
  ON teacher_subscriptions FOR INSERT
  WITH CHECK (auth.uid() = teacher_id);
-- Teachers can update their own subscriptions
CREATE POLICY "teachers_update_own_subscriptions"
  ON teacher_subscriptions FOR UPDATE
  USING (auth.uid() = teacher_id);
-- Teachers can delete their own subscriptions
CREATE POLICY "teachers_delete_own_subscriptions"
  ON teacher_subscriptions FOR DELETE
  USING (auth.uid() = teacher_id);
-- Owner/Admin have full access
CREATE POLICY "staff_full_access_subscriptions"
  ON teacher_subscriptions FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- Renewal history: teachers read their own renewals
CREATE POLICY "teachers_read_own_renewals"
  ON subscription_renewals FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM teacher_subscriptions
      WHERE teacher_subscriptions.id = subscription_renewals.subscription_id
        AND teacher_subscriptions.teacher_id = auth.uid()
    )
  );
-- Teachers can insert renewals for their own subscriptions
CREATE POLICY "teachers_insert_own_renewals"
  ON subscription_renewals FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM teacher_subscriptions
      WHERE teacher_subscriptions.id = subscription_renewals.subscription_id
        AND teacher_subscriptions.teacher_id = auth.uid()
    )
  );
-- Staff full access to renewals
CREATE POLICY "staff_full_access_renewals"
  ON subscription_renewals FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- ============================================================================
-- 6. SUBSCRIPTION STATISTICS RPC
-- ============================================================================
CREATE OR REPLACE FUNCTION subscription_stats(p_teacher UUID DEFAULT NULL)
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
-- Grant access
GRANT EXECUTE ON FUNCTION subscription_stats(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION refresh_subscription_statuses() TO authenticated;
-- Grant table access to authenticated
GRANT SELECT, INSERT, UPDATE, DELETE ON teacher_subscriptions TO authenticated;
GRANT SELECT, INSERT ON subscription_renewals TO authenticated;
