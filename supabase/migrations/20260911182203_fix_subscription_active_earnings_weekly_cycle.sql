-- ============================================================================
-- Fix subscription_active_earnings: WEEKLY subscriptions were not normalized
-- to a monthly figure.
--
-- billing_cycle is a real, actively-used column (default 'MONTHLY', and set
-- to 'WEEKLY' by the group-booking/self-service subscription flow — see
-- request_group_subscription's renewal-date logic). The original CASE only
-- handled QUARTERLY (÷3) and YEARLY (÷12); WEEKLY fell into the ELSE branch
-- and was divided by 1, so a weekly subscription's per-week amount was
-- counted as if it were the full monthly amount — understating what a
-- weekly subscriber is actually worth per month by roughly 4.33x once any
-- WEEKLY rows exist (none do yet in production, which is why this had not
-- surfaced as a visibly wrong number).
--
-- A week is 52/12 of a month on average, so a weekly amount's monthly
-- equivalent is monthly_amount * 52.0/12.0, not monthly_amount itself.
-- ============================================================================

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

  -- A teacher may always ask about themselves. Anything wider — another
  -- teacher, or the whole platform (p_teacher IS NULL) — needs the same
  -- permission every other cross-teacher figure is gated on.
  IF p_teacher IS DISTINCT FROM auth.uid()
     AND NOT (public.has_permission('analytics.read') OR public.has_permission('finance.read'))
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  WITH live AS (
    SELECT
      s.teacher_id,
      -- Normalised to a monthly figure so a non-monthly cycle cannot quietly
      -- misreport the headline.
      CASE COALESCE(s.billing_cycle, 'MONTHLY')
        WHEN 'WEEKLY' THEN s.monthly_amount * 52.0 / 12.0
        WHEN 'QUARTERLY' THEN s.monthly_amount / 3.0
        WHEN 'YEARLY' THEN s.monthly_amount / 12.0
        ELSE s.monthly_amount
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
GRANT EXECUTE ON FUNCTION subscription_active_earnings(UUID) TO authenticated;
;
