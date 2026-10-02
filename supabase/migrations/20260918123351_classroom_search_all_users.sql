-- ============================================================================
-- classroom_search_students(): the "student account" picker in the
-- subscription form (Group A -> Add subscription -> "Search by email or
-- name") previously only matched profiles with role = 'STUDENT'. Widen it to
-- search across ALL users (any role), so a teacher/owner/admin can link a
-- subscription to any registered account, not just ones already tagged
-- STUDENT. The function name and signature are unchanged (the Android app
-- calls it as classroom_search_students via
-- ClassroomRepository.searchStudents / TeacherRepository.searchStudentAccounts),
-- as are the SECURITY DEFINER role checks (caller must be signed in and be
-- TEACHER, OWNER, or ADMIN).
-- ============================================================================

CREATE OR REPLACE FUNCTION classroom_search_students(p_query TEXT)
RETURNS TABLE (id UUID, full_name TEXT, email TEXT, avatar_url TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF COALESCE(classroom_my_role(), '') NOT IN ('TEACHER', 'OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT p.id, p.full_name, p.email, p.avatar_url
  FROM profiles p
  WHERE (
      p_query IS NULL OR p_query = '' OR
      p.full_name ILIKE '%' || p_query || '%' OR
      p.email ILIKE '%' || p_query || '%'
    )
  ORDER BY p.full_name NULLS LAST
  LIMIT 30;
END;
$$;

GRANT EXECUTE ON FUNCTION classroom_search_students(TEXT) TO authenticated;
;
