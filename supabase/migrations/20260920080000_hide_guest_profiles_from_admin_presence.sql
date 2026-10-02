-- Anonymous classroom accounts are technical access profiles, not platform users.
-- Keep them available for the classroom cleanup flow, but never expose them in staff screens.

CREATE OR REPLACE FUNCTION public.admin_online_users()
RETURNS TABLE (
  user_id uuid,
  full_name text,
  email text,
  role text,
  avatar_url text,
  last_seen_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (public.is_owner() OR public.has_permission('analytics.read')) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT
    p.id,
    COALESCE(p.full_name, ''),
    COALESCE(p.email, ''),
    p.role::text,
    p.avatar_url,
    p.last_seen_at
  FROM public.profiles p
  WHERE NOT COALESCE(p.is_guest, false)
    AND p.last_seen_at > now() - interval '90 seconds'
  ORDER BY p.last_seen_at DESC
  LIMIT 500;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_online_users() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_online_users() TO authenticated;

-- A guest entering a meeting must not become an "online app user" through the heartbeat.
CREATE OR REPLACE FUNCTION public.ping_presence()
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE public.profiles
  SET last_seen_at = now()
  WHERE id = auth.uid()
    AND NOT COALESCE(is_guest, false);
$$;

REVOKE ALL ON FUNCTION public.ping_presence() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ping_presence() TO authenticated;
