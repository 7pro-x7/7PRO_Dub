-- Teachers can copy the OWNER's published exercises into their own account.
--
-- Rules (enforced here, in the database, not in the app):
--   * Only exercises whose author has role OWNER can be copied. Another teacher's exercise is
--     rejected outright, whatever id the caller sends, so it can never be listed or copied.
--   * Only PUBLISHED owner exercises are offered.
--   * The copy is a brand-new exercise owned by the teacher: DRAFT, unlocked, no level, placed
--     last in the teacher's own order. Editing it never touches the owner's original, and the
--     owner editing/deleting the original never touches the copy.
--   * Questions are copied with new ids (translations, points, cartoon dialogue, is_active come
--     along). Media is copied by URL, so the files are shared, not duplicated.
--   * Student attempts / results / group assignments are NOT copied.
--
-- Copies are done with jsonb_populate_record so every column of placement_tests / test_questions
-- travels with the row, including ones added by later migrations.

ALTER TABLE public.placement_tests
  ADD COLUMN IF NOT EXISTS copied_from uuid REFERENCES public.placement_tests(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_placement_tests_copied_from ON public.placement_tests(copied_from);

-- The list a teacher browses: only the owner's published exercises.
CREATE OR REPLACE FUNCTION public.owner_exercises_available_to_copy()
RETURNS TABLE(
  id uuid,
  title text,
  title_ar text,
  title_en text,
  description text,
  description_ar text,
  description_en text,
  question_count integer,
  time_limit_seconds integer,
  questions_total bigint,
  sort_order integer,
  already_copied boolean
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
  IF NOT public.is_approved_teacher(auth.uid()) THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;

  RETURN QUERY
  SELECT e.id, e.title, e.title_ar, e.title_en,
         e.description, e.description_ar, e.description_en,
         e.question_count, e.time_limit_seconds,
         (SELECT count(*) FROM public.test_questions q WHERE q.test_id = e.id),
         e.sort_order,
         EXISTS (SELECT 1 FROM public.placement_tests c
                 WHERE c.copied_from = e.id AND c.owner_id = auth.uid() AND c.kind = 'EXERCISE')
  FROM public.placement_tests e
  JOIN public.profiles op ON op.id = e.owner_id AND op.role = 'OWNER'
  WHERE e.kind = 'EXERCISE'
    AND e.status = 'PUBLISHED'
  ORDER BY e.sort_order ASC, e.created_at ASC;
END;
$$;

-- The copy itself. Returns the id of the teacher's new exercise.
CREATE OR REPLACE FUNCTION public.copy_owner_exercise(p_exercise_id uuid)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  me uuid := auth.uid();
  src public.placement_tests;
  new_id uuid := gen_random_uuid();
  next_order integer;
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
  IF NOT public.is_approved_teacher(me) THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;

  SELECT * INTO src FROM public.placement_tests
  WHERE id = p_exercise_id AND kind = 'EXERCISE';
  IF NOT FOUND THEN RAISE EXCEPTION 'EXERCISE_NOT_FOUND'; END IF;

  -- The rule that keeps other teachers' work private: the source must belong to an OWNER.
  IF NOT EXISTS (
    SELECT 1 FROM public.profiles p WHERE p.id = src.owner_id AND p.role = 'OWNER'
  ) THEN
    RAISE EXCEPTION 'ONLY_OWNER_EXERCISES_CAN_BE_COPIED';
  END IF;

  IF src.status <> 'PUBLISHED' THEN RAISE EXCEPTION 'EXERCISE_NOT_PUBLISHED'; END IF;

  SELECT count(*) INTO next_order FROM public.placement_tests
  WHERE kind = 'EXERCISE' AND owner_id = me;

  INSERT INTO public.placement_tests
  SELECT (r).*
  FROM (
    SELECT jsonb_populate_record(
             NULL::public.placement_tests,
             to_jsonb(src) || jsonb_build_object(
               'id', new_id,
               'owner_id', me,
               'status', 'DRAFT',
               'section_id', NULL,
               'is_locked', false,
               'sort_order', next_order,
               'copied_from', src.id,
               'created_at', now(),
               'updated_at', now()
             )
           ) AS r
  ) s;

  INSERT INTO public.test_questions
  SELECT (x.r).*
  FROM public.test_questions q
  CROSS JOIN LATERAL (
    SELECT jsonb_populate_record(
             NULL::public.test_questions,
             to_jsonb(q) || jsonb_build_object(
               'id', gen_random_uuid(),
               'test_id', new_id,
               'created_at', now()
             )
           ) AS r
  ) x
  WHERE q.test_id = src.id
  ORDER BY q.sort_order;

  RETURN new_id;
END;
$$;

REVOKE ALL ON FUNCTION public.owner_exercises_available_to_copy() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.copy_owner_exercise(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.owner_exercises_available_to_copy() TO authenticated;
GRANT EXECUTE ON FUNCTION public.copy_owner_exercise(uuid) TO authenticated;

COMMENT ON FUNCTION public.copy_owner_exercise(uuid) IS
  'Teacher copies a published OWNER exercise into their own account as a draft. Any other teacher''s exercise is rejected.';
