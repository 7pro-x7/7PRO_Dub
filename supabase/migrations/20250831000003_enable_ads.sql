-- ============================================================================
-- Enable ads globally so AdMob banners can display
-- ============================================================================

-- Insert ads.global_enabled if it doesn't exist (default to true)
INSERT INTO public.app_settings (key, value, description)
VALUES ('ads.global_enabled', 'true'::jsonb, 'Enable AdMob ads globally')
ON CONFLICT (key) DO NOTHING;
