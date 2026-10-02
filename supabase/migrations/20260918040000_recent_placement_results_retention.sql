-- Keep placement-test results available to staff for today and yesterday only.
-- This removes attempts (and their answer rows through the existing FK cascade),
-- not the learner's profile/account.

CREATE OR REPLACE FUNCTION public.cleanup_old_placement_attempts()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  removed integer;
BEGIN
  DELETE FROM public.test_attempts
  WHERE status = 'SUBMITTED'
    AND submitted_at < date_trunc('day', now()) - interval '1 day';
  GET DIAGNOSTICS removed = ROW_COUNT;
  RETURN removed;
END;
$$;

REVOKE ALL ON FUNCTION public.cleanup_old_placement_attempts() FROM PUBLIC;

CREATE OR REPLACE FUNCTION public.admin_recent_placement_results()
RETURNS TABLE (
  attempt_id uuid,
  user_id uuid,
  full_name text,
  email text,
  level text,
  percent numeric,
  submitted_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (public.is_owner() OR public.is_staff() OR public.has_permission('tests.manage')) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT
    a.id,
    a.user_id,
    COALESCE(p.full_name, ''),
    COALESCE(p.email, ''),
    a.level,
    a.percent,
    a.submitted_at
  FROM public.test_attempts a
  JOIN public.placement_tests t ON t.id = a.test_id AND t.kind = 'PLACEMENT'
  LEFT JOIN public.profiles p ON p.id = a.user_id
  WHERE a.status = 'SUBMITTED'
    AND a.submitted_at >= date_trunc('day', now()) - interval '1 day'
  ORDER BY a.submitted_at DESC;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_recent_placement_results() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_recent_placement_results() TO authenticated;

-- The existing maintenance sweep already runs on a schedule. Calling this from
-- the same sweep avoids creating a second scheduler and keeps cleanup reliable.
CREATE OR REPLACE FUNCTION public.maintenance_cleanup_placement_attempts()
RETURNS integer
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT public.cleanup_old_placement_attempts();
$$;

REVOKE ALL ON FUNCTION public.maintenance_cleanup_placement_attempts() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.maintenance_cleanup_placement_attempts() TO service_role;
