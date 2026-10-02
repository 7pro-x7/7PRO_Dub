-- 7PRO Teacher Exercise Assignments
-- Turns teacher exercises into a real classroom workflow:
-- draft -> publish -> assign to an approved group -> students solve -> teacher tracks results.

CREATE TABLE IF NOT EXISTS public.exercise_group_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  exercise_id uuid NOT NULL REFERENCES public.placement_tests(id) ON DELETE CASCADE,
  group_id uuid NOT NULL REFERENCES public.teacher_groups(id) ON DELETE CASCADE,
  assigned_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  due_at timestamptz,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (exercise_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_exercise_group_assignments_exercise
  ON public.exercise_group_assignments(exercise_id);
CREATE INDEX IF NOT EXISTS idx_exercise_group_assignments_group
  ON public.exercise_group_assignments(group_id);
CREATE INDEX IF NOT EXISTS idx_exercise_group_assignments_due
  ON public.exercise_group_assignments(due_at);

ALTER TABLE public.exercise_group_assignments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS exercise_assignments_staff_all ON public.exercise_group_assignments;
CREATE POLICY exercise_assignments_staff_all
  ON public.exercise_group_assignments FOR ALL
  USING (
    EXISTS (SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid() AND p.role IN ('OWNER','ADMIN'))
  )
  WITH CHECK (
    EXISTS (SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid() AND p.role IN ('OWNER','ADMIN'))
  );

DROP POLICY IF EXISTS exercise_assignments_teacher_all ON public.exercise_group_assignments;
CREATE POLICY exercise_assignments_teacher_all
  ON public.exercise_group_assignments FOR ALL
  USING (
    EXISTS (
      SELECT 1
      FROM public.placement_tests e
      JOIN public.teacher_groups g ON g.id = exercise_group_assignments.group_id
      WHERE e.id = exercise_group_assignments.exercise_id
        AND e.kind = 'EXERCISE'
        AND e.owner_id = auth.uid()
        AND g.teacher_id = auth.uid()
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1
      FROM public.placement_tests e
      JOIN public.teacher_groups g ON g.id = exercise_group_assignments.group_id
      WHERE e.id = exercise_group_assignments.exercise_id
        AND e.kind = 'EXERCISE'
        AND e.owner_id = auth.uid()
        AND g.teacher_id = auth.uid()
    )
  );

DROP POLICY IF EXISTS exercise_assignments_student_read ON public.exercise_group_assignments;
CREATE POLICY exercise_assignments_student_read
  ON public.exercise_group_assignments FOR SELECT
  USING (
    EXISTS (
      SELECT 1
      FROM public.placement_tests e
      JOIN public.teacher_groups g ON g.id = exercise_group_assignments.group_id
      JOIN public.teacher_subscriptions s
        ON s.teacher_id = g.teacher_id
       AND s.group_name = g.name
       AND s.student_user_id = auth.uid()
      WHERE e.id = exercise_group_assignments.exercise_id
        AND e.kind = 'EXERCISE'
        AND e.status = 'PUBLISHED'
        AND g.approval_status = 'APPROVED'
        AND s.approval_status = 'APPROVED'
        AND s.status IN ('ACTIVE','DUE','OVERDUE')
        AND exercise_group_assignments.is_active
        AND (exercise_group_assignments.due_at IS NULL OR exercise_group_assignments.due_at >= now())
    )
  );

CREATE OR REPLACE FUNCTION public.assign_exercise_to_group(
  p_exercise_id uuid,
  p_group_id uuid,
  p_due_at timestamptz DEFAULT NULL
)
RETURNS public.exercise_group_assignments
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  e public.placement_tests;
  g public.teacher_groups;
  a public.exercise_group_assignments;
BEGIN
  SELECT * INTO e FROM public.placement_tests
  WHERE id = p_exercise_id AND kind = 'EXERCISE';
  IF NOT FOUND THEN RAISE EXCEPTION 'EXERCISE_NOT_FOUND'; END IF;

  SELECT * INTO g FROM public.teacher_groups WHERE id = p_group_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'GROUP_NOT_FOUND'; END IF;

  IF NOT (
    (e.owner_id = auth.uid() AND g.teacher_id = auth.uid())
    OR EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role IN ('OWNER','ADMIN'))
  ) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  IF e.status <> 'PUBLISHED' THEN RAISE EXCEPTION 'EXERCISE_MUST_BE_PUBLISHED'; END IF;
  IF g.approval_status <> 'APPROVED' THEN RAISE EXCEPTION 'GROUP_NOT_APPROVED'; END IF;

  INSERT INTO public.exercise_group_assignments(exercise_id, group_id, assigned_by, due_at, is_active)
  VALUES (p_exercise_id, p_group_id, auth.uid(), p_due_at, true)
  ON CONFLICT (exercise_id, group_id) DO UPDATE
    SET due_at = EXCLUDED.due_at, is_active = true, updated_at = now(), assigned_by = auth.uid()
  RETURNING * INTO a;
  RETURN a;
END;
$$;

CREATE OR REPLACE FUNCTION public.unassign_exercise_from_group(
  p_exercise_id uuid,
  p_group_id uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM public.placement_tests e
    JOIN public.teacher_groups g ON g.id = p_group_id
    WHERE e.id = p_exercise_id AND e.kind = 'EXERCISE'
      AND ((e.owner_id = auth.uid() AND g.teacher_id = auth.uid())
        OR EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role IN ('OWNER','ADMIN')))
  ) THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;

  UPDATE public.exercise_group_assignments
  SET is_active = false, updated_at = now()
  WHERE exercise_id = p_exercise_id AND group_id = p_group_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.my_exercise_assignments(p_exercise_id uuid)
