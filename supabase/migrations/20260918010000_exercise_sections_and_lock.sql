-- Teacher exercises gain grouping into named levels, and a teacher-controlled lock.
-- Mirrors course_sections so the app's existing grouping logic transfers directly.
-- APPLIED DIRECTLY to the live 7pro-x7 project on 2026-09-18 via the Supabase MCP tools.

CREATE TABLE IF NOT EXISTS public.exercise_sections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.placement_tests
  ADD COLUMN IF NOT EXISTS section_id uuid REFERENCES public.exercise_sections(id) ON DELETE SET NULL;
ALTER TABLE public.placement_tests
  ADD COLUMN IF NOT EXISTS is_locked boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_placement_tests_section ON public.placement_tests(section_id);
CREATE INDEX IF NOT EXISTS idx_exercise_sections_owner ON public.exercise_sections(owner_id, sort_order);

ALTER TABLE public.exercise_sections ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS exercise_sections_read ON public.exercise_sections;
CREATE POLICY exercise_sections_read ON public.exercise_sections
FOR SELECT USING (
  owner_id = auth.uid()
  OR has_permission('tests.manage'::text)
  OR EXISTS (
    SELECT 1 FROM placement_tests t
    WHERE t.section_id = exercise_sections.id
      AND t.kind = 'EXERCISE' AND t.status = 'PUBLISHED'::test_status
  )
);

DROP POLICY IF EXISTS exercise_sections_owner_write ON public.exercise_sections;
CREATE POLICY exercise_sections_owner_write ON public.exercise_sections
FOR ALL USING (owner_id = auth.uid() AND is_approved_teacher(auth.uid()))
WITH CHECK (owner_id = auth.uid() AND is_approved_teacher(auth.uid()));

DROP POLICY IF EXISTS exercise_sections_staff_write ON public.exercise_sections;
CREATE POLICY exercise_sections_staff_write ON public.exercise_sections
FOR ALL USING (has_permission('tests.manage'::text))
WITH CHECK (has_permission('tests.manage'::text));

CREATE OR REPLACE FUNCTION public.check_exercise_section_owner()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
  IF NEW.section_id IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM exercise_sections s WHERE s.id = NEW.section_id AND s.owner_id = NEW.owner_id) THEN
      RAISE EXCEPTION 'EXERCISE_SECTION_NOT_YOURS';
    END IF;
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_check_exercise_section_owner ON public.placement_tests;
CREATE TRIGGER trg_check_exercise_section_owner
BEFORE INSERT OR UPDATE OF section_id ON public.placement_tests
FOR EACH ROW EXECUTE FUNCTION public.check_exercise_section_owner();
