-- ============================================================================
-- Approval System — items appear in place with a visible status
--
-- PROBLEM:
--   When a teacher added a group or subscription, only an approval_requests
--   row was created. The real row in teacher_groups / teacher_subscriptions
--   did not exist until the owner/admin approved, so the teacher could not see
--   the item in its natural place (groups list / subscribers list) and needed
--   a separate "my requests" screen to know what was pending.
--
-- NEW BEHAVIOR:
--   * submit_subscription_request (ADD_GROUP / ADD_SUBSCRIPTION) now creates
--     the real row immediately with approval_status = 'PENDING'. The teacher
--     sees it in place with a status badge, but it is excluded from earnings,
--     stats, and financial accounts until an owner/admin approves it.
--   * On APPROVE the existing row's approval_status flips to 'APPROVED' and a
--     trigger records the initial earning (same logic as the INSERT trigger).
--   * On REJECT the row's approval_status flips to 'REJECTED' — it stays
--     visible with a red status and never enters the financial accounts.
--   * Owner/Admin additions are inserted directly as 'APPROVED' (they are the
--     approvers, no self-approval needed).
--   * DELETE / UPDATE / RENEW actions still require approval before applying.
--
-- The earnings protection from the previous migrations is preserved:
--   subscription_earnings, teacher_ledger, subscription_stats and
--   refresh_subscription_statuses only ever see APPROVED rows.
-- ============================================================================

-- ============================================================================
-- 1. teacher_groups gets the same approval workflow column as subscriptions
--    Existing groups (created before the approval system) default to APPROVED.
-- ============================================================================
ALTER TABLE public.teacher_groups
  ADD COLUMN IF NOT EXISTS approval_status TEXT NOT NULL DEFAULT 'APPROVED'
  CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));
COMMENT ON COLUMN public.teacher_groups.approval_status IS 'Approval workflow status: PENDING (awaiting owner), APPROVED (active), REJECTED (denied)';
-- ============================================================================
-- 2. Record the initial earning when a subscription flips PENDING -> APPROVED
--    The INSERT trigger (trg_record_initial_earning) only fires when the row
--    is created as APPROVED. Rows created as PENDING need this UPDATE trigger
--    so approving them records the earning exactly once.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.record_approved_subscription_earning()
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
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
DROP TRIGGER IF EXISTS trg_approve_initial_earning ON public.teacher_subscriptions;
CREATE TRIGGER trg_approve_initial_earning
  AFTER UPDATE OF approval_status ON public.teacher_subscriptions
  FOR EACH ROW
  WHEN (NEW.approval_status = 'APPROVED' AND OLD.approval_status = 'PENDING' AND NEW.status != 'PAUSED')
  EXECUTE FUNCTION public.record_approved_subscription_earning();
-- ============================================================================
-- 3. submit_subscription_request — create ADD items in place as PENDING
--    (OWNER/ADMIN additions are created directly as APPROVED).
-- ============================================================================
CREATE OR REPLACE FUNCTION public.submit_subscription_request(
  p_action_type TEXT,
  p_target_type TEXT,
  p_target_id UUID DEFAULT NULL,
  p_request_data JSONB DEFAULT '{}'::jsonb
)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_is_staff BOOLEAN;
  v_request_id UUID;
  v_target_id UUID;
