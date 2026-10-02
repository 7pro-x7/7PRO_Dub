-- Who is using the app right now.
--
-- The owner console's "online now" tile is a COUNT (owner_analytics.online_now: profiles whose
-- last_seen_at was touched in the last 90 seconds by ping_presence()). This is the matching LIST,
-- so tapping the tile can show the actual people behind the number. It uses the exact same 90 s
-- window, so the list length always equals the number on the tile.
--
-- Same authorization as the count it explains (analytics.read), plus the owner.
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
  WHERE p.last_seen_at > now() - interval '90 seconds'
  ORDER BY p.last_seen_at DESC
  LIMIT 500;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_online_users() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_online_users() TO authenticated;

-- Supabase's default privileges grant EXECUTE on new public functions straight to anon, which
-- REVOKE ... FROM PUBLIC does not remove. The function already refuses non-staff callers, but
-- closing the door outright matches how the other money/analytics endpoints are locked down.
-- (Applied on production as "admin_online_users_revoke_anon".)
REVOKE ALL ON FUNCTION public.admin_online_users() FROM anon;
GRANT EXECUTE ON FUNCTION public.admin_online_users() TO authenticated;
