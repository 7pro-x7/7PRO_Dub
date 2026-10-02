-- The admob_placements.free_content_only column existed and was editable, but ad_config never
-- read it: on a paid course the function returned early for the WHOLE screen based only on the
-- global ads.free_content_only setting. So the owner had no way to say "show this banner on paid
-- courses but not that one" — the per-placement switch was decorative.
--
-- Now the decision is made per placement:
--   * global ads.enabled = false        -> nothing, anywhere (unchanged master kill switch)
--   * global ads.free_content_only=true -> nothing on paid content, whatever placements say
--                                          (unchanged master override, kept for compatibility)
--   * otherwise, on a PAID course       -> only placements with free_content_only = false
--   * on FREE content                   -> every enabled placement, as before
create or replace function public.ad_config(p_screen text, p_course_id uuid default null::uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
DECLARE
    v_global_enabled boolean;
    v_global_free_only boolean;
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

    SELECT COALESCE(
        (SELECT value::boolean FROM app_settings WHERE key = 'ads.free_content_only'),
        true
    ) INTO v_global_free_only;

    IF p_course_id IS NOT NULL THEN
        SELECT COALESCE(is_free, false) OR base_price <= 0
        INTO v_course_free
        FROM courses WHERE id = p_course_id;

        -- Master override: when on, paid content is ad-free no matter how placements are set.
        IF NOT v_course_free AND v_global_free_only THEN
            RETURN jsonb_build_object('enabled', false, 'reason', 'paid_content', 'placements', '[]'::jsonb);
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
      AND ap.ad_unit_id IS NOT NULL AND ap.ad_unit_id != ''
      -- Per-placement decision: a placement marked free-content-only sits out on paid courses,
      -- while its neighbours on the same screen can still run.
      AND (v_course_free OR ap.free_content_only = false);

    RETURN jsonb_build_object(
        'enabled', jsonb_array_length(v_placements) > 0,
        'reason', CASE
            WHEN jsonb_array_length(v_placements) = 0 AND NOT v_course_free THEN 'paid_content'
            WHEN jsonb_array_length(v_placements) = 0 THEN 'no_placements'
            ELSE NULL
        END,
        'placements', v_placements
    );
END;
$function$;;
