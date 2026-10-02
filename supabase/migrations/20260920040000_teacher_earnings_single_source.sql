-- Applied on production in three steps ("teacher_earnings_single_source",
-- "teacher_earnings_overview_permissions", "admin_teachers_manage_can_view_teacher_earnings");
-- this file holds the final definitions. Visibility rule for a specific teacher's earnings:
-- the teacher, the owner, and staff with finance.read / analytics.read / teachers.manage.
-- Platform-wide totals (no teacher) still need analytics.read or finance.read.
--
-- ONE server-side source for a teacher's earnings, used by BOTH the teacher's studio and the
-- owner/admin's per-teacher view, so the two can never show different numbers.

-- Per-group breakdown of active subscription income. Same filter, normalisation and per-teacher
-- rate as subscription_active_earnings(), so the groups always add up to that headline. (The
-- client-side breakdowns used status = 'ACTIVE' only and silently dropped DUE subscriptions.)
CREATE OR REPLACE FUNCTION public.subscription_active_groups(p_teacher uuid DEFAULT NULL::uuid)
 RETURNS TABLE(group_name text, total numeric, teacher_share numeric, owner_share numeric, subscription_count bigint)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF p_teacher IS DISTINCT FROM auth.uid()
     AND NOT (public.has_permission('analytics.read') OR public.has_permission('finance.read')
              OR (p_teacher IS NOT NULL AND public.has_permission('teachers.manage')))
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  PERFORM public.refresh_subscription_statuses();

  RETURN QUERY
  WITH live AS (
    SELECT
      s.group_name AS gname,
      CASE COALESCE(s.billing_cycle, 'MONTHLY')
        WHEN 'WEEKLY' THEN s.monthly_amount * 52.0 / 12.0
        WHEN 'QUARTERLY' THEN s.monthly_amount / 3.0
        WHEN 'YEARLY' THEN s.monthly_amount / 12.0
        ELSE s.monthly_amount
      END AS monthly_value,
      COALESCE(r.percentage, 0) AS pct
    FROM teacher_subscriptions s
    LEFT JOIN teacher_subscription_rates r ON r.teacher_id = s.teacher_id
    WHERE s.status NOT IN ('PAUSED', 'OVERDUE')
      AND s.approval_status = 'APPROVED'
      AND (p_teacher IS NULL OR s.teacher_id = p_teacher)
  )
  SELECT
    gname,
    ROUND(SUM(monthly_value), 2),
    ROUND(SUM(monthly_value * pct / 100.0), 2),
    ROUND(SUM(monthly_value * (100.0 - pct) / 100.0), 2),
    COUNT(*)
  FROM live
  GROUP BY gname
  ORDER BY 2 DESC;
END;
$function$;

REVOKE ALL ON FUNCTION public.subscription_active_groups(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.subscription_active_groups(uuid) TO authenticated;

-- Everything the earnings cards show for one teacher, in one call:
--   course        : paid COURSE orders in [p_from, p_to], net of refunds
--   subscriptions : subscription_active_earnings() (active/due, not paused/overdue)
--   balance       : teacher_balance() (available / pending / paid, course earnings only)
-- Allowed for the teacher themself, or staff with analytics.read / finance.read, or admins who
-- manage teachers (teachers.manage) for ONE specific teacher. The wallet balance stays behind
-- finance.read (as teacher_balance() already requires) -> null otherwise.
CREATE OR REPLACE FUNCTION public.teacher_earnings_overview(p_teacher uuid, p_from date, p_to date)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_course jsonb;
  v_sub jsonb;
  v_bal jsonb := NULL;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF p_teacher IS NULL THEN
    RAISE EXCEPTION 'TEACHER_REQUIRED';
  END IF;
  IF p_teacher <> auth.uid()
     AND NOT (public.has_permission('finance.read') OR public.has_permission('analytics.read')
              OR public.has_permission('teachers.manage'))
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  SELECT jsonb_build_object(
           'total', ROUND(COALESCE(SUM(net_total), 0), 2),
           'teacher_share', ROUND(COALESCE(SUM(net_total * kept_teacher), 0), 2),
           'owner_share', ROUND(COALESCE(SUM(net_total * kept_platform), 0), 2),
           'count', COUNT(*))
    INTO v_course
  FROM (
    SELECT
      o.total_amount - LEAST(GREATEST(COALESCE(o.refunded_amount, 0), 0), o.total_amount) AS net_total,
      CASE WHEN o.total_amount > 0 THEN o.teacher_amount / o.total_amount ELSE 0 END AS kept_teacher,
      CASE WHEN o.total_amount > 0 THEN o.platform_amount / o.total_amount ELSE 0 END AS kept_platform
    FROM public.orders o
    WHERE o.teacher_id = p_teacher
      AND o.item_type = 'COURSE'
      AND o.status IN ('PAID', 'PARTIALLY_REFUNDED')
      AND o.paid_at >= p_from
      AND o.paid_at < (p_to + 1)
  ) x;

  SELECT jsonb_build_object(
           'total', e.total, 'teacher_share', e.teacher_share,
           'owner_share', e.owner_share, 'count', e.subscription_count)
    INTO v_sub
  FROM public.subscription_active_earnings(p_teacher) e;

  IF p_teacher = auth.uid() OR public.has_permission('finance.read') THEN
    v_bal := public.teacher_balance(p_teacher);
  END IF;

  RETURN jsonb_build_object(
    'course', v_course,
    'subscriptions', COALESCE(v_sub, jsonb_build_object('total', 0, 'teacher_share', 0, 'owner_share', 0, 'count', 0)),
    'balance', v_bal
  );
END;
$function$;

REVOKE ALL ON FUNCTION public.teacher_earnings_overview(uuid, date, date) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.teacher_earnings_overview(uuid, date, date) TO authenticated;

-- Same gate as the two functions above, so the headline and the breakdown agree for every viewer.
CREATE OR REPLACE FUNCTION public.subscription_active_earnings(p_teacher uuid DEFAULT NULL::uuid)
 RETURNS TABLE(total numeric, teacher_share numeric, owner_share numeric, subscription_count bigint)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  IF p_teacher IS DISTINCT FROM auth.uid()
     AND NOT (public.has_permission('analytics.read') OR public.has_permission('finance.read')
              OR (p_teacher IS NOT NULL AND public.has_permission('teachers.manage')))
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  PERFORM public.refresh_subscription_statuses();

  RETURN QUERY
  WITH live AS (
    SELECT
      s.teacher_id,
      CASE COALESCE(s.billing_cycle, 'MONTHLY')
        WHEN 'WEEKLY' THEN s.monthly_amount * 52.0 / 12.0
        WHEN 'QUARTERLY' THEN s.monthly_amount / 3.0
        WHEN 'YEARLY' THEN s.monthly_amount / 12.0
        ELSE s.monthly_amount
      END AS monthly_value,
      COALESCE(r.percentage, 0) AS pct
    FROM teacher_subscriptions s
    LEFT JOIN teacher_subscription_rates r ON r.teacher_id = s.teacher_id
    WHERE s.status NOT IN ('PAUSED', 'OVERDUE')
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
$function$;
