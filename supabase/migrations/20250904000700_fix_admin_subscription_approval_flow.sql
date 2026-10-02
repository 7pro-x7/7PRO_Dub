-- ============================================================================
-- Final fixes for the Owner/Admin subscription approval flow
--
-- 1. The approval-sync trigger used to force every INSERT that matched a group
--    to PENDING, including rows inserted by the Owner/Admin console. That made
--    an admin-created subscription look like a teacher approval request.
-- 2. Staff activation was blocked by the teacher's already-open activation
--    request because the duplicate-request guard ran before the staff branch.
-- 3. Direct rejection left an ACTIVATE_SUBSCRIPTION request open in the queue.
-- ============================================================================

-- Keep the group safety rule for teachers, but do not override an explicit
-- Owner/Admin decision made by the console.
CREATE OR REPLACE FUNCTION public.sync_subscription_approval_from_group()
RETURNS TRIGGER AS $$
DECLARE
  v_group_status TEXT;
  v_caller_role TEXT;
BEGIN
  SELECT approval_status INTO v_group_status
  FROM public.teacher_groups
  WHERE teacher_id = NEW.teacher_id
    AND lower(name) = lower(NEW.group_name)
  LIMIT 1;

  SELECT role::text INTO v_caller_role
  FROM public.profiles
  WHERE id = auth.uid();

  IF v_group_status IS NOT NULL THEN
    IF TG_OP = 'INSERT' THEN
      -- Teacher-created members always need activation. Staff-created rows
      -- carry the explicit APPROVED value supplied by the admin console.
      IF COALESCE(v_caller_role, '') NOT IN ('OWNER', 'ADMIN') THEN
        NEW.approval_status := 'PENDING';
      END IF;
    ELSIF v_group_status <> 'APPROVED' THEN
      -- Moving/editing a member into an inactive group cannot make it active.
      NEW.approval_status := 'PENDING';
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
DROP TRIGGER IF EXISTS trg_sync_subscription_approval_from_group ON public.teacher_subscriptions;
CREATE TRIGGER trg_sync_subscription_approval_from_group
  BEFORE INSERT OR UPDATE OF group_name, status ON public.teacher_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_subscription_approval_from_group();
-- Staff can activate a pending subscription even when a teacher has already
-- submitted its activation request. The request is resolved together with the
-- subscription so it cannot remain as a stale item in the approval queue.
CREATE OR REPLACE FUNCTION public.activate_subscription(p_subscription_id UUID)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_sub RECORD;
  v_request_id UUID;
  v_open_request BOOLEAN;
BEGIN
  SELECT role::text INTO v_caller_role
  FROM public.profiles
  WHERE id = auth.uid();

  IF v_caller_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;
  IF v_caller_role NOT IN ('TEACHER', 'ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT * INTO v_sub
  FROM public.teacher_subscriptions
  WHERE id = p_subscription_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SUBSCRIPTION_NOT_FOUND';
  END IF;

  IF v_sub.teacher_id <> auth.uid() AND v_caller_role NOT IN ('ADMIN', 'OWNER') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  IF v_sub.approval_status = 'APPROVED' THEN
    RETURN;
  END IF;

  IF v_caller_role IN ('ADMIN', 'OWNER') THEN
    UPDATE public.teacher_subscriptions
    SET approval_status = 'APPROVED', updated_at = now()
    WHERE id = p_subscription_id;

    UPDATE public.approval_requests
    SET status = 'APPROVED',
        reviewed_by = auth.uid(),
        reviewed_at = now(),
        review_note = COALESCE(review_note, 'Approved directly by Owner/Admin')
    WHERE target_id = p_subscription_id
      AND action_type = 'ACTIVATE_SUBSCRIPTION'
      AND status = 'PENDING';

    BEGIN
      INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
      VALUES (
        auth.uid(), 'SUBSCRIPTION_ACTIVATED', 'subscription', p_subscription_id,
        v_caller_role,
        jsonb_build_object(
          'teacher_id', v_sub.teacher_id,
          'student_name', v_sub.student_name,
          'group_name', v_sub.group_name
        )
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
    RETURN;
  END IF;

  -- Teachers may submit one activation request at a time for a subscription.
  -- Keep this guard inside the teacher branch so staff can resolve a request
  -- that is already open for the same subscription.
  SELECT EXISTS (
    SELECT 1
    FROM public.approval_requests
    WHERE target_id = p_subscription_id
      AND action_type = 'ACTIVATE_SUBSCRIPTION'
      AND status = 'PENDING'
  ) INTO v_open_request;
  IF v_open_request THEN
    RAISE EXCEPTION 'SUBSCRIPTION_ALREADY_PENDING';
  END IF;

  UPDATE public.teacher_subscriptions
  SET approval_status = 'PENDING', updated_at = now()
  WHERE id = p_subscription_id;

  INSERT INTO public.approval_requests (
    teacher_id, action_type, target_type, target_id, request_data
  ) VALUES (
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
      v_caller_role,
      jsonb_build_object('request_id', v_request_id, 'teacher_id', v_sub.teacher_id)
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.activate_subscription(UUID) TO authenticated;
-- Direct rejection is used by the Owner/Admin teacher detail screen. Resolve
-- any matching activation requests in the same transaction and leave an audit
-- trail without allowing audit-schema issues to roll back the decision.
CREATE OR REPLACE FUNCTION public.reject_subscription(p_subscription_id UUID)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_sub RECORD;
BEGIN
  SELECT role::text INTO v_caller_role
  FROM public.profiles
  WHERE id = auth.uid();

  IF v_caller_role NOT IN ('OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT * INTO v_sub
  FROM public.teacher_subscriptions
  WHERE id = p_subscription_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SUBSCRIPTION_NOT_FOUND';
  END IF;

  UPDATE public.teacher_subscriptions
  SET approval_status = 'REJECTED', updated_at = now()
  WHERE id = p_subscription_id;

  UPDATE public.approval_requests
  SET status = 'REJECTED',
      reviewed_by = auth.uid(),
      reviewed_at = now(),
      review_note = COALESCE(review_note, 'Rejected directly by Owner/Admin')
  WHERE target_id = p_subscription_id
    AND action_type = 'ACTIVATE_SUBSCRIPTION'
    AND status = 'PENDING';

  BEGIN
    INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    VALUES (
      auth.uid(), 'SUBSCRIPTION_REJECTED', 'subscription', p_subscription_id,
      v_caller_role,
      jsonb_build_object('teacher_id', v_sub.teacher_id, 'student_name', v_sub.student_name)
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.reject_subscription(UUID) TO authenticated;
NOTIFY pgrst, 'reload schema';
