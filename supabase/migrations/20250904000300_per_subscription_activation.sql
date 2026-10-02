-- ============================================================================
-- Per-Subscription Activation Approval Flow
--
-- NEW BEHAVIOR:
--   * Every new subscription starts PENDING regardless of group status.
--   * Teachers must "activate" each subscription individually to request
--     owner/admin approval.
--   * Owner/Admin approves or rejects individual subscriptions.
--   * APPROVED subscriptions count toward earnings; PENDING/REJECTED do not.
--   * Renewals are only allowed for APPROVED subscriptions.
--   * The earnings trigger records initial earning ONLY when a subscription
--     transitions from non-APPROVED to APPROVED (prevents duplicates).
-- ============================================================================

-- ============================================================================
-- 1. Allow ACTIVATE_SUBSCRIPTION action type
-- ============================================================================
ALTER TABLE public.approval_requests
  DROP CONSTRAINT IF EXISTS approval_requests_action_type_check;
ALTER TABLE public.approval_requests
  ADD CONSTRAINT approval_requests_action_type_check
  CHECK (action_type IN (
    'ADD_SUBSCRIPTION', 'DELETE_SUBSCRIPTION', 'UPDATE_SUBSCRIPTION',
    'ADD_GROUP', 'DELETE_GROUP', 'RENEW_SUBSCRIPTION',
    'ACTIVATE_GROUP', 'ACTIVATE_SUBSCRIPTION'
  ));
-- ============================================================================
-- 2. activate_subscription(p_subscription_id)
--    Teacher:  PENDING/REJECTED subscription -> PENDING + ACTIVATE_SUBSCRIPTION request.
--    Staff:    approves the subscription immediately (they are the approvers).
-- ============================================================================
CREATE OR REPLACE FUNCTION public.activate_subscription(p_subscription_id UUID)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_sub RECORD;
  v_request_id UUID;
