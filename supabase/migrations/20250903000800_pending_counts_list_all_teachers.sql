-- ============================================================================
-- Owner dashboard: pending_request_counts must list ALL teachers
--
-- Previously the function grouped approval_requests rows directly, so a teacher
-- with zero pending requests never appeared on the owner dashboard at all.
-- The owner should see every teacher with their pending count (0 included).
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
  SELECT tp.id AS teacher_id,
         p.full_name AS teacher_name,
         p.avatar_url AS teacher_avatar,
         COALESCE(ar.pending_count, 0) AS pending_count
  FROM public.teacher_profiles tp
  LEFT JOIN public.profiles p ON p.id = tp.id
  LEFT JOIN (
    SELECT teacher_id, count(*) AS pending_count
    FROM public.approval_requests
    WHERE status = 'PENDING'
    GROUP BY teacher_id
  ) ar ON ar.teacher_id = tp.id
  ORDER BY pending_count DESC, p.full_name ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
GRANT EXECUTE ON FUNCTION public.pending_request_counts() TO authenticated;
-- ============================================================================
-- Refresh PostgREST schema cache
-- ============================================================================
NOTIFY pgrst, 'reload schema';
