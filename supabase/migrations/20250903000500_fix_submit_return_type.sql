-- ============================================================================
-- Fix: submit_subscription_request returns UUID but Kotlin calls rpcVoid
--
-- PostgreSQL does not allow changing a function's return type with
-- CREATE OR REPLACE. We must DROP first, then recreate as VOID.
-- ============================================================================

-- Drop the existing function (signature must match exactly for DROP)
DROP FUNCTION IF EXISTS public.submit_subscription_request(TEXT, TEXT, UUID, JSONB);
CREATE FUNCTION public.submit_subscription_request(
  p_action_type TEXT,
  p_target_type TEXT,
  p_target_id UUID DEFAULT NULL,
  p_request_data JSONB DEFAULT '{}'::jsonb
)
RETURNS VOID AS $$
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

  -- Log to audit — wrap in EXCEPTION so audit failure never rolls back the request
  BEGIN
    INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    VALUES (
      auth.uid(),
      'SUBSCRIPTION_REQUEST_' || p_action_type,
      p_target_type,
      p_target_id,
      'TEACHER',
      jsonb_build_object('request_id', v_request_id, 'teacher_id', auth.uid())
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.submit_subscription_request(TEXT, TEXT, UUID, JSONB) TO authenticated;
