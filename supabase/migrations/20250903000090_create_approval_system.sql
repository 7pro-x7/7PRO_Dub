-- ============================================================================
-- Subscription Approval System
--
-- Any group or subscription addition/deletion made by a teacher now requires
-- owner/admin approval before it affects earnings or becomes active.
-- ============================================================================

-- ============================================================================
-- 1. Add approval_status to teacher_subscriptions
--    Existing subscriptions default to APPROVED; new teacher-created ones start PENDING.
-- ============================================================================
ALTER TABLE public.teacher_subscriptions
  ADD COLUMN IF NOT EXISTS approval_status TEXT NOT NULL DEFAULT 'APPROVED'
  CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));
COMMENT ON COLUMN public.teacher_subscriptions.approval_status IS 'Approval workflow status: PENDING (awaiting owner), APPROVED (active), REJECTED (denied)';
-- Mark all existing subscriptions as approved
UPDATE public.teacher_subscriptions SET approval_status = 'APPROVED' WHERE approval_status IS NULL;
-- ============================================================================
-- 2. Create approval_requests table
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.approval_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  action_type TEXT NOT NULL CHECK (action_type IN ('ADD_SUBSCRIPTION', 'DELETE_SUBSCRIPTION', 'UPDATE_SUBSCRIPTION', 'ADD_GROUP', 'DELETE_GROUP', 'RENEW_SUBSCRIPTION')),
  target_type TEXT NOT NULL CHECK (target_type IN ('subscription', 'group', 'renewal')),
  target_id UUID,
  request_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  reviewed_by UUID REFERENCES auth.users(id),
  reviewed_at TIMESTAMPTZ,
  review_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_approval_requests_teacher ON public.approval_requests(teacher_id);
CREATE INDEX IF NOT EXISTS idx_approval_requests_status ON public.approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_approval_requests_created ON public.approval_requests(created_at DESC);
-- ============================================================================
-- 3. RLS on approval_requests
-- ============================================================================
ALTER TABLE public.approval_requests ENABLE ROW LEVEL SECURITY;
-- Teachers can read their own requests
CREATE POLICY "teachers_read_own_requests"
  ON public.approval_requests FOR SELECT
  USING (auth.uid() = teacher_id);
-- Teachers can insert their own requests
CREATE POLICY "teachers_insert_own_requests"
  ON public.approval_requests FOR INSERT
  WITH CHECK (auth.uid() = teacher_id);
-- Staff (owner/admin) can read all requests
CREATE POLICY "staff_read_all_requests"
  ON public.approval_requests FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- Staff can update all requests (to approve/reject)
CREATE POLICY "staff_update_all_requests"
  ON public.approval_requests FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM public.profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
GRANT SELECT, INSERT ON public.approval_requests TO authenticated;
GRANT UPDATE ON public.approval_requests TO authenticated;
-- ============================================================================
-- 4. RPC: Submit a subscription change request (teacher only)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.submit_subscription_request(
  p_action_type TEXT,
  p_target_type TEXT,
  p_target_id UUID DEFAULT NULL,
  p_request_data JSONB DEFAULT '{}'::jsonb
)
RETURNS UUID AS $$
DECLARE
  v_caller_role TEXT;
  v_request_id UUID;
BEGIN
  -- Verify caller is a teacher
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  IF v_caller_role IS DISTINCT FROM 'TEACHER' THEN
    RAISE EXCEPTION 'Only teachers can submit subscription requests';
  END IF;

  INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  VALUES (auth.uid(), p_action_type, p_target_type, p_target_id, p_request_data)
  RETURNING id INTO v_request_id;

  -- Log to audit
  INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
  VALUES (
    'SUBSCRIPTION_REQUEST_' || p_action_type,
    p_target_type,
    p_target_id,
    'TEACHER',
    jsonb_build_object('request_id', v_request_id, 'teacher_id', auth.uid())
  );

  RETURN v_request_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.submit_subscription_request(TEXT, TEXT, UUID, JSONB) TO authenticated;
-- ============================================================================
-- 5. RPC: Process (approve/reject) a request (owner/admin only)
--    On APPROVE: executes the actual database change.
--    On REJECT: just marks the request.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.process_approval_request(
  p_request_id UUID,
  p_decision TEXT,
  p_note TEXT DEFAULT ''
)
RETURNS VOID AS $$
DECLARE
  v_caller_role TEXT;
  v_request RECORD;
  v_sub_data JSONB;
  v_new_sub_id UUID;
  v_rate NUMERIC(5,2);
  v_teacher_earning NUMERIC(12,2);
  v_owner_earning NUMERIC(12,2);
  v_period_start DATE;
  v_period_end DATE;
  v_prev_date DATE;
  v_new_date DATE;
  v_renewal_id UUID;
