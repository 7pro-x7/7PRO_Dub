-- Multiple banners on one screen were impossible: a unique constraint plus a duplicate index on
-- (screen, format) allowed exactly one placement per screen per format. The table already carries
-- a `section` column meant to name the slot within a screen ("TOP", "MID_LIST", "BOTTOM"), so
-- uniqueness moves there.
--
-- Existing rows keep section NULL, which now means "the screen's default slot" — they stay valid
-- and keep working untouched.

alter table public.admob_placements
  drop constraint if exists admob_placements_screen_format_unique;
drop index if exists public.uq_admob_placement_screen_format;

-- coalesce so a NULL section behaves as one concrete slot rather than bypassing uniqueness
-- (NULLs never compare equal, so a plain 3-column index would allow endless duplicates).
create unique index if not exists uq_admob_placement_screen_format_section
  on public.admob_placements (screen, format, coalesce(section, ''));

-- The app matches a slot by section, so it has to come back from the config RPC.
create or replace function public.ad_config(p_screen text, p_course_id uuid default null::uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
DECLARE
    v_global_enabled boolean;
    v_placements jsonb;
    v_course_free boolean := true;
BEGIN
    SELECT COALESCE(
        (SELECT value::boolean FROM app_settings WHERE key = 'ads.enabled'),
        true
    ) INTO v_global_enabled;

    IF NOT v_global_enabled THEN
        RETURN jsonb_build_object('enabled', false, 'reason', 'ads_disabled_globally', 'placements', '[]'::jsonb);
    END IF;

    IF p_course_id IS NOT NULL THEN
        SELECT COALESCE(is_free, false) OR base_price <= 0
        INTO v_course_free
        FROM courses WHERE id = p_course_id;

        IF NOT v_course_free THEN
            IF COALESCE((SELECT value::boolean FROM app_settings WHERE key = 'ads.free_content_only'), true) THEN
                RETURN jsonb_build_object('enabled', false, 'reason', 'paid_content', 'placements', '[]'::jsonb);
            END IF;
        END IF;
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', ap.id, 'format', ap.format, 'ad_unit_id', ap.ad_unit_id,
                'section', ap.section,
                'max_impressions', ap.max_impressions_per_session,
                'display_interval_seconds', ap.display_interval_seconds,
                'sort_order', ap.sort_order
            ) ORDER BY ap.sort_order, ap.section NULLS FIRST
        ),
        '[]'::jsonb
    ) INTO v_placements
    FROM admob_placements ap
    WHERE ap.screen = p_screen AND ap.is_enabled = true
      AND ap.ad_unit_id IS NOT NULL AND ap.ad_unit_id != '';

    RETURN jsonb_build_object(
        'enabled', jsonb_array_length(v_placements) > 0,
        'reason', CASE WHEN jsonb_array_length(v_placements) = 0 THEN 'no_placements' ELSE NULL END,
        'placements', v_placements
    );
END;
$function$;;
