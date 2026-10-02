-- ============================================================================
-- Complete fix: disable forced update + ensure teacher_groups exists
-- ============================================================================

-- 1. Disable the forced update gate
UPDATE public.app_settings SET value = 'false'::jsonb WHERE key = 'app.force_update';
UPDATE public.app_settings SET value = '1'::jsonb WHERE key = 'app.min_build';
UPDATE public.app_settings SET value = '""'::jsonb WHERE key = 'app.update_message';
-- 2. Ensure teacher_groups table exists (was missing from live database)
CREATE TABLE IF NOT EXISTS public.teacher_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  level TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(teacher_id, name)
);
CREATE INDEX IF NOT EXISTS idx_teacher_groups_teacher ON public.teacher_groups(teacher_id);
ALTER TABLE public.teacher_groups ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'teacher_groups_self_access' AND tablename = 'teacher_groups') THEN
    CREATE POLICY "teacher_groups_self_access" ON public.teacher_groups FOR ALL USING (auth.uid() = teacher_id);
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'teacher_groups_staff_read' AND tablename = 'teacher_groups') THEN
    CREATE POLICY "teacher_groups_staff_read" ON public.teacher_groups FOR SELECT USING (
      EXISTS (SELECT 1 FROM public.profiles WHERE profiles.id = auth.uid() AND profiles.role IN ('OWNER', 'ADMIN'))
    );
  END IF;
END $$;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.teacher_groups TO authenticated;
-- 3. Add missing level column to teacher_subscriptions
ALTER TABLE public.teacher_subscriptions ADD COLUMN IF NOT EXISTS level TEXT;
COMMENT ON COLUMN public.teacher_subscriptions.level IS 'Proficiency level or class tier for this subscription group';
-- 4. Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';
