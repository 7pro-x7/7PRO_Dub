-- ============================================================================
-- Update ad_config: show ads on ALL content (free + paid) and for all users
-- ============================================================================

DROP FUNCTION IF EXISTS public.ad_config(text, uuid);
CREATE OR REPLACE FUNCTION ad_config(p_screen text, p_course_id uuid DEFAULT NULL)
RETURNS jsonb AS $$
DECLARE
  v_global boolean;
BEGIN
  v_global := coalesce((SELECT (value#>>'{}')::boolean FROM public.app_settings WHERE key = 'ads.global_enabled'), false);
  IF NOT v_global THEN RETURN jsonb_build_object('enabled', false, 'placements', '[]'::jsonb); END IF;

  -- Ads are shown on ALL content: free, paid, subscribers — no restrictions.
  RETURN jsonb_build_object('enabled', true, 'placements', coalesce(((
    SELECT jsonb_agg(jsonb_build_object(
      'id', id,
      'section', section,
      'format', format,
      'ad_unit_id', ad_unit_id,
      'frequency', frequency,
      'spacing', spacing,
      'max_impressions', max_impressions_per_session,
      'display_interval_seconds', display_interval_seconds
    ) ORDER BY sort_order)
    FROM public.admob_placements WHERE screen = p_screen AND is_enabled
  )), '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
