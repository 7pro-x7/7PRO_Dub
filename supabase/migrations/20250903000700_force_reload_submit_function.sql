-- Force PostgREST to reload schema by touching the function
-- Also add better error handling to catch the actual root cause
CREATE OR REPLACE FUNCTION public.submit_subscription_request(
  p_action_type TEXT,
  p_target_type TEXT,
  p_target_id UUID DEFAULT NULL,
  p_request_data JSONB DEFAULT '{}'::jsonb
)
RETURNS void AS $$
DECLARE
  v_caller_role TEXT;
  v_request_id UUID;
BEGIN
  -- Get caller role from profiles
  SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
  
  IF v_caller_role IS NULL THEN
    RAISE EXCEPTION 'PROFILE_NOT_FOUND';
  END IF;
  
  IF v_caller_role IS DISTINCT FROM 'TEACHER' 
     AND v_caller_role IS DISTINCT FROM 'ADMIN'
     AND v_caller_role IS DISTINCT FROM 'OWNER' THEN
    RAISE EXCEPTION 'Only teachers, admins, or owners can submit subscription requests';
  END IF;

  -- Validate action_type
  IF p_action_type NOT IN ('ADD_GROUP', 'DELETE_GROUP', 'ADD_SUBSCRIPTION', 'DELETE_SUBSCRIPTION', 'UPDATE_SUBSCRIPTION', 'RENEW_SUBSCRIPTION') THEN
    RAISE EXCEPTION 'Invalid action type: %', p_action_type;
  END IF;

  INSERT INTO public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  VALUES (auth.uid(), p_action_type, p_target_type, p_target_id, p_request_data)
  RETURNING id INTO v_request_id;

  -- Log to audit — wrapped in EXCEPTION so audit failure never rolls back the request
  BEGIN
    INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    VALUES (
      auth.uid(),
      'SUBSCRIPTION_REQUEST_' || p_action_type,
      p_target_type,
      p_target_id,
      v_caller_role::public.app_role,
      jsonb_build_object('request_id', v_request_id, 'teacher_id', auth.uid())
    );
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.submit_subscription_request(TEXT, TEXT, UUID, JSONB) TO authenticated;
