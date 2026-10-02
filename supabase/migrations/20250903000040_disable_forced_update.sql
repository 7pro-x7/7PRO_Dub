-- ============================================================================
-- Disable forced update gate
--
-- The app force_update setting is blocking access to the entire app because
-- the current build's VERSION_CODE is below the min_build threshold.
-- Setting force_update to false allows all builds to pass through.
-- ============================================================================

UPDATE public.app_settings
SET value = 'false'::jsonb
WHERE key = 'app.force_update';
-- Also lower min_build to 1 so even if force_update is re-enabled,
-- the current build won't be blocked.
UPDATE public.app_settings
SET value = '1'::jsonb
WHERE key = 'app.min_build';
