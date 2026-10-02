-- ============================================================================
-- CLEAN SUBSCRIPTION SYSTEM REBUILD (v2 — fixes expire_subscriptions return type)
-- ============================================================================

DROP TRIGGER IF EXISTS trg_record_initial_earning ON public.teacher_subscriptions;
DROP TRIGGER IF EXISTS trg_approve_initial_earning ON public.teacher_subscriptions;
DROP TRIGGER IF EXISTS trg_sync_subscription_approval_from_group ON public.teacher_subscriptions;
DROP TRIGGER IF EXISTS trg_record_renewal_ledger ON public.subscription_renewals;
DROP TRIGGER IF EXISTS trg_on_subscription_approved ON public.teacher_subscriptions;
DROP TRIGGER IF EXISTS trg_on_subscription_renewed ON public.subscription_renewals;

DROP FUNCTION IF EXISTS public.record_initial_subscription_earning() CASCADE;
DROP FUNCTION IF EXISTS public.record_approved_subscription_earning() CASCADE;
DROP FUNCTION IF EXISTS public.record_subscription_ledger_entry() CASCADE;
DROP FUNCTION IF EXISTS public.sync_subscription_approval_from_group() CASCADE;
DROP FUNCTION IF EXISTS public.submit_subscription_request(TEXT, TEXT, UUID, JSONB) CASCADE;
DROP FUNCTION IF EXISTS public.activate_subscription(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.reject_subscription(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.reject_subscription(UUID, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.renew_subscription(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.refresh_subscription_statuses() CASCADE;
DROP FUNCTION IF EXISTS public.subscription_stats(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.subscription_stats_all() CASCADE;
DROP FUNCTION IF EXISTS public.subscription_earnings_summary(UUID, DATE, DATE) CASCADE;
DROP FUNCTION IF EXISTS public.subscription_earnings_reports(UUID, INT) CASCADE;
DROP FUNCTION IF EXISTS public.generate_subscription_monthly_report(UUID, INT, INT) CASCADE;
DROP FUNCTION IF EXISTS public.generate_subscription_monthly_report_v2(UUID, INT, INT) CASCADE;
DROP FUNCTION IF EXISTS public.set_teacher_subscription_rate(UUID, NUMERIC) CASCADE;
DROP FUNCTION IF EXISTS public.get_teacher_subscription_rate(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.expire_subscriptions() CASCADE;

ALTER TABLE public.teacher_subscriptions
  ADD COLUMN IF NOT EXISTS approval_status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
  ADD COLUMN IF NOT EXISTS reviewed_by UUID REFERENCES auth.users(id),
  ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS review_note TEXT,
  ADD COLUMN IF NOT EXISTS level TEXT,
  ADD COLUMN IF NOT EXISTS parent_phone TEXT;

UPDATE public.teacher_subscriptions
SET approval_status = 'APPROVED'
WHERE reviewed_at IS NULL AND created_at < now();

DROP POLICY IF EXISTS "teachers_read_own_subscriptions" ON public.teacher_subscriptions;
DROP POLICY IF EXISTS "teachers_insert_own_subscriptions" ON public.teacher_subscriptions;
DROP POLICY IF EXISTS "teachers_update_own_subscriptions" ON public.teacher_subscriptions;
DROP POLICY IF EXISTS "teachers_delete_own_subscriptions" ON public.teacher_subscriptions;
DROP POLICY IF EXISTS "staff_full_access_subscriptions" ON public.teacher_subscriptions;

CREATE POLICY "teachers_read_own_subscriptions"
  ON public.teacher_subscriptions FOR SELECT
  USING (auth.uid() = teacher_id);

CREATE POLICY "teachers_insert_own_subscriptions"
  ON public.teacher_subscriptions FOR INSERT
  WITH CHECK (
    auth.uid() = teacher_id
    AND approval_status = 'PENDING'
  );

CREATE POLICY "staff_full_access_subscriptions"
  ON public.teacher_subscriptions FOR ALL
  USING (
    EXISTS (SELECT 1 FROM public.profiles WHERE profiles.id = auth.uid() AND profiles.role IN ('OWNER', 'ADMIN'))
  )
  WITH CHECK (
    EXISTS (SELECT 1 FROM public.profiles WHERE profiles.id = auth.uid() AND profiles.role IN ('OWNER', 'ADMIN'))
  );

DROP POLICY IF EXISTS "teachers_read_own_renewals" ON public.subscription_renewals;
DROP POLICY IF EXISTS "teachers_insert_own_renewals" ON public.subscription_renewals;
DROP POLICY IF EXISTS "staff_full_access_renewals" ON public.subscription_renewals;

CREATE POLICY "teachers_read_own_renewals"
  ON public.subscription_renewals FOR SELECT
  USING (
    EXISTS (SELECT 1 FROM public.teacher_subscriptions s WHERE s.id = subscription_renewals.subscription_id AND s.teacher_id = auth.uid())
  );

CREATE POLICY "staff_full_access_renewals"
  ON public.subscription_renewals FOR ALL
  USING (
    EXISTS (SELECT 1 FROM public.profiles WHERE profiles.id = auth.uid() AND profiles.role IN ('OWNER', 'ADMIN'))
  );

-- Returns the number of rows transitioned (OVERDUE + DUE), preserving the
-- integer return type the maintenance edge function relies on.
CREATE OR REPLACE FUNCTION public.refresh_subscription_statuses()
RETURNS INTEGER AS $$
DECLARE
  v_count INTEGER := 0;
  v_n INTEGER;
BEGIN
  UPDATE public.teacher_subscriptions
  SET status = 'OVERDUE'
  WHERE approval_status = 'APPROVED'
    AND status IN ('ACTIVE', 'DUE')
    AND next_renewal_date < current_date;
  GET DIAGNOSTICS v_n = ROW_COUNT;
  v_count := v_count + v_n;

  UPDATE public.teacher_subscriptions
  SET status = 'DUE'
  WHERE approval_status = 'APPROVED'
    AND status = 'ACTIVE'
    AND next_renewal_date BETWEEN current_date AND current_date + INTERVAL '7 days';
  GET DIAGNOSTICS v_n = ROW_COUNT;
  v_count := v_count + v_n;

  RETURN v_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.subscription_stats(p_teacher UUID DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
BEGIN
  PERFORM public.refresh_subscription_statuses();

  SELECT jsonb_build_object(
    'total_subscriptions', count(*),
    'due_this_week', count(*) FILTER (WHERE status IN ('ACTIVE', 'DUE') AND next_renewal_date <= current_date + INTERVAL '7 days'),
    'overdue', count(*) FILTER (WHERE status = 'OVERDUE'),
    'pending_approval', count(*) FILTER (WHERE approval_status = 'PENDING'),
    'total_monthly_value', COALESCE(sum(monthly_amount) FILTER (WHERE status != 'PAUSED'), 0)
  ) INTO result
  FROM public.teacher_subscriptions
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND approval_status = 'APPROVED';

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.subscription_stats_all()
RETURNS JSONB AS $$
BEGIN
  RETURN public.subscription_stats(NULL);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.create_subscription_request(
  p_group_name TEXT,
  p_parent_name TEXT,
  p_student_name TEXT,
  p_start_date DATE,
  p_monthly_amount NUMERIC,
  p_next_renewal_date DATE,
  p_level TEXT DEFAULT NULL,
  p_parent_phone TEXT DEFAULT NULL,
  p_notes TEXT DEFAULT NULL,
  p_currency TEXT DEFAULT 'EGP'
)
RETURNS UUID AS $$
DECLARE
  v_role TEXT;
  v_id UUID;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  IF v_role IS DISTINCT FROM 'TEACHER' THEN
    RAISE EXCEPTION 'Only teachers can submit subscription requests';
  END IF;

  IF p_monthly_amount < 0 THEN
    RAISE EXCEPTION 'monthly_amount must be >= 0';
  END IF;

  INSERT INTO public.teacher_subscriptions (
    teacher_id, group_name, parent_name, student_name, start_date,
    monthly_amount, currency, next_renewal_date, status, approval_status,
    level, parent_phone, notes
  ) VALUES (
    auth.uid(), p_group_name, p_parent_name, p_student_name, p_start_date,
    p_monthly_amount, p_currency, p_next_renewal_date, 'ACTIVE', 'PENDING',
    p_level, p_parent_phone, p_notes
  ) RETURNING id INTO v_id;

  RETURN v_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.create_subscription_request(TEXT, TEXT, TEXT, DATE, NUMERIC, DATE, TEXT, TEXT, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.set_teacher_subscription_rate(p_teacher UUID, p_percentage NUMERIC)
RETURNS VOID AS $$
DECLARE
  v_role TEXT;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  IF v_role != 'OWNER' THEN
    RAISE EXCEPTION 'Only the owner can set a teacher''s subscription rate';
  END IF;
  IF p_percentage < 0 OR p_percentage > 100 THEN
    RAISE EXCEPTION 'percentage must be between 0 and 100';
  END IF;

  INSERT INTO public.teacher_subscription_rates (teacher_id, percentage, set_by)
  VALUES (p_teacher, p_percentage, auth.uid())
  ON CONFLICT (teacher_id) DO UPDATE
    SET percentage = EXCLUDED.percentage, set_by = EXCLUDED.set_by, updated_at = now();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.set_teacher_subscription_rate(UUID, NUMERIC) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_teacher_subscription_rate(p_teacher UUID)
RETURNS NUMERIC AS $$
  SELECT COALESCE((SELECT percentage FROM public.teacher_subscription_rates WHERE teacher_id = p_teacher), 0);
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.get_teacher_subscription_rate(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION public._record_subscription_earning(
  p_subscription_id UUID,
  p_renewal_id UUID,
  p_period_start DATE,
  p_period_end DATE,
  p_note TEXT
) RETURNS VOID AS $$
DECLARE
  v_sub RECORD;
  v_rate NUMERIC;
  v_teacher_share NUMERIC;
  v_owner_share NUMERIC;
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
  );

  INSERT INTO public.teacher_ledger (teacher_id, kind, amount, currency, status, note)
  VALUES (v_sub.teacher_id, 'CREDIT', v_teacher_share, v_sub.currency, 'AVAILABLE', p_note);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public._on_subscription_approved()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.approval_status = 'APPROVED' AND OLD.approval_status IS DISTINCT FROM 'APPROVED' THEN
    PERFORM public._record_subscription_earning(
      NEW.id, NULL, NEW.start_date, NEW.next_renewal_date,
      'Subscription approved: ' || NEW.group_name || ' - ' || NEW.student_name
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE TRIGGER trg_on_subscription_approved
  AFTER UPDATE ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public._on_subscription_approved();

CREATE OR REPLACE FUNCTION public._on_subscription_renewed()
RETURNS TRIGGER AS $$
BEGIN
  PERFORM public._record_subscription_earning(
    NEW.subscription_id, NEW.id, NEW.previous_renewal_date, NEW.new_renewal_date,
    'Subscription renewal'
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE TRIGGER trg_on_subscription_renewed
  AFTER INSERT ON public.subscription_renewals
  FOR EACH ROW
  EXECUTE FUNCTION public._on_subscription_renewed();

CREATE OR REPLACE FUNCTION public._assert_staff()
RETURNS VOID AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role IN ('OWNER', 'ADMIN')) THEN
    RAISE EXCEPTION 'Only owner or admin can perform this action';
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.activate_subscription(p_subscription_id UUID)
RETURNS VOID AS $$
DECLARE
  v_role TEXT;
  v_sub RECORD;
BEGIN
  SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
  IF v_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;

  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = p_subscription_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SUBSCRIPTION_NOT_FOUND';
  END IF;

  IF v_role IN ('OWNER', 'ADMIN') THEN
    UPDATE public.teacher_subscriptions
    SET approval_status = 'APPROVED', reviewed_by = auth.uid(), reviewed_at = now()
    WHERE id = p_subscription_id AND approval_status != 'APPROVED';
  ELSIF v_sub.teacher_id = auth.uid() THEN
    UPDATE public.teacher_subscriptions
    SET approval_status = 'PENDING', reviewed_by = NULL, reviewed_at = NULL, review_note = NULL
    WHERE id = p_subscription_id AND approval_status = 'REJECTED';
  ELSE
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.activate_subscription(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION public.reject_subscription(p_subscription_id UUID, p_note TEXT DEFAULT '')
RETURNS VOID AS $$
BEGIN
  PERFORM public._assert_staff();

  UPDATE public.teacher_subscriptions
  SET approval_status = 'REJECTED', reviewed_by = auth.uid(), reviewed_at = now(), review_note = p_note
  WHERE id = p_subscription_id AND approval_status = 'PENDING';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Subscription not found or already reviewed';
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.reject_subscription(UUID, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.renew_subscription(p_subscription_id UUID)
RETURNS VOID AS $$
DECLARE
  v_sub RECORD;
  v_new_date DATE;
BEGIN
  PERFORM public._assert_staff();

  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = p_subscription_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Subscription not found';
  END IF;
  IF v_sub.approval_status != 'APPROVED' THEN
    RAISE EXCEPTION 'Only an approved subscription can be renewed';
  END IF;

  v_new_date := v_sub.next_renewal_date + INTERVAL '1 month';

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

GRANT EXECUTE ON FUNCTION public.renew_subscription(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION public.set_subscription_paused(p_subscription_id UUID, p_paused BOOLEAN)
RETURNS VOID AS $$
BEGIN
  PERFORM public._assert_staff();

  UPDATE public.teacher_subscriptions
  SET status = CASE WHEN p_paused THEN 'PAUSED' ELSE 'ACTIVE' END
  WHERE id = p_subscription_id AND approval_status = 'APPROVED';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.set_subscription_paused(UUID, BOOLEAN) TO authenticated;

CREATE OR REPLACE FUNCTION public.subscription_earnings_summary(
  p_teacher UUID DEFAULT NULL,
  p_period_start DATE DEFAULT NULL,
  p_period_end DATE DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
  groups JSONB;
BEGIN
  SELECT COALESCE(jsonb_agg(g), '[]'::jsonb) INTO groups FROM (
    SELECT group_name,
           sum(teacher_earning + owner_earning) AS total,
           sum(teacher_earning) AS teacher_share,
           sum(owner_earning) AS owner_share,
           count(*) AS count
    FROM public.subscription_earnings
    WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
      AND (p_period_start IS NULL OR period_start >= p_period_start)
      AND (p_period_end IS NULL OR period_end <= p_period_end)
    GROUP BY group_name
    ORDER BY group_name
  ) g;

  SELECT jsonb_build_object(
    'period_start', p_period_start,
    'period_end', p_period_end,
    'total_earned', COALESCE(sum(teacher_earning + owner_earning), 0),
    'teacher_share', COALESCE(sum(teacher_earning), 0),
    'owner_share', COALESCE(sum(owner_earning), 0),
    'subscription_count', count(DISTINCT subscription_id),
    'groups', groups
  ) INTO result
  FROM public.subscription_earnings
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND (p_period_start IS NULL OR period_start >= p_period_start)
    AND (p_period_end IS NULL OR period_end <= p_period_end);

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.subscription_earnings_summary(UUID, DATE, DATE) TO authenticated;

CREATE OR REPLACE FUNCTION public.generate_subscription_monthly_report(p_teacher UUID, p_year INT, p_month INT)
RETURNS UUID AS $$
DECLARE
  v_start DATE := make_date(p_year, p_month, 1);
  v_end DATE := (make_date(p_year, p_month, 1) + INTERVAL '1 month - 1 day')::DATE;
  v_summary JSONB;
  v_id UUID;
BEGIN
  v_summary := public.subscription_earnings_summary(p_teacher, v_start, v_end);

  INSERT INTO public.subscription_earnings_reports (
    teacher_id, year, month, total_earned, owner_share, currency, subscription_count, data
  ) VALUES (
    p_teacher, p_year, p_month,
    COALESCE((v_summary->>'total_earned')::NUMERIC, 0),
    COALESCE((v_summary->>'owner_share')::NUMERIC, 0),
    'EGP',
    COALESCE((v_summary->>'subscription_count')::INT, 0),
    v_summary
  )
  ON CONFLICT (teacher_id, year, month) DO UPDATE
    SET total_earned = EXCLUDED.total_earned,
        owner_share = EXCLUDED.owner_share,
        subscription_count = EXCLUDED.subscription_count,
        data = EXCLUDED.data,
        generated_at = now()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.generate_subscription_monthly_report(UUID, INT, INT) TO authenticated;

-- Preserves original INTEGER return type used by backend/functions/maintenance.
CREATE OR REPLACE FUNCTION public.expire_subscriptions()
RETURNS INTEGER AS $$
BEGIN
  RETURN public.refresh_subscription_statuses();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.expire_subscriptions() TO authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.refresh_subscription_statuses() TO authenticated;
GRANT EXECUTE ON FUNCTION public.subscription_stats(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.subscription_stats_all() TO authenticated;
;
