-- ============================================================================
-- Group Activation Approval Flow
--
-- NEW BEHAVIOR (replaces the old per-item approval flow):
--   * Teachers create / edit / delete groups and members directly — no approval.
--   * A teacher-created group starts INACTIVE. It never counts toward earnings,
--     stats, renewals or the ledger until it is activated.
--   * "Activate group" submits an ACTIVATE_GROUP approval request; the group
--     shows PENDING until the owner/admin decides.
--   * APPROVE  -> group becomes APPROVED (active) and its members start counting.
--   * REJECT   -> group becomes REJECTED; nothing financial ever happened.
--   * Members automatically mirror their group: a subscription inside a group
--     that is not APPROVED is stored as PENDING and is therefore excluded from
--     earnings, stats and renewals (the existing APPROVED-only guards). When a
--     group is approved, its members flip to APPROVED and trg_approve_initial_earning
--     records the initial earning for each one exactly once.
--   * Renewals are direct (no approval) but only allowed for members of an
--     APPROVED group, so the ledger is never credited for inactive groups.
--
-- The old submit_subscription_request / process_approval_request functions are
-- kept working so older app versions continue to function; the new app uses the
-- direct table writes, activate_group and renew_subscription instead.
-- ============================================================================

-- ============================================================================
-- 1. teacher_groups: add INACTIVE to the approval workflow statuses
--    Existing rows keep APPROVED; teacher-created rows start INACTIVE.
-- ============================================================================
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'public.teacher_groups'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%approval_status%'
  LOOP
    EXECUTE format('ALTER TABLE public.teacher_groups DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;
ALTER TABLE public.teacher_groups
  ADD CONSTRAINT teacher_groups_approval_status_check
  CHECK (approval_status IN ('INACTIVE', 'PENDING', 'APPROVED', 'REJECTED'));
COMMENT ON COLUMN public.teacher_groups.approval_status
  IS 'Group lifecycle: INACTIVE (created, not counting), PENDING (activation requested), APPROVED (active, counts), REJECTED (denied, does not count)';
-- ============================================================================
-- 2. approval_requests: allow ACTIVATE_GROUP action type
-- ============================================================================
ALTER TABLE public.approval_requests
  DROP CONSTRAINT IF EXISTS approval_requests_action_type_check;
ALTER TABLE public.approval_requests
  ADD CONSTRAINT approval_requests_action_type_check
  CHECK (action_type IN (
    'ADD_SUBSCRIPTION', 'DELETE_SUBSCRIPTION', 'UPDATE_SUBSCRIPTION',
    'ADD_GROUP', 'DELETE_GROUP', 'RENEW_SUBSCRIPTION', 'ACTIVATE_GROUP'
  ));
-- ============================================================================
-- 3. Restore direct teacher write access to groups and members.
--    (These policies were dropped in 20250903000090 to force everything
--    through the approval RPC. Teachers now manage their own rows directly;
--    only group ACTIVATION goes through the owner/admin.)
-- ============================================================================
DROP POLICY IF EXISTS teacher_groups_self_access ON public.teacher_groups;
CREATE POLICY "teacher_groups_self_access"
  ON public.teacher_groups FOR ALL
  USING (auth.uid() = teacher_id)
  WITH CHECK (auth.uid() = teacher_id);
DROP POLICY IF EXISTS teachers_insert_own_subscriptions ON public.teacher_subscriptions;
CREATE POLICY "teachers_insert_own_subscriptions"
  ON public.teacher_subscriptions FOR INSERT
  WITH CHECK (auth.uid() = teacher_id);
DROP POLICY IF EXISTS teachers_update_own_subscriptions ON public.teacher_subscriptions;
CREATE POLICY "teachers_update_own_subscriptions"
  ON public.teacher_subscriptions FOR UPDATE
  USING (auth.uid() = teacher_id);
DROP POLICY IF EXISTS teachers_delete_own_subscriptions ON public.teacher_subscriptions;
CREATE POLICY "teachers_delete_own_subscriptions"
  ON public.teacher_subscriptions FOR DELETE
  USING (auth.uid() = teacher_id);
-- ============================================================================
-- 4. Members mirror their group's status.
--    A subscription inserted into (or moved to) a group that is not APPROVED is
--    stored as PENDING; inside an APPROVED group it is stored as APPROVED.
--    Rows with no matching group (legacy subscriptions) keep whatever status
--    the caller supplied (column default APPROVED).
-- ============================================================================
CREATE OR REPLACE FUNCTION public.sync_subscription_approval_from_group()
RETURNS TRIGGER AS $$
DECLARE
  v_group_status TEXT;
BEGIN
  SELECT approval_status INTO v_group_status
  FROM public.teacher_groups
  WHERE teacher_id = NEW.teacher_id
    AND lower(name) = lower(NEW.group_name)
  LIMIT 1;

  IF v_group_status IS NOT NULL THEN
    NEW.approval_status := CASE WHEN v_group_status = 'APPROVED' THEN 'APPROVED' ELSE 'PENDING' END;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
DROP TRIGGER IF EXISTS trg_sync_subscription_approval_from_group ON public.teacher_subscriptions;
CREATE TRIGGER trg_sync_subscription_approval_from_group
  BEFORE INSERT OR UPDATE OF group_name, status ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_subscription_approval_from_group();
-- ============================================================================
-- 5. activate_group(p_group_id)
--    Teacher:  INACTIVE / REJECTED group -> PENDING + ACTIVATE_GROUP request.
--    Staff:    activates the group immediately (they are the approvers) and the
--              members start counting (initial earnings recorded by trigger).
-- ============================================================================
CREATE OR REPLACE FUNCTION public.activate_group(p_group_id UUID)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_group RECORD;
  v_request_id UUID;
  v_member_count BIGINT;
BEGIN
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  IF v_caller_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;

  IF v_caller_role NOT IN ('TEACHER', 'ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT * INTO v_group FROM public.teacher_groups WHERE id = p_group_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'GROUP_NOT_FOUND';
  END IF;

  IF v_group.teacher_id <> auth.uid() AND v_caller_role NOT IN ('ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  -- Already active: nothing to do. Already pending: wait for the decision.
  IF v_group.approval_status = 'APPROVED' THEN
    RETURN;
  END IF;
  IF v_group.approval_status = 'PENDING' THEN
    RAISE EXCEPTION 'GROUP_ALREADY_PENDING';
  END IF;

  SELECT count(*) INTO v_member_count
  FROM public.teacher_subscriptions
  WHERE teacher_id = v_group.teacher_id
    AND lower(group_name) = lower(v_group.name);

  IF v_caller_role IN ('ADMIN', 'OWNER') THEN
    UPDATE public.teacher_groups
    SET approval_status = 'APPROVED', updated_at = now()
    WHERE id = p_group_id;

    UPDATE public.teacher_subscriptions
    SET approval_status = 'APPROVED', updated_at = now()
    WHERE teacher_id = v_group.teacher_id
      AND lower(group_name) = lower(v_group.name)
      AND status != 'PAUSED'
      AND approval_status != 'APPROVED';
  ELSE
    UPDATE public.teacher_groups
    SET approval_status = 'PENDING', updated_at = now()
    WHERE id = p_group_id;

    INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
    VALUES (
      auth.uid(), 'ACTIVATE_GROUP', 'group', p_group_id,
      jsonb_build_object('name', v_group.name, 'level', v_group.level, 'member_count', v_member_count)
    )
    RETURNING id INTO v_request_id;
  END IF;

  BEGIN
    INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    VALUES (
      auth.uid(),
      CASE WHEN v_caller_role IN ('ADMIN', 'OWNER') THEN 'GROUP_ACTIVATED' ELSE 'GROUP_ACTIVATION_REQUESTED' END,
      'group', p_group_id,
      v_caller_role::public.app_role,
      jsonb_build_object('request_id', v_request_id, 'teacher_id', v_group.teacher_id, 'name', v_group.name)
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.activate_group(UUID) TO authenticated;
-- ============================================================================
-- 6. process_approval_request — handle ACTIVATE_GROUP
--    APPROVE flips the group to APPROVED and activates its members (each flip
--    fires trg_approve_initial_earning and records the initial earning once).
--    REJECT marks the group REJECTED so the teacher sees the red status.
--    All previous action types keep their existing behavior.
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
  -- PENDING rows never produced earnings, so there is nothing to reverse.
  IF p_decision = 'REJECTED' THEN
    IF v_request.action_type IN ('ADD_GROUP', 'ACTIVATE_GROUP') AND v_request.target_id IS NOT NULL THEN
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
      -- Legacy request (submitted before the inline migration): create the row now
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
      -- Legacy inline row created at submit time (PENDING) — flip to APPROVED.
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
-- 7. renew_subscription(p_subscription_id)
--    Direct renewal (no approval) for teachers, admins and owners, but only for
--    members of an APPROVED group — an inactive group never credits the ledger
--    or records earnings. Renewal history + ledger entry come from the same
--    triggers the old approval flow used.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.renew_subscription(p_subscription_id UUID)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_sub RECORD;
  v_group_status TEXT;
  v_prev_date DATE;
  v_new_date DATE;
  v_renewal_id UUID;
  v_rate NUMERIC(5,2);
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

  -- Legacy subscriptions without a group row keep counting as before.
  SELECT approval_status INTO v_group_status
  FROM public.teacher_groups
  WHERE teacher_id = v_sub.teacher_id
    AND lower(name) = lower(v_sub.group_name)
  LIMIT 1;
  IF v_group_status IS NULL THEN
    v_group_status := 'APPROVED';
  END IF;

  IF v_group_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'GROUP_NOT_ACTIVE';
  END IF;
  IF v_sub.approval_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'CANNOT_RENEW_PENDING';
  END IF;

  v_prev_date := v_sub.next_renewal_date;
  v_new_date := (v_prev_date + INTERVAL '1 month')::date;

  -- trg_record_renewal_ledger credits the teacher ledger from this insert.
  INSERT INTO public.subscription_renewals (
    subscription_id, renewed_by, previous_renewal_date, new_renewal_date, amount, currency
  ) VALUES (
    p_subscription_id, auth.uid(), v_prev_date, v_new_date,
    v_sub.monthly_amount, v_sub.currency
  ) RETURNING id INTO v_renewal_id;

  SELECT COALESCE(percentage, 70) INTO v_rate
  FROM public.teacher_subscription_rates
  WHERE teacher_id = v_sub.teacher_id;
  v_rate := COALESCE(v_rate, 70);

  INSERT INTO public.subscription_earnings (
    teacher_id, subscription_id, renewal_id, group_name,
    monthly_amount, currency, teacher_percentage,
    teacher_earning, owner_earning, period_start, period_end
  ) VALUES (
    v_sub.teacher_id, p_subscription_id, v_renewal_id, v_sub.group_name,
    v_sub.monthly_amount, v_sub.currency,
    v_rate,
    round(v_sub.monthly_amount * v_rate / 100, 2),
    v_sub.monthly_amount - round(v_sub.monthly_amount * v_rate / 100, 2),
    date_trunc('month', current_date)::date,
    (date_trunc('month', current_date) + INTERVAL '1 month - 1 day')::date
  );

  UPDATE public.teacher_subscriptions
  SET next_renewal_date = v_new_date, status = 'ACTIVE', updated_at = now()
  WHERE id = p_subscription_id;

  BEGIN
    INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    VALUES (
      auth.uid(), 'SUBSCRIPTION_RENEWED', 'subscription', p_subscription_id,
      v_caller_role::public.app_role,
      jsonb_build_object('teacher_id', v_sub.teacher_id, 'amount', v_sub.monthly_amount, 'new_date', v_new_date)
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.renew_subscription(UUID) TO authenticated;
-- ============================================================================
-- 8. Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
