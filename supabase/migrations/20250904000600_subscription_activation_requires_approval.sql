-- ============================================================================
-- Subscription activation always requires admin approval
--
-- PROBLEM:
--   sync_subscription_approval_from_group mirrored the group's status onto new
--   members, so a member added to an already-APPROVED group was stored as
--   APPROVED immediately — no admin approval was ever asked. That bypassed the
--   per-subscription activation flow (activate_subscription -> PENDING +
--   approval request -> owner/admin decision).
--
-- FIX:
--   A NEW subscription (INSERT) is always stored as PENDING regardless of the
--   group's status. Only an owner/admin decision (process_approval_request, or
--   the staff activate_subscription / activate_group paths) flips it to
--   APPROVED.
--
--   Moving an existing member between groups (UPDATE) never auto-approves
--   either: if the destination group is not APPROVED the member is downgraded
--   to PENDING, otherwise the member's current status is left untouched.
--   Legacy subscriptions without a matching teacher_groups row keep the
--   column default (APPROVED) exactly as before.
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
    -- New members always start PENDING; activation needs an admin decision.
    -- On updates, only downgrade to PENDING — never auto-approve.
    IF TG_OP = 'INSERT' OR v_group_status <> 'APPROVED' THEN
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
-- ============================================================================
-- 2. activate_subscription — allow teachers to REQUEST activation of a
--    PENDING member.
--
--    PROBLEM: with the change above every new member starts PENDING, but the
--    old activate_subscription raised SUBSCRIPTION_ALREADY_PENDING whenever
--    the row was PENDING — so a teacher could never create the approval
--    request for a PENDING subscriber.
--
--    FIX: the guard now checks for an in-flight ACTIVATE_SUBSCRIPTION request
--    on the same subscription (idempotency: one request at a time). A PENDING
--    member with no open request can request activation; REJECTED members
--    keep working exactly as before.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.activate_subscription(p_subscription_id UUID)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_sub RECORD;
  v_request_id UUID;
  v_open_request BOOLEAN;
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

  -- Already active: nothing to do.
  IF v_sub.approval_status = 'APPROVED' THEN
    RETURN;
  END IF;

  -- One activation request at a time per subscription.
  SELECT EXISTS (
    SELECT 1 FROM public.approval_requests
    WHERE target_id = p_subscription_id
      AND action_type = 'ACTIVATE_SUBSCRIPTION'
      AND status = 'PENDING'
  ) INTO v_open_request;
  IF v_open_request THEN
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
        COALESCE(v_caller_role, 'TEACHER'),
        jsonb_build_object('teacher_id', v_sub.teacher_id, 'student_name', v_sub.student_name, 'group_name', v_sub.group_name)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
  ELSE
    -- Teacher: PENDING + approval request
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
        COALESCE(v_caller_role, 'TEACHER'),
        jsonb_build_object('request_id', v_request_id, 'teacher_id', v_sub.teacher_id, 'student_name', v_sub.student_name)
      );
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.activate_subscription(UUID) TO authenticated;
-- Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';
