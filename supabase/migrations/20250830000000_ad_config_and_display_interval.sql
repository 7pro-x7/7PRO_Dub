-- ============================================================================
-- Fix AdMob: add display_interval_seconds column and update ad_config to include it
-- ============================================================================

-- 1. Add display_interval_seconds column if missing
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'admob_placements'
        AND column_name = 'display_interval_seconds'
    ) THEN
        ALTER TABLE admob_placements
        ADD COLUMN display_interval_seconds INTEGER NOT NULL DEFAULT 90;
    END IF;
END $$;
-- 1b. Add unique constraint on (screen, format) to prevent duplicates
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'admob_placements_screen_format_unique'
    ) THEN
        ALTER TABLE admob_placements
        ADD CONSTRAINT admob_placements_screen_format_unique UNIQUE (screen, format);
    END IF;
END $$;
-- 2. Update ad_config to include display_interval_seconds in response
CREATE OR REPLACE FUNCTION ad_config(p_screen text, p_course_id uuid DEFAULT NULL)
RETURNS jsonb AS $$
DECLARE
  v_global boolean;
  v_paid boolean := false;
BEGIN
  v_global := coalesce((SELECT (value#>>'{}')::boolean FROM public.app_settings WHERE key = 'ads.global_enabled'), false);
  IF NOT v_global THEN RETURN jsonb_build_object('enabled', false, 'placements', '[]'::jsonb); END IF;

  IF p_course_id IS NOT NULL THEN
    SELECT (NOT c.is_free) OR NOT c.ads_enabled INTO v_paid FROM public.courses c WHERE c.id = p_course_id;
    IF coalesce(v_paid, false) THEN RETURN jsonb_build_object('enabled', false, 'placements', '[]'::jsonb, 'reason', 'PAID_CONTENT'); END IF;
  END IF;

  IF EXISTS (SELECT 1 FROM public.subscriptions WHERE user_id = auth.uid() AND status IN ('ACTIVE', 'EXPIRING')) THEN
    RETURN jsonb_build_object('enabled', false, 'placements', '[]'::jsonb, 'reason', 'SUBSCRIBER');
  END IF;

  RETURN jsonb_build_object('enabled', true, 'placements', coalesce((
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
  ), '[]'::jsonb));
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