BEGIN
  -- Verify caller is owner or admin
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  IF v_caller_role NOT IN ('OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'Only owner or admin can approve/reject requests';
  END IF;

  -- Fetch the request
  SELECT * INTO v_request FROM public.approval_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Request not found';
  END IF;

  IF v_request.status != 'PENDING' THEN
    RAISE EXCEPTION 'Request already processed';
  END IF;

  IF p_decision NOT IN ('APPROVED', 'REJECTED') THEN
    RAISE EXCEPTION 'Invalid decision: must be APPROVED or REJECTED';
  END IF;

  -- Update request status
  UPDATE public.approval_requests
  SET status = p_decision,
      reviewed_by = auth.uid(),
      reviewed_at = now(),
      review_note = p_note
  WHERE id = p_request_id;

  -- If REJECTED, log and return
  IF p_decision = 'REJECTED' THEN
    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES (
      'SUBSCRIPTION_REQUEST_REJECTED',
      v_request.target_type,
      v_request.target_id,
      v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'note', p_note)
    );
    RETURN;
  END IF;

  -- ════════════════════════════════════════════════════════════════════════
  -- APPROVED: Execute the actual change
  -- ════════════════════════════════════════════════════════════════════════

  IF v_request.action_type = 'ADD_SUBSCRIPTION' THEN
    -- Create the subscription with APPROVED status
    v_sub_data := v_request.request_data;
    INSERT INTO public.teacher_subscriptions (
      teacher_id, group_name, parent_name, student_name,
      start_date, monthly_amount, currency, next_renewal_date,
      status, level, parent_phone, notes, approval_status
    ) VALUES (
      v_request.teacher_id,
      (v_sub_data->>'group_name'),
      (v_sub_data->>'parent_name'),
      (v_sub_data->>'student_name'),
      (v_sub_data->>'start_date')::DATE,
      (v_sub_data->>'monthly_amount')::NUMERIC,
      COALESCE(v_sub_data->>'currency', 'EGP'),
      (v_sub_data->>'next_renewal_date')::DATE,
      COALESCE(v_sub_data->>'status', 'ACTIVE'),
      v_sub_data->>'level',
      v_sub_data->>'parent_phone',
      v_sub_data->>'notes',
      'APPROVED'
    ) RETURNING id INTO v_new_sub_id;

    -- Record initial earning (same logic as the trigger, but we bypass trigger since status was PENDING)
    SELECT COALESCE(tsr.percentage, 70) INTO v_rate
    FROM public.teacher_subscription_rates tsr
    WHERE tsr.teacher_id = v_request.teacher_id;
    v_teacher_earning := ROUND((v_sub_data->>'monthly_amount')::NUMERIC * v_rate / 100, 2);
    v_owner_earning := (v_sub_data->>'monthly_amount')::NUMERIC - v_teacher_earning;
    v_period_start := date_trunc('month', current_date)::date;
    v_period_end := (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date;

    INSERT INTO public.subscription_earnings (
      teacher_id, subscription_id, renewal_id, group_name,
      monthly_amount, currency, teacher_percentage,
      teacher_earning, owner_earning, period_start, period_end
    ) VALUES (
      v_request.teacher_id, v_new_sub_id, NULL,
      (v_sub_data->>'group_name'),
      (v_sub_data->>'monthly_amount')::NUMERIC,
      COALESCE(v_sub_data->>'currency', 'EGP'),
      COALESCE(v_rate, 70), v_teacher_earning, v_owner_earning,
      v_period_start, v_period_end
    );

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES ('SUBSCRIPTION_APPROVED', 'subscription', v_new_sub_id, v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));

  ELSIF v_request.action_type = 'DELETE_SUBSCRIPTION' THEN
    -- Delete the subscription
    DELETE FROM public.teacher_subscriptions WHERE id = v_request.target_id;

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES ('SUBSCRIPTION_DELETE_APPROVED', 'subscription', v_request.target_id, v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));

  ELSIF v_request.action_type = 'UPDATE_SUBSCRIPTION' THEN
    -- Update subscription fields from request_data
    v_sub_data := v_request.request_data;
    UPDATE public.teacher_subscriptions
    SET group_name = COALESCE(v_sub_data->>'group_name', group_name),
        parent_name = COALESCE(v_sub_data->>'parent_name', parent_name),
        student_name = COALESCE(v_sub_data->>'student_name', student_name),
        start_date = COALESCE((v_sub_data->>'start_date')::DATE, start_date),
        monthly_amount = COALESCE((v_sub_data->>'monthly_amount')::NUMERIC, monthly_amount),
        currency = COALESCE(v_sub_data->>'currency', currency),
        next_renewal_date = COALESCE((v_sub_data->>'next_renewal_date')::DATE, next_renewal_date),
        status = COALESCE(v_sub_data->>'status', status),
        level = COALESCE(v_sub_data->>'level', level),
        parent_phone = COALESCE(v_sub_data->>'parent_phone', parent_phone),
        notes = COALESCE(v_sub_data->>'notes', notes),
        approval_status = 'APPROVED'
    WHERE id = v_request.target_id;

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES ('SUBSCRIPTION_UPDATE_APPROVED', 'subscription', v_request.target_id, v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));

  ELSIF v_request.action_type = 'ADD_GROUP' THEN
    -- Create the group
    INSERT INTO public.teacher_groups (teacher_id, name, level)
    VALUES (v_request.teacher_id, (v_request.request_data->>'name'), (v_request.request_data->>'level'))
    ON CONFLICT (teacher_id, name) DO NOTHING;

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES ('GROUP_APPROVED', 'group', NULL, v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'name', v_request.request_data->>'name'));

  ELSIF v_request.action_type = 'DELETE_GROUP' THEN
    -- Delete group and its subscriptions
    DELETE FROM public.teacher_subscriptions
    WHERE teacher_id = v_request.teacher_id
      AND group_name = (v_request.request_data->>'group_name');
    DELETE FROM public.teacher_groups WHERE id = v_request.target_id;

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES ('GROUP_DELETE_APPROVED', 'group', v_request.target_id, v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));

  ELSIF v_request.action_type = 'RENEW_SUBSCRIPTION' THEN
    -- Process the renewal: update renewal date + record earnings + ledger
    v_sub_data := v_request.request_data;
    v_prev_date := (v_sub_data->>'previous_renewal_date')::DATE;
    v_new_date := (v_sub_data->>'new_renewal_date')::DATE;

    -- Record renewal history
    INSERT INTO public.subscription_renewals (
      subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
    ) VALUES (
      v_request.target_id, auth.uid(), v_prev_date, v_new_date,
      (v_sub_data->>'amount')::NUMERIC, COALESCE(v_sub_data->>'currency', 'EGP')
    ) RETURNING id INTO v_renewal_id;

    -- Record earnings
    SELECT COALESCE(tsr.percentage, 70) INTO v_rate
    FROM public.teacher_subscription_rates tsr
    WHERE tsr.teacher_id = v_request.teacher_id;
    v_teacher_earning := ROUND((v_sub_data->>'amount')::NUMERIC * v_rate / 100, 2);
    v_owner_earning := (v_sub_data->>'amount')::NUMERIC - v_teacher_earning;
    v_period_start := date_trunc('month', current_date)::date;
    v_period_end := (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date;

    INSERT INTO public.subscription_earnings (
      teacher_id, subscription_id, renewal_id, group_name,
      monthly_amount, currency, teacher_percentage,
      teacher_earning, owner_earning, period_start, period_end
    ) SELECT
      ts.teacher_id, v_request.target_id, v_renewal_id, ts.group_name,
      (v_sub_data->>'amount')::NUMERIC, COALESCE(v_sub_data->>'currency', 'EGP'),
      COALESCE(v_rate, 70), v_teacher_earning, v_owner_earning,
      v_period_start, v_period_end
    FROM public.teacher_subscriptions ts WHERE ts.id = v_request.target_id;

    -- Record ledger entry
    INSERT INTO public.teacher_ledger (teacher_id, kind, amount, currency, status, note)
    SELECT ts.teacher_id, 'CREDIT', (v_sub_data->>'amount')::NUMERIC,
           COALESCE(v_sub_data->>'currency', 'EGP'), 'AVAILABLE',
           'Subscription renewal: ' || ts.group_name || ' - ' || ts.student_name
    FROM public.teacher_subscriptions ts WHERE ts.id = v_request.target_id;

    -- Update subscription
    UPDATE public.teacher_subscriptions
    SET next_renewal_date = v_new_date, status = 'ACTIVE'
    WHERE id = v_request.target_id;

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES ('RENEWAL_APPROVED', 'subscription', v_request.target_id, v_caller_role,
      jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id,
        'amount', v_sub_data->>'amount', 'new_date', v_new_date));
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.process_approval_request(UUID, TEXT, TEXT) TO authenticated;
-- ============================================================================
-- 6. RPC: List pending requests (owner/admin)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.list_approval_requests(
  p_status TEXT DEFAULT 'PENDING',
  p_teacher UUID DEFAULT NULL
)
RETURNS TABLE (
  id UUID,
  teacher_id UUID,
  action_type TEXT,
  target_type TEXT,
  target_id UUID,
  request_data JSONB,
  status TEXT,
  reviewed_by UUID,
  reviewed_at TIMESTAMPTZ,
  review_note TEXT,
  created_at TIMESTAMPTZ,
  teacher_name TEXT,
  teacher_avatar TEXT
) AS $$
BEGIN
  RETURN QUERY
  SELECT ar.id, ar.teacher_id, ar.action_type, ar.target_type, ar.target_id,
         ar.request_data, ar.status, ar.reviewed_by, ar.reviewed_at,
         ar.review_note, ar.created_at,
         p.full_name AS teacher_name, p.avatar_url AS teacher_avatar
  FROM public.approval_requests ar
  LEFT JOIN public.profiles p ON p.id = ar.teacher_id
  WHERE (p_status IS NULL OR ar.status = p_status)
    AND (p_teacher IS NULL OR ar.teacher_id = p_teacher)
  ORDER BY ar.created_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.list_approval_requests(TEXT, UUID) TO authenticated;
