-- ============================================================================
-- Fix pending_request_counts: "column reference \"teacher_id\" is ambiguous"
--
-- The function is declared RETURNS TABLE (teacher_id UUID, teacher_name TEXT,
-- teacher_avatar TEXT, pending_count BIGINT). Inside the RETURN QUERY body the
-- query used unqualified names (teacher_id, pending_count) which PostgreSQL
-- cannot tell apart from the OUT parameters of RETURNS TABLE and fails with
-- error 42702 ("column reference is ambiguous — could refer to either a
-- PL/pgSQL variable or a table column").
--
-- Fix: fully qualify every column reference and never shadow the OUT
-- parameter names inside the function body. The inner aggregation is done in
-- a derived table, so no table in the outer query exposes a column named
-- like an OUT parameter.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.pending_request_counts()
RETURNS TABLE (
  teacher_id UUID,
  teacher_name TEXT,
  teacher_avatar TEXT,
  pending_count BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT tp.id,
         p.full_name,
         p.avatar_url,
         COALESCE(ar.pending_count, 0)
  FROM public.teacher_profiles tp
  LEFT JOIN public.profiles p ON p.id = tp.id
  LEFT JOIN (
    SELECT approved.teacher_id, count(*) AS pending_count
    FROM public.approval_requests approved
    WHERE approved.status = 'PENDING'
    GROUP BY approved.teacher_id
  ) ar ON ar.teacher_id = tp.id
  ORDER BY ar.pending_count DESC NULLS LAST, p.full_name ASC NULLS LAST;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.pending_request_counts() TO authenticated;
-- ============================================================================
-- Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
