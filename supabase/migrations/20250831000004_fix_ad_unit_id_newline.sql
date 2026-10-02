-- ============================================================================
-- Fix trailing newline in ad unit IDs that prevents AdMob from loading ads
-- ============================================================================

UPDATE public.admob_placements
SET ad_unit_id = trim(TRAILING E'\n' FROM ad_unit_id)
WHERE ad_unit_id LIKE '%\n';
