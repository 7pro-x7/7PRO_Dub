CREATE OR REPLACE FUNCTION subscription_active_earnings(p_teacher UUID DEFAULT NULL)
RETURNS TABLE (
  total NUMERIC,
  teacher_share NUMERIC,
  owner_share NUMERIC,
  subscription_count BIGINT
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  IF p_teacher IS DISTINCT FROM auth.uid()
     AND NOT (public.has_permission('analytics.read') OR public.has_permission('finance.read'))
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  WITH live AS (
    SELECT
      s.teacher_id,
      s.monthly_amount / CASE COALESCE(s.billing_cycle, 'MONTHLY')
                           WHEN 'QUARTERLY' THEN 3
                           WHEN 'YEARLY' THEN 12
                           ELSE 1
                         END AS monthly_value,
      COALESCE(r.percentage, 0) AS pct
    FROM teacher_subscriptions s
    LEFT JOIN teacher_subscription_rates r ON r.teacher_id = s.teacher_id
    WHERE s.status = 'ACTIVE'
      AND s.approval_status = 'APPROVED'
      AND (p_teacher IS NULL OR s.teacher_id = p_teacher)
  )
  SELECT
    ROUND(COALESCE(SUM(monthly_value), 0), 2),
    ROUND(COALESCE(SUM(monthly_value * pct / 100.0), 0), 2),
    ROUND(COALESCE(SUM(monthly_value * (100.0 - pct) / 100.0), 0), 2),
    COUNT(*)
  FROM live;
END;
$$;

REVOKE ALL ON FUNCTION subscription_active_earnings(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION subscription_active_earnings(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION subscription_active_earnings(UUID) TO authenticated;;
