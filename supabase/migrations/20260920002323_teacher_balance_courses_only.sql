-- ============================================================================
-- SYNC FILE: mirrors what is already live on production (7pro-x7, applied as
-- version 20260920002323). Re-applying it is a no-op (CREATE OR REPLACE).
--
-- Rule: the withdrawable/available balance is COURSE earnings only. A ledger
-- row counts as course money when it is an EARNING whose order is a COURSE
-- order (orders.item_type = 'COURSE'); PAYOUT rows are included so withdrawal
-- reservations/payments net against it. Legacy subscription earnings have no
-- order, so they are excluded without needing any backfill.
--
-- subscription_active_earnings() refreshes statuses first and counts every
-- approved subscription that is not PAUSED or OVERDUE.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.teacher_balance(p_teacher uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_pending numeric; v_available numeric; v_paid numeric; v_gross numeric; v_currency text;
begin
  if p_teacher <> auth.uid() and not public.has_permission('finance.read') then
    raise exception 'FORBIDDEN';
  end if;
  v_currency := public.setting_text('payout.currency','EGP');

  select coalesce(sum(tl.amount),0) into v_pending
    from public.teacher_ledger tl
    join public.orders o on o.id = tl.order_id
    where tl.teacher_id = p_teacher and tl.status = 'PENDING' and tl.kind = 'EARNING' and o.item_type = 'COURSE';

  select coalesce(sum(tl.amount),0) into v_available
    from public.teacher_ledger tl
    left join public.orders o on o.id = tl.order_id
    where tl.teacher_id = p_teacher and tl.status = 'AVAILABLE'
      and (tl.kind = 'PAYOUT' or (tl.kind = 'EARNING' and o.item_type = 'COURSE'));

  select coalesce(-sum(amount),0) into v_paid from public.teacher_ledger
    where teacher_id = p_teacher and kind = 'PAYOUT' and status = 'PAID';

  -- A paid-out reservation row leaves the 'AVAILABLE' bucket once it's marked PAID, so its
  -- negative offset would otherwise vanish. Subtracting the lifetime paid total keeps that
  -- money excluded permanently rather than only while the row still says AVAILABLE.
  v_available := greatest(v_available - v_paid, 0);

  select coalesce(sum(o.total_amount),0) into v_gross from public.orders o
    where o.teacher_id = p_teacher and o.status in ('PAID','PARTIALLY_REFUNDED');

  return jsonb_build_object(
    'pending', round(v_pending,2), 'available', round(v_available,2), 'paid', round(v_paid,2),
    'gross', round(v_gross,2), 'currency', v_currency,
    'minimum_payout', public.setting_num('payout.minimum_amount', 1000)
  );
end $function$;

CREATE OR REPLACE FUNCTION public.request_payout(p_amount numeric, p_method text, p_destination text, p_note text)
 RETURNS payouts
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_min numeric; v_avail numeric; v_cur text; p public.payouts;
begin
  if not public.is_approved_teacher(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  perform public.release_matured_earnings();
  v_min := public.setting_num('payout.minimum_amount', 1000);
  v_cur := public.setting_text('payout.currency','EGP');

  perform 1 from public.teacher_ledger where teacher_id = auth.uid() for update;

  -- Course earnings only -- see teacher_balance() above for the same order_id/item_type test.
  select coalesce(sum(tl.amount),0) into v_avail
    from public.teacher_ledger tl
    left join public.orders o on o.id = tl.order_id
    where tl.teacher_id = auth.uid() and tl.status = 'AVAILABLE'
      and (tl.kind = 'PAYOUT' or (tl.kind = 'EARNING' and o.item_type = 'COURSE'));

  if p_amount is null or p_amount <= 0 then raise exception 'INVALID_AMOUNT'; end if;
  if p_amount < v_min then raise exception 'BELOW_MINIMUM:%', v_min; end if;
  if p_amount > v_avail then raise exception 'INSUFFICIENT_BALANCE:%', v_avail; end if;
  if exists (select 1 from public.payouts where teacher_id = auth.uid() and status in ('PENDING','APPROVED')) then
    raise exception 'PAYOUT_ALREADY_OPEN';
  end if;

  insert into public.payouts(teacher_id, amount, currency, method, destination, note, status)
  values (auth.uid(), round(p_amount,2), v_cur, p_method, p_destination, p_note, 'PENDING')
  returning * into p;

  insert into public.teacher_ledger(teacher_id, payout_id, kind, amount, currency, status, note)
  values (auth.uid(), p.id, 'PAYOUT', -round(p_amount,2), v_cur, 'AVAILABLE', 'Withdrawal request');

  perform public.notify_staff('PAYOUT_REQUEST', 'Withdrawal requested',
    round(p_amount,2) || ' ' || v_cur, jsonb_build_object('payout_id', p.id, 'teacher_id', auth.uid()));
  perform public.write_audit('payout.request','payout',p.id::text, jsonb_build_object('amount',p_amount));
  return p;
end $function$;

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
     AND NOT (public.has_permission('analytics.read') OR public.has_permission('finance.read'))
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