RETURNS TABLE(
  id uuid, group_id uuid, group_name text, group_level text,
  due_at timestamptz, is_active boolean, assigned_at timestamptz
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  RETURN QUERY
  SELECT a.id, g.id, g.name, g.level, a.due_at, a.is_active, a.created_at
  FROM public.exercise_group_assignments a
  JOIN public.placement_tests e ON e.id = a.exercise_id
  JOIN public.teacher_groups g ON g.id = a.group_id
  WHERE a.exercise_id = p_exercise_id
    AND (
      e.owner_id = auth.uid()
      OR EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role IN ('OWNER','ADMIN'))
    )
  ORDER BY g.name;
END;
$$;

-- Student-facing catalogue: only exercises actually assigned to the learner's approved group(s).
CREATE OR REPLACE FUNCTION public.student_teacher_exercises(p_teacher_id uuid)
RETURNS SETOF public.placement_tests
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT DISTINCT e.*
  FROM public.placement_tests e
  JOIN public.exercise_group_assignments a ON a.exercise_id = e.id AND a.is_active
  JOIN public.teacher_groups g ON g.id = a.group_id AND g.approval_status = 'APPROVED'
  JOIN public.teacher_subscriptions s
    ON s.teacher_id = g.teacher_id
   AND s.group_name = g.name
   AND s.student_user_id = auth.uid()
  WHERE e.kind = 'EXERCISE'
    AND e.status = 'PUBLISHED'
    AND e.owner_id = p_teacher_id
    AND s.approval_status = 'APPROVED'
    AND s.status IN ('ACTIVE','DUE','OVERDUE')
  ORDER BY e.sort_order ASC, e.created_at ASC;
$$;

-- Teacher chooser for learners: only teachers who currently have an assigned exercise.
CREATE OR REPLACE FUNCTION public.student_exercise_teacher_ids()
RETURNS TABLE(teacher_id uuid, exercise_count bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT e.owner_id, count(DISTINCT e.id)
  FROM public.placement_tests e
  JOIN public.exercise_group_assignments a ON a.exercise_id = e.id AND a.is_active
  JOIN public.teacher_groups g ON g.id = a.group_id AND g.approval_status = 'APPROVED'
  JOIN public.teacher_subscriptions s
    ON s.teacher_id = g.teacher_id AND s.group_name = g.name AND s.student_user_id = auth.uid()
  WHERE e.kind = 'EXERCISE' AND e.status = 'PUBLISHED'
    AND s.approval_status = 'APPROVED' AND s.status IN ('ACTIVE','DUE','OVERDUE')
  GROUP BY e.owner_id;
$$;

REVOKE ALL ON FUNCTION public.student_exercise_teacher_ids() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.student_exercise_teacher_ids() TO authenticated;

-- Teacher/admin result board: every assigned student, including students who have not submitted yet.
CREATE OR REPLACE FUNCTION public.teacher_exercise_results(p_exercise_id uuid)
RETURNS TABLE(
  student_id uuid,
  student_name text,
  student_email text,
  group_name text,
  due_at timestamptz,
  submitted_at timestamptz,
  percent numeric,
  status text
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.placement_tests e
    WHERE e.id = p_exercise_id AND e.kind = 'EXERCISE'
      AND (e.owner_id = auth.uid()
        OR EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role IN ('OWNER','ADMIN')))
  ) THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;

  RETURN QUERY
  WITH assigned AS (
    SELECT DISTINCT s.student_user_id AS sid, g.name AS gname, a.due_at
    FROM public.exercise_group_assignments a
    JOIN public.teacher_groups g ON g.id = a.group_id AND g.approval_status = 'APPROVED'
    JOIN public.placement_tests e ON e.id = a.exercise_id
    JOIN public.teacher_subscriptions s
      ON s.teacher_id = g.teacher_id AND s.group_name = g.name
    WHERE a.exercise_id = p_exercise_id
      AND a.is_active
      AND s.student_user_id IS NOT NULL
      AND s.approval_status = 'APPROVED'
      AND s.status IN ('ACTIVE','DUE','OVERDUE')
  )
  SELECT
    a.sid,
    COALESCE(p.full_name, 'Student'),
    p.email,
    a.gname,
    a.due_at,
    ta.submitted_at,
    ta.percent,
    CASE WHEN ta.id IS NULL THEN 'PENDING' ELSE ta.status END
  FROM assigned a
  JOIN public.profiles p ON p.id = a.sid
  LEFT JOIN LATERAL (
    SELECT t.* FROM public.test_attempts t
    WHERE t.test_id = p_exercise_id AND t.user_id = a.sid
    ORDER BY CASE WHEN t.status = 'SUBMITTED' THEN 0 ELSE 1 END, t.submitted_at DESC NULLS LAST, t.started_at DESC
    LIMIT 1
  ) ta ON true
  ORDER BY CASE WHEN ta.id IS NULL THEN 0 ELSE 1 END, p.full_name;
END;
$$;

REVOKE ALL ON FUNCTION public.assign_exercise_to_group(uuid,uuid,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.unassign_exercise_from_group(uuid,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.my_exercise_assignments(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.student_teacher_exercises(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.teacher_exercise_results(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.assign_exercise_to_group(uuid,uuid,timestamptz) TO authenticated;
GRANT EXECUTE ON FUNCTION public.unassign_exercise_from_group(uuid,uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.my_exercise_assignments(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.student_teacher_exercises(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.teacher_exercise_results(uuid) TO authenticated;

COMMENT ON TABLE public.exercise_group_assignments IS
  'Assignment layer for teacher exercises. Students see only exercises assigned to their approved active group subscription.';
;
