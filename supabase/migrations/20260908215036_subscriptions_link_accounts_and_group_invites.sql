-- ============================================================================
-- 1) Link a subscription row to the student's ACTUAL registered app account.
--
--    Previously, adding a student to a group only recorded a free-text
--    "level" plus a free-text student_name. The add-student form now looks
--    the learner up by their registered email (or name) instead of asking
--    the teacher/owner/admin to type a level, so the subscription can be
--    tied to a real profile. `level` itself is untouched here — it is still
--    inherited automatically from the group.
-- ============================================================================

ALTER TABLE teacher_subscriptions
  ADD COLUMN IF NOT EXISTS student_user_id UUID
    CONSTRAINT teacher_subscriptions_student_user_id_fkey
    REFERENCES profiles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_teacher_subscriptions_student_user_id
  ON teacher_subscriptions(student_user_id);

COMMENT ON COLUMN teacher_subscriptions.student_user_id IS
  'The learner''s actual registered profile, looked up by email/name when the subscription is created. Only account-linked subscriptions can be invited to a Virtual Classroom session.';

-- ============================================================================
-- 2) classroom_my_subscribed_students
--
--    Feeds the "create session" screen: instead of a global student search,
--    a TEACHER (or OWNER/ADMIN acting for their own account) sees only the
--    people already subscribed with them, grouped by class, so they can
--    invite a whole group or hand-pick individual students.
-- ============================================================================
CREATE OR REPLACE FUNCTION classroom_my_subscribed_students()
RETURNS TABLE (
  group_id UUID,
  group_name TEXT,
  group_level TEXT,
  student_id UUID,
  student_full_name TEXT,
  student_email TEXT,
  student_avatar_url TEXT
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF classroom_my_role() NOT IN ('TEACHER', 'OWNER', 'ADMIN') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT
    g.id, g.name, g.level,
    p.id, p.full_name, p.email, p.avatar_url
  FROM teacher_groups g
  JOIN teacher_subscriptions s
    ON s.teacher_id = g.teacher_id AND s.group_name = g.name
  JOIN profiles p ON p.id = s.student_user_id
  WHERE g.teacher_id = auth.uid()
    AND s.student_user_id IS NOT NULL
    AND s.approval_status = 'APPROVED'
    AND s.status != 'PAUSED'
  ORDER BY g.name, p.full_name NULLS LAST;
END;
$$;

REVOKE ALL ON FUNCTION classroom_my_subscribed_students() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_my_subscribed_students() TO authenticated;
;
