-- ============================================================================
-- Allow teachers to resubmit a rejected group
--
-- PROBLEM:
--   With the inline approval system, a REJECTED group row stays in
--   teacher_groups (visible with a red badge). When the teacher tried to
--   create a group with the same name again, submit_subscription_request
--   raised GROUP_ALREADY_EXISTS, so the only way forward was asking the
--   owner to delete the rejected group first.
--
-- FIX:
--   Re-creating a group whose previous row is REJECTED now resubmits it:
--   the existing row is updated with the new name/level and flipped back to
--   PENDING, and a new approval request is recorded. APPROVED and PENDING
--   groups still block duplicate names as before.
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
  v_existing_group_id UUID;
  v_existing_status TEXT;
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
  --             A REJECTED row with the same name is resubmitted (flipped
  --             back to PENDING with the new values) instead of erroring.
  --   Staff:    row created as APPROVED (owner/admin are the approvers).
  -- ────────────────────────────────────────────────────────────────────────
  IF p_action_type = 'ADD_GROUP' THEN
    IF NOT v_is_staff THEN
      SELECT id, approval_status INTO v_existing_group_id, v_existing_status
      FROM public.teacher_groups
      WHERE teacher_id = auth.uid()
        AND lower(name) = lower(COALESCE(p_request_data->>'name', ''))
      LIMIT 1;

      IF v_existing_status IN ('APPROVED', 'PENDING') THEN
        RAISE EXCEPTION 'GROUP_ALREADY_EXISTS';
      END IF;

      IF v_existing_status = 'REJECTED' THEN
        -- Resubmit: reuse the rejected row, refresh its values, back to PENDING.
        UPDATE public.teacher_groups
        SET name = COALESCE(NULLIF(p_request_data->>'name', ''), name),
            level = NULLIF(p_request_data->>'level', ''),
            approval_status = 'PENDING',
            updated_at = now()
        WHERE id = v_existing_group_id
        RETURNING id INTO v_target_id;
      ELSE
        INSERT INTO public.teacher_groups (teacher_id, name, level, approval_status)
        VALUES (
          auth.uid(),
          COALESCE(NULLIF(p_request_data->>'name', ''), ''),
          NULLIF(p_request_data->>'level', ''),
          'PENDING'
        )
        RETURNING id INTO v_target_id;
      END IF;

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
-- Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
