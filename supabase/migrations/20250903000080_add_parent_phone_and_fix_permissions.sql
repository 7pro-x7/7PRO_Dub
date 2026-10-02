-- ============================================================================
-- Add parent_phone column and fix OWNER/ADMIN permissions on teacher_groups
-- ============================================================================

-- 1. Add parent_phone column to teacher_subscriptions
ALTER TABLE public.teacher_subscriptions ADD COLUMN IF NOT EXISTS parent_phone TEXT;
COMMENT ON COLUMN public.teacher_subscriptions.parent_phone IS 'Parent/guardian phone number for the subscription';
-- 2. Fix OWNER/ADMIN permissions on teacher_groups
-- Drop the old read-only policy
DROP POLICY IF EXISTS teacher_groups_staff_read ON public.teacher_groups;
-- Create full access policy for OWNER/ADMIN
CREATE POLICY teacher_groups_staff_full_access ON public.teacher_groups
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.profiles
            WHERE profiles.id = auth.uid()
            AND profiles.role IN ('OWNER', 'ADMIN')
        )
    );
-- 3. Reload PostgREST schema cache
NOTIFY pgrst, 'reload schema';