-- ============================================================================
-- 7. RPC: Get pending request count per teacher (owner dashboard)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.pending_request_counts()
RETURNS TABLE (
  teacher_id UUID,
  teacher_name TEXT,
  teacher_avatar TEXT,
  pending_count BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT ar.teacher_id,
         p.full_name AS teacher_name,
         p.avatar_url AS teacher_avatar,
         count(*) AS pending_count
  FROM public.approval_requests ar
  LEFT JOIN public.profiles p ON p.id = ar.teacher_id
  WHERE ar.status = 'PENDING'
  GROUP BY ar.teacher_id, p.full_name, p.avatar_url
  ORDER BY pending_count DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.pending_request_counts() TO authenticated;
-- ============================================================================
-- 8. Fix trigger: only fire earning trigger for APPROVED subscriptions
--    When a teacher creates a subscription via submit_subscription_request,
--    the subscription is inserted as PENDING and the trigger should NOT fire.
--    The trigger only fires when the approval RPC inserts with APPROVED status.
-- ============================================================================

-- Update the trigger condition to require approval_status = 'APPROVED'
DROP TRIGGER IF EXISTS trg_record_initial_earning ON public.teacher_subscriptions;
CREATE TRIGGER trg_record_initial_earning
  AFTER INSERT ON public.teacher_subscriptions
  FOR EACH ROW
  WHEN (NEW.status != 'PAUSED' AND NEW.approval_status = 'APPROVED')
  EXECUTE FUNCTION public.record_initial_subscription_earning();
-- ============================================================================
-- 9. Update subscription_stats to exclude PENDING subscriptions from earnings
-- ============================================================================
CREATE OR REPLACE FUNCTION public.subscription_stats(p_teacher UUID DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
  total_count INT;
  due_this_week INT;
  overdue_count INT;
  total_monthly NUMERIC(12,2);
BEGIN
  PERFORM refresh_subscription_statuses();

  SELECT count(*), count(*) FILTER (WHERE status = 'DUE'), count(*) FILTER (WHERE status = 'OVERDUE'), COALESCE(sum(monthly_amount), 0)
  INTO total_count, due_this_week, overdue_count, total_monthly
  FROM teacher_subscriptions
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND status != 'PAUSED'
    AND approval_status = 'APPROVED';

  due_this_week := (SELECT count(*) FROM teacher_subscriptions
    WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
      AND status IN ('ACTIVE', 'DUE')
      AND approval_status = 'APPROVED'
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
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.subscription_stats(UUID) TO authenticated;
-- ============================================================================
-- 10. Block teachers from directly modifying subscriptions (enforce approval)
-- ============================================================================
-- Drop teacher's direct write policies on teacher_subscriptions
DROP POLICY IF EXISTS teachers_insert_own_subscriptions ON public.teacher_subscriptions;
DROP POLICY IF EXISTS teachers_update_own_subscriptions ON public.teacher_subscriptions;
DROP POLICY IF EXISTS teachers_delete_own_subscriptions ON public.teacher_subscriptions;
-- Keep teacher's read-only access
-- (teachers_read_own_subscriptions policy already exists from original migration)

-- Teachers can now ONLY read their own subscriptions
-- All writes must go through submit_subscription_request + process_approval_request

-- Also restrict teacher_groups direct writes for teachers
DROP POLICY IF EXISTS teacher_groups_self_access ON public.teacher_groups;
-- Teacher can only READ their own groups
CREATE POLICY "teacher_groups_self_read"
  ON public.teacher_groups FOR SELECT
  USING (auth.uid() = teacher_id);
-- Teacher inserts for groups go through approval request
-- (teacher_groups still has staff_full_access for OWNER/ADMIN)

-- ============================================================================
-- 11. Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
