-- ============================================================================
-- BUG: teachers could not add a subscriber at all (every insert failed with
-- a 403).
--
-- ROOT CAUSE: teacher_subscriptions.approval_status still defaulted to
-- 'APPROVED'. It was first added with that default in
-- 20250903000090_create_approval_system. The later rebuild
-- (20260906163132_clean_subscription_system_rebuild_v2) tried to change it
-- to 'PENDING' via:
--     ALTER TABLE ... ADD COLUMN IF NOT EXISTS approval_status ... DEFAULT 'PENDING'
-- but the column already existed, so IF NOT EXISTS made the whole clause a
-- silent no-op -- the default was never actually changed. Verified live:
-- information_schema.columns still showed 'APPROVED'::text.
--
-- IMPACT: TeacherRepository.saveSubscription() (the app's "add student" flow)
-- never sets approval_status in its insert payload -- it relies entirely on
-- the column default to land on PENDING. Because the real default was
-- APPROVED, every teacher-submitted insert produced a NEW row with
-- approval_status='APPROVED', which the INSERT policy
-- (teachers_insert_own_subscriptions, WITH CHECK approval_status='PENDING')
-- then rejected outright -- a 403 on every single attempt to add a student.
--
-- Same latent mismatch existed on teacher_groups (default was 'APPROVED',
-- intended 'INACTIVE' per 20250904000200_group_activation_flow). Not
-- currently triggered because TeacherRepository.createGroup() happens to
-- always pass approval_status explicitly, but fixed here too so the column
-- itself matches its documented lifecycle and any future insert path is
-- safe by default.
-- ============================================================================

ALTER TABLE public.teacher_subscriptions
  ALTER COLUMN approval_status SET DEFAULT 'PENDING';

ALTER TABLE public.teacher_groups
  ALTER COLUMN approval_status SET DEFAULT 'INACTIVE';

NOTIFY pgrst, 'reload schema';
;