BEGIN
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  IF v_caller_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;

  IF v_caller_role NOT IN ('TEACHER', 'ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT * INTO v_sub FROM public.teacher_subscriptions WHERE id = p_subscription_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SUBSCRIPTION_NOT_FOUND';
  END IF;

  IF v_sub.teacher_id <> auth.uid() AND v_caller_role NOT IN ('ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  -- Already approved: nothing to do.
  IF v_sub.approval_status = 'APPROVED' THEN
    RETURN;
  END IF;
  -- Already pending: wait for the decision.
  IF v_sub.approval_status = 'PENDING' THEN
    RAISE EXCEPTION 'SUBSCRIPTION_ALREADY_PENDING';
  END IF;

  IF v_caller_role IN ('ADMIN', 'OWNER') THEN
    -- Staff: approve immediately
    UPDATE public.teacher_subscriptions
    SET approval_status = 'APPROVED', updated_at = now()
    WHERE id = p_subscription_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_ACTIVATED', 'subscription', p_subscription_id,
        v_caller_role::public.app_role,
        jsonb_build_object('teacher_id', v_sub.teacher_id, 'student_name', v_sub.student_name, 'group_name', v_sub.group_name)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
  ELSE
    -- Teacher: set PENDING + create approval request
    UPDATE public.teacher_subscriptions
    SET approval_status = 'PENDING', updated_at = now()
    WHERE id = p_subscription_id;

    INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
    VALUES (
      auth.uid(), 'ACTIVATE_SUBSCRIPTION', 'subscription', p_subscription_id,
      jsonb_build_object(
        'student_name', v_sub.student_name,
        'parent_name', v_sub.parent_name,
        'group_name', v_sub.group_name,
        'monthly_amount', v_sub.monthly_amount,
        'currency', v_sub.currency,
        'level', v_sub.level
      )
    ) RETURNING id INTO v_request_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_ACTIVATION_REQUESTED', 'subscription', p_subscription_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', v_request_id, 'teacher_id', v_sub.teacher_id, 'student_name', v_sub.student_name)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.activate_subscription(UUID) TO authenticated;
-- ============================================================================
-- 3. Fix the initial earning trigger to prevent duplicates
--    Records initial earning only on INSERT (new subscriptions) or when a
--    subscription transitions from non-APPROVED to APPROVED.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.record_initial_subscription_earning()
RETURNS TRIGGER AS $$
DECLARE
  v_rate NUMERIC(5,2);
  v_teacher_earning NUMERIC(12,2);
  v_owner_earning NUMERIC(12,2);
  v_period_start DATE;
  v_period_end DATE;
  v_old_approval TEXT;
BEGIN
  -- Determine the old approval status (NULL for INSERT)
  IF TG_OP = 'UPDATE' THEN
    v_old_approval := OLD.approval_status;
  ELSE
    v_old_approval := NULL;
  END IF;

  -- Record initial earning only when:
  --   - INSERT with approval_status = 'APPROVED' (admin-created)
  --   - UPDATE transitioning from non-APPROVED to 'APPROVED' (approval granted)
  -- Skip if:
  --   - Approval status is PENDING or REJECTED (no earnings yet)
  --   - Already was APPROVED before (no duplicate on other field changes)
  IF NEW.approval_status = 'APPROVED' THEN
    IF TG_OP = 'INSERT' THEN
      -- New subscription created as APPROVED — record earning
    ELSIF TG_OP = 'UPDATE' AND v_old_approval != 'APPROVED' THEN
      -- Transitioning from PENDING/REJECTED to APPROVED — record earning
    ELSE
      -- Already was APPROVED, just a field update — skip
      RETURN NEW;
    END IF;
  ELSE
    -- PENDING or REJECTED — no earning
    RETURN NEW;
  END IF;

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
-- Recreate trigger to also fire on UPDATE of approval_status
DROP TRIGGER IF EXISTS trg_record_initial_earning ON public.teacher_subscriptions;
CREATE TRIGGER trg_record_initial_earning
  AFTER INSERT OR UPDATE OF approval_status ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public.record_initial_subscription_earning();
GRANT EXECUTE ON FUNCTION public.record_initial_subscription_earning() TO authenticated;
-- ============================================================================
-- 4. Update process_approval_request to handle ACTIVATE_SUBSCRIPTION
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
  v_group_name TEXT;
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

  -- If REJECTED, mark the in-place row REJECTED (if it exists) and return.
  IF p_decision = 'REJECTED' THEN
    IF v_request.action_type IN ('ADD_GROUP', 'ACTIVATE_GROUP') AND v_request.target_id IS NOT NULL THEN
      UPDATE public.teacher_groups
      SET approval_status = 'REJECTED', updated_at = now()
      WHERE id = v_request.target_id;
    ELSIF v_request.action_type IN ('ADD_SUBSCRIPTION', 'ACTIVATE_SUBSCRIPTION') AND v_request.target_id IS NOT NULL THEN
      UPDATE public.teacher_subscriptions
      SET approval_status = 'REJECTED', updated_at = now()
      WHERE id = v_request.target_id;
    END IF;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_REQUEST_REJECTED',
        v_request.target_type, v_request.target_id,
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
      -- trg_record_initial_earning records the initial earning.
      UPDATE public.teacher_subscriptions
      SET approval_status = 'APPROVED', updated_at = now()
      WHERE id = v_request.target_id
      RETURNING id INTO v_new_sub_id;
    ELSE
      -- Legacy request: create the row now with APPROVED.
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

  ELSIF v_request.action_type = 'ACTIVATE_SUBSCRIPTION' THEN
    -- Subscription already exists (PENDING/REJECTED) — activate it.
    -- trg_record_initial_earning records the earning when approval_status flips to APPROVED.
    UPDATE public.teacher_subscriptions
    SET approval_status = 'APPROVED', updated_at = now()
    WHERE id = v_request.target_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_ACTIVATED', 'subscription', v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type IN ('ADD_GROUP', 'ACTIVATE_GROUP') THEN
    IF v_request.action_type = 'ACTIVATE_GROUP' THEN
      -- Group already exists (INACTIVE / REJECTED) — activate it and its members.
      SELECT name INTO v_group_name FROM public.teacher_groups WHERE id = v_request.target_id;

      UPDATE public.teacher_groups
      SET approval_status = 'APPROVED', updated_at = now()
      WHERE id = v_request.target_id;

      IF v_group_name IS NOT NULL THEN
        UPDATE public.teacher_subscriptions
        SET approval_status = 'APPROVED', updated_at = now()
        WHERE teacher_id = v_request.teacher_id
          AND lower(group_name) = lower(v_group_name)
          AND status != 'PAUSED'
          AND approval_status != 'APPROVED';
      END IF;
    ELSIF v_request.target_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM public.teacher_groups WHERE id = v_request.target_id) THEN
      UPDATE public.teacher_groups
      SET approval_status = 'APPROVED', updated_at = now()
      WHERE id = v_request.target_id;
    ELSE
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
        auth.uid(),
        CASE WHEN v_request.action_type = 'ACTIVATE_GROUP' THEN 'GROUP_ACTIVATED' ELSE 'GROUP_APPROVED' END,
        'group', v_request.target_id,
        v_caller_role::public.app_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'name', v_request.request_data->>'name')
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'DELETE_SUBSCRIPTION' THEN
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
    v_sub_data := v_request.request_data;

    INSERT INTO public.subscription_renewals (
      subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
    ) VALUES (
      v_request.target_id, auth.uid(),
      (v_sub_data->>'previous_renewal_date')::DATE,
      (v_sub_data->>'new_renewal_date')::DATE,
      (v_sub_data->>'amount')::NUMERIC,
      COALESCE(v_sub_data->>'currency', 'EGP')
    ) RETURNING id INTO v_renewal_id;

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
