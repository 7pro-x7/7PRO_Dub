-- Found by live end-to-end testing after shipping the self-service renewal flow:
--
-- 1) renew_subscription() (a teacher renewing their own student directly) recorded NO earning
--    anywhere — not subscription_earnings, not teacher_ledger. Every plain monthly renewal was
--    earning nothing for anyone.
-- 2) process_approval_request()'s RENEW_SUBSCRIPTION branch (an owner/admin-approved renewal)
--    hand-rolled a partial subscription_earnings insert that never touched teacher_ledger, so
--    the report showed a number but the teacher's payable balance never actually grew.
--
-- Both are fixed by routing through the same _record_subscription_earning() helper the initial
-- subscription approval already uses — same dedup guard (subscription_id, period_start,
-- period_end), same ledger entry, no more inconsistency between the two renewal paths.

CREATE OR REPLACE FUNCTION public.renew_subscription(p_subscription_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
  v_role TEXT;
  v_sub RECORD;
  v_new_date DATE;
  v_renewal_id uuid;
begin
  select role into v_role from public.profiles where id = auth.uid();
  if v_role is null then
    raise exception 'PROFILE_NOT_FOUND';
  end if;
  if v_role not in ('TEACHER', 'ADMIN', 'OWNER') then
    raise exception 'FORBIDDEN';
  end if;

  select * into v_sub from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found then
    raise exception 'SUBSCRIPTION_NOT_FOUND';
  end if;

  if v_sub.teacher_id <> auth.uid() and v_role not in ('ADMIN', 'OWNER') then
    raise exception 'FORBIDDEN';
  end if;

  if v_sub.approval_status != 'APPROVED' then
    raise exception 'CANNOT_RENEW_PENDING';
  end if;

  if current_date < v_sub.next_renewal_date then
    raise exception 'NOT_DUE_YET:%', v_sub.next_renewal_date;
  end if;

  v_new_date := v_sub.next_renewal_date + case
    when v_sub.billing_cycle = 'WEEKLY' then interval '1 week'
    else interval '1 month'
  end;

  insert into public.subscription_renewals (
    subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
  ) values (
    p_subscription_id, auth.uid(), v_sub.next_renewal_date, v_new_date, v_sub.monthly_amount, v_sub.currency
  ) returning id into v_renewal_id;

  update public.teacher_subscriptions
  set next_renewal_date = v_new_date, status = 'ACTIVE'
  where id = p_subscription_id;

  perform public._record_subscription_earning(
    p_subscription_id, v_renewal_id, v_sub.next_renewal_date, v_new_date,
    'Renewal: ' || v_sub.group_name || ' - ' || v_sub.student_name
  );
end;
$function$;

CREATE OR REPLACE FUNCTION public.process_approval_request(p_request_id uuid, p_decision text, p_note text DEFAULT ''::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_caller_role TEXT;
  v_request RECORD;
  v_sub_data JSONB;
  v_new_sub_id UUID;
  v_renewal_id UUID;
  v_group_name TEXT;
BEGIN
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  IF v_caller_role NOT IN ('OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'Only owner or admin can approve/reject requests';
  END IF;

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

  UPDATE public.approval_requests
  SET status = p_decision, reviewed_by = auth.uid(), reviewed_at = now(), review_note = p_note
  WHERE id = p_request_id;

  IF p_decision = 'REJECTED' THEN
    IF v_request.action_type IN ('ADD_GROUP', 'ACTIVATE_GROUP') AND v_request.target_id IS NOT NULL THEN
      UPDATE public.teacher_groups SET approval_status = 'REJECTED', updated_at = now() WHERE id = v_request.target_id;
    ELSIF v_request.action_type IN ('ADD_SUBSCRIPTION', 'ACTIVATE_SUBSCRIPTION') AND v_request.target_id IS NOT NULL THEN
      UPDATE public.teacher_subscriptions SET approval_status = 'REJECTED', updated_at = now() WHERE id = v_request.target_id;
    END IF;
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'SUBSCRIPTION_REQUEST_REJECTED', v_request.target_type, v_request.target_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'note', p_note));
    EXCEPTION WHEN OTHERS THEN NULL; END;
    RETURN;
  END IF;

  IF v_request.action_type = 'ADD_SUBSCRIPTION' THEN
    IF v_request.target_id IS NOT NULL AND EXISTS (SELECT 1 FROM public.teacher_subscriptions WHERE id = v_request.target_id) THEN
      UPDATE public.teacher_subscriptions SET approval_status = 'APPROVED', updated_at = now()
      WHERE id = v_request.target_id RETURNING id INTO v_new_sub_id;
    ELSE
      v_sub_data := v_request.request_data;
      INSERT INTO public.teacher_subscriptions (
        teacher_id, group_name, parent_name, student_name, start_date, monthly_amount, currency,
        next_renewal_date, status, level, parent_phone, notes, approval_status
      ) VALUES (
        v_request.teacher_id, (v_sub_data->>'group_name'), (v_sub_data->>'parent_name'), (v_sub_data->>'student_name'),
        (v_sub_data->>'start_date')::DATE, (v_sub_data->>'monthly_amount')::NUMERIC, COALESCE(v_sub_data->>'currency', 'EGP'),
        (v_sub_data->>'next_renewal_date')::DATE, COALESCE(v_sub_data->>'status', 'ACTIVE'),
        v_sub_data->>'level', v_sub_data->>'parent_phone', v_sub_data->>'notes', 'APPROVED'
      ) RETURNING id INTO v_new_sub_id;
    END IF;
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'SUBSCRIPTION_APPROVED', 'subscription', v_new_sub_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN NULL; END;

  ELSIF v_request.action_type = 'ACTIVATE_SUBSCRIPTION' THEN
    UPDATE public.teacher_subscriptions SET approval_status = 'APPROVED', updated_at = now() WHERE id = v_request.target_id;
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'SUBSCRIPTION_ACTIVATED', 'subscription', v_request.target_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN NULL; END;

  ELSIF v_request.action_type IN ('ADD_GROUP', 'ACTIVATE_GROUP') THEN
    IF v_request.action_type = 'ACTIVATE_GROUP' THEN
      SELECT name INTO v_group_name FROM public.teacher_groups WHERE id = v_request.target_id;
      UPDATE public.teacher_groups SET approval_status = 'APPROVED', updated_at = now() WHERE id = v_request.target_id;
      IF v_group_name IS NOT NULL THEN
        UPDATE public.teacher_subscriptions SET approval_status = 'APPROVED', updated_at = now()
        WHERE teacher_id = v_request.teacher_id AND lower(group_name) = lower(v_group_name)
          AND status != 'PAUSED' AND approval_status != 'APPROVED';
      END IF;
    ELSIF v_request.target_id IS NOT NULL AND EXISTS (SELECT 1 FROM public.teacher_groups WHERE id = v_request.target_id) THEN
      UPDATE public.teacher_groups SET approval_status = 'APPROVED', updated_at = now() WHERE id = v_request.target_id;
    ELSE
      INSERT INTO public.teacher_groups (teacher_id, name, level, approval_status)
      VALUES (v_request.teacher_id, (v_request.request_data->>'name'), (v_request.request_data->>'level'), 'APPROVED')
      ON CONFLICT (teacher_id, name) DO UPDATE SET
        level = COALESCE(EXCLUDED.level, teacher_groups.level), approval_status = 'APPROVED', updated_at = now();
    END IF;
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), CASE WHEN v_request.action_type = 'ACTIVATE_GROUP' THEN 'GROUP_ACTIVATED' ELSE 'GROUP_APPROVED' END,
        'group', v_request.target_id, COALESCE(v_caller_role, 'ADMIN'),
        jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id, 'name', v_request.request_data->>'name'));
    EXCEPTION WHEN OTHERS THEN NULL; END;

  ELSIF v_request.action_type = 'DELETE_SUBSCRIPTION' THEN
    DELETE FROM public.teacher_subscriptions WHERE id = v_request.target_id;
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'SUBSCRIPTION_DELETE_APPROVED', 'subscription', v_request.target_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN NULL; END;

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
      VALUES (auth.uid(), 'SUBSCRIPTION_UPDATE_APPROVED', 'subscription', v_request.target_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN NULL; END;

  ELSIF v_request.action_type = 'DELETE_GROUP' THEN
    DELETE FROM public.teacher_subscriptions WHERE teacher_id = v_request.teacher_id AND group_name = (v_request.request_data->>'group_name');
    DELETE FROM public.teacher_groups WHERE id = v_request.target_id;
    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'GROUP_DELETE_APPROVED', 'group', v_request.target_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id));
    EXCEPTION WHEN OTHERS THEN NULL; END;

  ELSIF v_request.action_type = 'RENEW_SUBSCRIPTION' THEN
    v_sub_data := v_request.request_data;

    INSERT INTO public.subscription_renewals (
      subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
    ) VALUES (
      v_request.target_id, auth.uid(),
      (v_sub_data->>'previous_renewal_date')::DATE, (v_sub_data->>'new_renewal_date')::DATE,
      (v_sub_data->>'amount')::NUMERIC, COALESCE(v_sub_data->>'currency', 'EGP')
    ) RETURNING id INTO v_renewal_id;

    UPDATE public.teacher_subscriptions
    SET next_renewal_date = (v_sub_data->>'new_renewal_date')::DATE, status = 'ACTIVE'
    WHERE id = v_request.target_id;

    PERFORM public._record_subscription_earning(
      v_request.target_id, v_renewal_id,
      (v_sub_data->>'previous_renewal_date')::DATE, (v_sub_data->>'new_renewal_date')::DATE,
      'Renewal approved: request ' || p_request_id
    );

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (auth.uid(), 'RENEWAL_APPROVED', 'subscription', v_request.target_id,
        COALESCE(v_caller_role, 'ADMIN'), jsonb_build_object('request_id', p_request_id, 'teacher_id', v_request.teacher_id,
          'amount', v_sub_data->>'amount', 'new_date', v_sub_data->>'new_renewal_date'));
    EXCEPTION WHEN OTHERS THEN NULL; END;
  END IF;
END;
$function$;
