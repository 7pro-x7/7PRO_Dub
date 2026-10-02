
-- The app calls AdBanner() on HOME (default + BOTTOM section), PROFILE, TESTS, EXERCISES,
-- COURSE_LIST, COURSE_DETAIL and FREE_LESSON, but admob_placements only had a row for the
-- HOME default banner. Every other screen's server RPC correctly answered "no placement here",
-- so the client obeyed and showed nothing -- by design, not a bug in the app code.
-- This adds the missing rows, reusing the same live banner ad unit already serving on HOME.

INSERT INTO public.admob_placements (screen, section, format, ad_unit_id, is_enabled, display_interval_seconds)
SELECT v.screen, v.section, 'BANNER', 'ca-app-pub-2143545712755970/7727635098', true, 10
FROM (VALUES
    ('HOME', 'BOTTOM'),
    ('PROFILE', NULL),
    ('TESTS', NULL),
    ('EXERCISES', NULL),
    ('COURSE_LIST', NULL),
    ('COURSE_DETAIL', NULL),
    ('FREE_LESSON', NULL)
) AS v(screen, section)
WHERE NOT EXISTS (
    SELECT 1 FROM public.admob_placements p
    WHERE p.screen = v.screen
      AND p.format = 'BANNER'
      AND p.section IS NOT DISTINCT FROM v.section
);
;
