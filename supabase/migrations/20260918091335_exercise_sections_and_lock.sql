-- Teacher exercises gain two things courses already had: grouping into named levels, and a
-- lock the teacher controls.
--
-- Deliberately mirrors course_sections rather than inventing a new shape, so the app's
-- existing "group children under their section, loose ones last" logic transfers directly.

CREATE TABLE IF NOT EXISTS public.exercise_sections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- An exercise with no section is not an error: it simply hasn't been filed into a level yet,
-- and the UI shows those after the grouped ones. Deleting a level therefore frees its
-- exercises instead of destroying them.
ALTER TABLE public.placement_tests
  ADD COLUMN IF NOT EXISTS section_id uuid REFERENCES public.exercise_sections(id) ON DELETE SET NULL;

-- The teacher-controlled lock. Locked is the cautious default for nothing: existing rows stay
-- unlocked so this migration can't silently hide anyone's published work.
ALTER TABLE public.placement_tests
  ADD COLUMN IF NOT EXISTS is_locked boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_placement_tests_section ON public.placement_tests(section_id);
CREATE INDEX IF NOT EXISTS idx_exercise_sections_owner ON public.exercise_sections(owner_id, sort_order);

ALTER TABLE public.exercise_sections ENABLE ROW LEVEL SECURITY;

-- Read: the owner, staff, or anyone who can see at least one published exercise filed under it.
-- Without that last clause a student would receive exercises whose level name they cannot read,
-- and the grouped list would render with blank headers.
DROP POLICY IF EXISTS exercise_sections_read ON public.exercise_sections;
CREATE POLICY exercise_sections_read ON public.exercise_sections
FOR SELECT
USING (
  owner_id = auth.uid()
  OR has_permission('tests.manage'::text)
  OR EXISTS (
    SELECT 1 FROM placement_tests t
    WHERE t.section_id = exercise_sections.id
      AND t.kind = 'EXERCISE'
      AND t.status = 'PUBLISHED'::test_status
  )
);

DROP POLICY IF EXISTS exercise_sections_owner_write ON public.exercise_sections;
CREATE POLICY exercise_sections_owner_write ON public.exercise_sections
FOR ALL
USING (owner_id = auth.uid() AND is_approved_teacher(auth.uid()))
WITH CHECK (owner_id = auth.uid() AND is_approved_teacher(auth.uid()));

DROP POLICY IF EXISTS exercise_sections_staff_write ON public.exercise_sections;
CREATE POLICY exercise_sections_staff_write ON public.exercise_sections
FOR ALL
USING (has_permission('tests.manage'::text))
WITH CHECK (has_permission('tests.manage'::text));

-- A section may only ever hold exercises owned by the same teacher. Enforced here rather than
-- trusted to the client, because the write policy on placement_tests checks the exercise's own
-- owner and would happily accept someone else's section id.
CREATE OR REPLACE FUNCTION public.check_exercise_section_owner()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NEW.section_id IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1 FROM exercise_sections s
      WHERE s.id = NEW.section_id AND s.owner_id = NEW.owner_id
    ) THEN
      RAISE EXCEPTION 'EXERCISE_SECTION_NOT_YOURS';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_check_exercise_section_owner ON public.placement_tests;
CREATE TRIGGER trg_check_exercise_section_owner
BEFORE INSERT OR UPDATE OF section_id ON public.placement_tests
FOR EACH ROW EXECUTE FUNCTION public.check_exercise_section_owner();
;
