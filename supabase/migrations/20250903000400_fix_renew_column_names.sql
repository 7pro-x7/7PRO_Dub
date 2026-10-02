-- ============================================================================
-- Fix: Wrong column names in RENEW branch of process_approval_request
--
-- BUG 1: tsr.teacher_share_pct should be tsr.percentage (the actual column name)
-- BUG 2: student_name column does not exist in subscription_earnings table
--         (it was incorrectly referenced from teacher_subscriptions)
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

  -- If REJECTED, log and return
  IF p_decision = 'REJECTED' THEN
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(),
        'SUBSCRIPTION_REQUEST_REJECTED',
        v_request.target_type,
        v_request.target_id,
        v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'note', p_note)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
    RETURN;
  END IF;

  -- ════════════════════════════════════════════════════════════════════════
  -- APPROVED: Execute the actual change
  -- ════════════════════════════════════════════════════════════════════════

  IF v_request.action_type = 'ADD_SUBSCRIPTION' THEN
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

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'SUBSCRIPTION_APPROVED', 'subscription', v_new_sub_id, v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'DELETE_SUBSCRIPTION' THEN
    DELETE FROM public.teacher_subscriptions WHERE id = v_request.target_id;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'SUBSCRIPTION_DELETE_APPROVED', 'subscription', v_request.target_id, v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
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
      VALUES (auth.uid(), 'SUBSCRIPTION_UPDATE_APPROVED', 'subscription', v_request.target_id, v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  ELSIF v_request.action_type = 'ADD_GROUP' THEN
    INSERT INTO public.teacher_groups (teacher_id, name, level)
    VALUES (v_request.teacher_id, (v_request.request_data->>'name'), (v_request.request_data->>'level'))
    ON CONFLICT (teacher_id, name) DO UPDATE SET
      level = COALESCE(EXCLUDED.level, teacher_groups.level),
      updated_at = now();

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'GROUP_APPROVED', 'group', NULL, v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'name', v_request.request_data->>'name'));
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
      VALUES (auth.uid(), 'GROUP_DELETE_APPROVED', 'group', v_request.target_id, v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
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

    -- Update subscription's next renewal date
    UPDATE public.teacher_subscriptions
    SET next_renewal_date = (v_sub_data->>'new_renewal_date')::DATE
    WHERE id = v_request.target_id;

    -- Record earnings for the renewal (no automatic trigger for renewals)
    BEGIN
      INSERT INTO public.subscription_earnings (
        subscription_id, teacher_id, renewal_id, group_name,
        monthly_amount, currency, teacher_percentage,
        teacher_earning, owner_earning, period_start, period_end
      )
      SELECT
        v_request.target_id,
        ts.teacher_id,
        v_renewal_id,
        ts.group_name,
        (v_sub_data->>'amount')::NUMERIC,
        COALESCE(v_sub_data->>'currency', 'EGP'),
        COALESCE(tsr.percentage, 70),
        ROUND((v_sub_data->>'amount')::NUMERIC * COALESCE(tsr.percentage, 70) / 100, 2),
        (v_sub_data->>'amount')::NUMERIC - ROUND((v_sub_data->>'amount')::NUMERIC * COALESCE(tsr.percentage, 70) / 100, 2),
        date_trunc('month', current_date)::date,
        (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date
      FROM public.teacher_subscriptions ts
      LEFT JOIN public.teacher_subscription_rates tsr ON tsr.teacher_id = ts.teacher_id
      WHERE ts.id = v_request.target_id;
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'RENEWAL_APPROVED', 'renewal', v_renewal_id, v_caller_role,
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'subscription_id', v_request.target_id));
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;

  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.process_approval_request(UUID, TEXT, TEXT) TO authenticated;
