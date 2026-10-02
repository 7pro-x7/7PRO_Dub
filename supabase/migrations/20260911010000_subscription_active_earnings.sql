-- ============================================================================
-- 7PRO — subscription earnings measured from the subscriptions that exist
--
-- The dashboards used to derive the subscription figure from the
-- `subscription_earnings` ledger, prorated by how many days of each earning's
-- covered period fell inside a rolling 30-day window. That answers "how much
-- accrued in the last 30 days", which is not the question the owner and the
-- teacher are actually asking of a card headed "monthly": a live 400/month
-- subscription that started two days ago showed as 25.81, and a subscription
-- that is active right now but whose ledger row was written last cycle showed
-- as part of a window rather than as itself.
--
-- This function answers the plain question instead: what do the subscriptions
-- that exist and are active bill per month, and how does that split between
-- the teacher and the platform. Nothing historical, nothing prorated — the
-- weekly figure the clients show beside it is simply this divided by four.
--
-- Only ACTIVE + APPROVED subscriptions count: a pending, rejected, paused or
-- cancelled row is not income anybody should be looking at.
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
      -- inflate the headline. Only MONTHLY exists today; the rest are here so
      -- adding one later doesn't silently misreport.
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
GRANT EXECUTE ON FUNCTION subscription_active_earnings(UUID) TO authenticated;