BEGIN
  -- Get caller role from profiles
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();

  IF v_caller_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;

  IF v_caller_role NOT IN ('TEACHER', 'ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'Only teachers, admins, or owners can submit subscription requests';
  END IF;

  -- Validate action_type
  IF p_action_type NOT IN ('ADD_GROUP', 'DELETE_GROUP', 'ADD_SUBSCRIPTION', 'DELETE_SUBSCRIPTION', 'UPDATE_SUBSCRIPTION', 'RENEW_SUBSCRIPTION') THEN
    RAISE EXCEPTION 'Invalid action type: %', p_action_type;
  END IF;

  v_is_staff := v_caller_role IN ('ADMIN', 'OWNER');

  -- ────────────────────────────────────────────────────────────────────────
  -- ADD_GROUP: create the group immediately.
  --   Teacher:  row created as PENDING + approval request created.
  --   Staff:    row created as APPROVED (owner/admin are the approvers).
  -- ────────────────────────────────────────────────────────────────────────
  IF p_action_type = 'ADD_GROUP' THEN
    IF NOT v_is_staff THEN
      IF EXISTS (
        SELECT 1 FROM public.teacher_groups
        WHERE teacher_id = auth.uid()
          AND lower(name) = lower(COALESCE(p_request_data->>'name', ''))
      ) THEN
        RAISE EXCEPTION 'GROUP_ALREADY_EXISTS';
      END IF;

      INSERT INTO public.teacher_groups (teacher_id, name, level, approval_status)
      VALUES (
        auth.uid(),
        COALESCE(NULLIF(p_request_data->>'name', ''), ''),
        NULLIF(p_request_data->>'level', ''),
        'PENDING'
      )
      RETURNING id INTO v_target_id;

      INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
      VALUES (auth.uid(), p_action_type, p_target_type, v_target_id, p_request_data)
      RETURNING id INTO v_request_id;
    ELSE
      INSERT INTO public.teacher_groups (teacher_id, name, level, approval_status)
      VALUES (
        auth.uid(),
        COALESCE(NULLIF(p_request_data->>'name', ''), ''),
        NULLIF(p_request_data->>'level', ''),
        'APPROVED'
      )
      ON CONFLICT (teacher_id, name) DO UPDATE SET
        level = COALESCE(EXCLUDED.level, teacher_groups.level),
        approval_status = 'APPROVED',
        updated_at = now();
    END IF;

  -- ────────────────────────────────────────────────────────────────────────
  -- ADD_SUBSCRIPTION: create the subscription immediately.
  --   Teacher:  row created as PENDING + approval request created.
  --   Staff:    row created as APPROVED (INSERT trigger records the earning).
  -- ────────────────────────────────────────────────────────────────────────
  ELSIF p_action_type = 'ADD_SUBSCRIPTION' THEN
    INSERT INTO public.teacher_subscriptions (
      teacher_id, group_name, parent_name, student_name,
      start_date, monthly_amount, currency, next_renewal_date,
      status, level, parent_phone, notes, approval_status
    ) VALUES (
      auth.uid(),
      p_request_data->>'group_name',
      p_request_data->>'parent_name',
      p_request_data->>'student_name',
      (p_request_data->>'start_date')::DATE,
      (p_request_data->>'monthly_amount')::NUMERIC,
      COALESCE(p_request_data->>'currency', 'EGP'),
      (p_request_data->>'next_renewal_date')::DATE,
      COALESCE(p_request_data->>'status', 'ACTIVE'),
      p_request_data->>'level',
      p_request_data->>'parent_phone',
      p_request_data->>'notes',
      CASE WHEN v_is_staff THEN 'APPROVED' ELSE 'PENDING' END
    ) RETURNING id INTO v_target_id;

    IF NOT v_is_staff THEN
      INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
      VALUES (auth.uid(), p_action_type, p_target_type, v_target_id, p_request_data)
      RETURNING id INTO v_request_id;
    END IF;

  -- ────────────────────────────────────────────────────────────────────────
  -- DELETE / UPDATE / RENEW: request only — applied by the owner/admin.
  -- ────────────────────────────────────────────────────────────────────────
  ELSE
    INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
    VALUES (auth.uid(), p_action_type, p_target_type, p_target_id, p_request_data)
    RETURNING id INTO v_request_id;
  END IF;

  -- Log to audit — wrapped in EXCEPTION so audit failure never rolls back the request
  BEGIN
    INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    VALUES (
      auth.uid(),
      'SUBSCRIPTION_REQUEST_' || p_action_type,
      p_target_type,
      COALESCE(v_target_id, p_target_id),
      v_caller_role::public.app_role,
      jsonb_build_object('request_id', v_request_id, 'teacher_id', auth.uid())
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.submit_subscription_request(TEXT, TEXT, UUID, JSONB) TO authenticated;
-- ============================================================================
-- 4. process_approval_request — approve/reject now acts on the in-place row
--    APPROVE of ADD_GROUP / ADD_SUBSCRIPTION flips the existing PENDING row
--    to APPROVED (falling back to creating it for legacy requests that were
--    submitted before this migration). REJECT marks the existing row REJECTED
--    so the teacher still sees it with the red status.
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

  -- If REJECTED, mark the in-place ADD row REJECTED (if it exists) and return.
  -- PENDING rows never produced earnings, so there is nothing to reverse.
  IF p_decision = 'REJECTED' THEN
    IF v_request.action_type = 'ADD_GROUP' AND v_request.target_id IS NOT NULL THEN
      UPDATE public.teacher_groups
      SET approval_status = 'REJECTED', updated_at = now()
      WHERE id = v_request.target_id;
    ELSIF v_request.action_type = 'ADD_SUBSCRIPTION' AND v_request.target_id IS NOT NULL THEN
      UPDATE public.teacher_subscriptions
      SET approval_status = 'REJECTED', updated_at = now()
      WHERE id = v_request.target_id;
    END IF;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(),
        'SUBSCRIPTION_REQUEST_REJECTED',
        v_request.target_type,
        v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'note', p_note)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
    RETURN;
  END IF;

  -- ════════════════════════════════════════════════════════════════════════
  -- APPROVED: execute the actual change
  -- ════════════════════════════════════════════════════════════════════════

  IF v_request.action_type = 'ADD_SUBSCRIPTION' THEN
    IF v_request.target_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM public.teacher_subscriptions WHERE id = v_request.target_id) THEN
      -- Row already created at submit time (PENDING) — flip to APPROVED.
      -- trg_approve_initial_earning records the initial earning.
      UPDATE public.teacher_subscriptions
      SET approval_status = 'APPROVED', updated_at = now()
      WHERE id = v_request.target_id
      RETURNING id INTO v_new_sub_id;
    ELSE
      -- Legacy request (submitted before this migration): create the row now
      -- with APPROVED. The trg_record_initial_earning INSERT trigger records
      -- the initial earning automatically.
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
    END IF;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_APPROVED', 'subscription', v_new_sub_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'ADD_GROUP' THEN
    IF v_request.target_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM public.teacher_groups WHERE id = v_request.target_id) THEN
      -- Row already created at submit time (PENDING) — flip to APPROVED.
      UPDATE public.teacher_groups
      SET approval_status = 'APPROVED', updated_at = now()
      WHERE id = v_request.target_id;
    ELSE
      -- Legacy request: create the group now (ON CONFLICT handles duplicates).
      INSERT INTO public.teacher_groups (teacher_id, name, level, approval_status)
      VALUES (
        v_request.teacher_id,
        (v_request.request_data->>'name'),
        (v_request.request_data->>'level'),
        'APPROVED'
      )
      ON CONFLICT (teacher_id, name) DO UPDATE SET
        level = COALESCE(EXCLUDED.level, teacher_groups.level),
        approval_status = 'APPROVED',
        updated_at = now();
    END IF;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'GROUP_APPROVED', 'group', NULL,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'name', v_request.request_data->>'name')
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'DELETE_SUBSCRIPTION' THEN
    -- Delete the subscription
    DELETE FROM public.teacher_subscriptions WHERE id = v_request.target_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_DELETE_APPROVED', 'subscription', v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

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

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_UPDATE_APPROVED', 'subscription', v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'DELETE_GROUP' THEN
    -- Delete group and its subscriptions
    DELETE FROM public.teacher_subscriptions
    WHERE teacher_id = v_request.teacher_id
      AND group_name = (v_request.request_data->>'group_name');
    DELETE FROM public.teacher_groups WHERE id = v_request.target_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'GROUP_DELETE_APPROVED', 'group', v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'RENEW_SUBSCRIPTION' THEN
    -- Process the renewal: update renewal date.
    --
    -- The trg_record_renewal_ledger trigger fires on INSERT to
    -- subscription_renewals and creates the teacher_ledger entry automatically.
    -- We do NOT insert into teacher_ledger manually (that would double-count).
    --
    -- We DO manually insert into subscription_earnings because there is no
    -- automatic earnings trigger for renewals (only for initial subscription
    -- creation via trg_record_initial_earning).
    v_sub_data := v_request.request_data;

    -- Record renewal history → trigger trg_record_renewal_ledger creates
    -- the teacher_ledger entry automatically
    INSERT INTO public.subscription_renewals (
      subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
    ) VALUES (
      v_request.target_id, auth.uid(),
      (v_sub_data->>'previous_renewal_date')::DATE,
      (v_sub_data->>'new_renewal_date')::DATE,
      (v_sub_data->>'amount')::NUMERIC,
      COALESCE(v_sub_data->>'currency', 'EGP')
    ) RETURNING id INTO v_renewal_id;

    -- Record earnings for the renewal (no automatic trigger for this)
    INSERT INTO public.subscription_earnings (
      teacher_id, subscription_id, renewal_id, group_name,
      monthly_amount, currency, teacher_percentage,
      teacher_earning, owner_earning, period_start, period_end
    )
    SELECT
      ts.teacher_id,
      v_request.target_id,
      v_renewal_id,
      ts.group_name,
      (v_sub_data->>'amount')::NUMERIC,
      COALESCE(v_sub_data->>'currency', 'EGP'),
      COALESCE(
        (SELECT percentage FROM public.teacher_subscription_rates WHERE teacher_id = ts.teacher_id),
        70
      ),
      ROUND((v_sub_data->>'amount')::NUMERIC * COALESCE(
        (SELECT percentage FROM public.teacher_subscription_rates WHERE teacher_id = ts.teacher_id),
        70
      ) / 100, 2),
      (v_sub_data->>'amount')::NUMERIC - ROUND((v_sub_data->>'amount')::NUMERIC * COALESCE(
        (SELECT percentage FROM public.teacher_subscription_rates WHERE teacher_id = ts.teacher_id),
        70
      ) / 100, 2),
      date_trunc('month', current_date)::date,
      (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date
    FROM public.teacher_subscriptions ts WHERE ts.id = v_request.target_id;

    -- Update subscription renewal date
    UPDATE public.teacher_subscriptions
    SET next_renewal_date = (v_sub_data->>'new_renewal_date')::DATE, status = 'ACTIVE'
    WHERE id = v_request.target_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'RENEWAL_APPROVED', 'subscription', v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id,
          'amount', v_sub_data->>'amount', 'new_date', v_sub_data->>'new_renewal_date')
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.process_approval_request(UUID, TEXT, TEXT) TO authenticated;
-- ============================================================================
-- 5. Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
