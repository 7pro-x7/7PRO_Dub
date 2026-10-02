-- ============================================================================
-- Add subscription_stats_all - the old APK calls this instead of subscription_stats
-- Also ensure all subscription RPCs are clean (no version checks)
-- ============================================================================

-- Create subscription_stats_all as an alias for subscription_stats (no teacher filter)
DROP FUNCTION IF EXISTS public.subscription_stats_all();
CREATE OR REPLACE FUNCTION public.subscription_stats_all()
RETURNS JSONB AS $$
BEGIN
  RETURN public.subscription_stats(NULL);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.subscription_stats_all() TO authenticated;
-- Also ensure subscription_stats has the correct overloaded version
DROP FUNCTION IF EXISTS public.subscription_stats(UUID);
CREATE OR REPLACE FUNCTION public.subscription_stats(p_teacher UUID DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
  result JSONB;
  total_count INT;
  due_this_week INT;
  overdue_count INT;
  total_monthly NUMERIC(12,2);
BEGIN
  PERFORM refresh_subscription_statuses();

  SELECT count(*), count(*) FILTER (WHERE status = 'DUE'), count(*) FILTER (WHERE status = 'OVERDUE'), COALESCE(sum(monthly_amount), 0)
  INTO total_count, due_this_week, overdue_count, total_monthly
  FROM teacher_subscriptions
  WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
    AND status != 'PAUSED';

  due_this_week := (SELECT count(*) FROM teacher_subscriptions
    WHERE (p_teacher IS NULL OR teacher_id = p_teacher)
      AND status IN ('ACTIVE', 'DUE')
      AND next_renewal_date <= current_date + INTERVAL '7 days'
      AND next_renewal_date >= current_date);

  result := jsonb_build_object(
    'total_subscriptions', total_count,
    'due_this_week', due_this_week,
    'overdue', overdue_count,
    'total_monthly_value', total_monthly
  );

  RETURN result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.subscription_stats(UUID) TO authenticated;
-- Disable forced update completely
UPDATE public.app_settings SET value = 'false'::jsonb WHERE key = 'app.force_update';
UPDATE public.app_settings SET value = '1'::jsonb WHERE key = 'app.min_build';
UPDATE public.app_settings SET value = '""'::jsonb WHERE key = 'app.update_message';
-- Reload PostgREST schema cache
NOTIFY pgrst, 'reload schema';
