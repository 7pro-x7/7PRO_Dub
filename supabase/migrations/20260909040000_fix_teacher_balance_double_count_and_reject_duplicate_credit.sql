-- Two live financial bugs found by tracing a real payout through
-- teacher_balance() / request_payout() / review_payout() on production data.
-- Applied directly to project 7pro-x7 and verified against real rows.
--
-- BUG 1 (confirmed causing real overpayment): once a payout is marked PAID,
-- review_payout() flips its teacher_ledger reservation row's status from
-- 'AVAILABLE' to 'PAID'. teacher_balance() computed `available` as a plain
-- sum(status = 'AVAILABLE'), with nothing that permanently remembers money
-- already paid out. The moment the reservation row leaves the 'AVAILABLE'
-- bucket, its negative amount stops offsetting the original earning row,
-- and the paid amount reappears as "available" again -- indefinitely
-- re-withdrawable.
--   Live evidence: teacher f4c669ea-e4dd-46be-b0b0-525a61ff1788 had earned
--   6,122 EGP total (ever) but had already been paid 8,480 EGP across 4
--   payouts (already overpaid by 2,358), and teacher_balance() was still
--   reporting "available: 6,122" for them -- enough to be paid a second
--   time for money they'd already received. The 2,358 already overpaid is a
--   business/accounting matter (recover from future earnings or write off),
--   not something this migration silently touches.
-- FIX: subtract lifetime paid-out amount from available, so a paid
-- reservation's effect is never lost once its ledger row changes status.
--
-- BUG 2: review_payout()'s REJECT branch both (a) flipped the reservation
-- row to 'REVERSED' and (b) inserted a brand-new '+amount' ADJUSTMENT row.
-- Since the original earning row was never touched by the reservation in
-- the first place, step (a) alone already restores the balance -- step (b)
-- credited the same amount a second time. No rejected payout had happened
-- yet in production, but the next rejection would have handed that teacher
-- free money equal to the rejected amount.
-- FIX: reverse the reservation only; drop the extra credit.

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
  select coalesce(sum(amount),0) into v_pending from public.teacher_ledger
    where teacher_id = p_teacher and status = 'PENDING' and kind <> 'PAYOUT';
  select coalesce(sum(amount),0) into v_available from public.teacher_ledger
    where teacher_id = p_teacher and status = 'AVAILABLE';
  select coalesce(-sum(amount),0) into v_paid from public.teacher_ledger
    where teacher_id = p_teacher and kind = 'PAYOUT' and status = 'PAID';
  v_available := greatest(v_available - v_paid, 0);
  select coalesce(sum(o.total_amount),0) into v_gross from public.orders o
    where o.teacher_id = p_teacher and o.status in ('PAID','PARTIALLY_REFUNDED');
  return jsonb_build_object(
    'pending', round(v_pending,2), 'available', round(v_available,2), 'paid', round(v_paid,2),
    'gross', round(v_gross,2), 'currency', v_currency,
    'minimum_payout', public.setting_num('payout.minimum_amount', 1000)
  );
end $function$;

CREATE OR REPLACE FUNCTION public.review_payout(p_payout_id uuid, p_decision text, p_reference text, p_note text)
 RETURNS payouts
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare p public.payouts;
begin
  if not public.has_permission('payouts.manage') then raise exception 'FORBIDDEN'; end if;
  select * into p from public.payouts where id = p_payout_id for update;
  if not found then raise exception 'PAYOUT_NOT_FOUND'; end if;

  if upper(p_decision) = 'APPROVE' then
    if p.status <> 'PENDING' then raise exception 'INVALID_STATE'; end if;
    update public.payouts set status = 'APPROVED', reviewed_by = auth.uid(), reviewed_at = now(), note = coalesce(p_note, note)
      where id = p.id returning * into p;
    perform public.push_notification(p.teacher_id,'PAYOUT','Withdrawal approved','Your withdrawal is approved and queued for transfer.', jsonb_build_object('payout_id',p.id));
  elsif upper(p_decision) = 'REJECT' then
    if p.status not in ('PENDING','APPROVED') then raise exception 'INVALID_STATE'; end if;
    update public.payouts set status = 'REJECTED', reviewed_by = auth.uid(), reviewed_at = now(), note = coalesce(p_note, note)
      where id = p.id returning * into p;
    update public.teacher_ledger set status = 'REVERSED' where payout_id = p.id and kind = 'PAYOUT';
    perform public.push_notification(p.teacher_id,'PAYOUT','Withdrawal rejected', coalesce(p_note,'Your withdrawal request was rejected.'), jsonb_build_object('payout_id',p.id));
  elsif upper(p_decision) = 'PAID' then
    if p.status <> 'APPROVED' then raise exception 'INVALID_STATE'; end if;
    update public.payouts set status = 'PAID', paid_at = now(), reference = coalesce(p_reference, reference), note = coalesce(p_note, note)
      where id = p.id returning * into p;
    update public.teacher_ledger set status = 'PAID' where payout_id = p.id and kind = 'PAYOUT';
    perform public.push_notification(p.teacher_id,'PAYOUT','Withdrawal paid', p.amount || ' ' || p.currency || ' has been transferred.', jsonb_build_object('payout_id',p.id));
  else
    raise exception 'UNKNOWN_DECISION';
  end if;

  perform public.write_audit('payout.'||lower(p_decision),'payout',p.id::text, jsonb_build_object('amount',p.amount));
  return p;
end $function$;
