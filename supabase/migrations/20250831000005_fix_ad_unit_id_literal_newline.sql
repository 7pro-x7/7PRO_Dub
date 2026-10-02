-- ============================================================================
-- Fix trailing newline characters in ad unit IDs
-- ============================================================================

-- Remove actual newline characters
UPDATE public.admob_placements
SET ad_unit_id = replace(ad_unit_id, E'\n', '')
WHERE ad_unit_id LIKE '%' || E'\n';
-- Remove literal backslash-n sequences  
UPDATE public.admob_placements
SET ad_unit_id = replace(ad_unit_id, '\n', '')
WHERE ad_unit_id LIKE '%\n%';
-- Final safety: trim any whitespace
UPDATE public.admob_placements
SET ad_unit_id = trim(ad_unit_id);
