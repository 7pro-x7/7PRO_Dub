-- Baseline: the payouts system as it actually exists in production.
--
-- request_payout(), review_payout(), release_matured_earnings(), and the
-- `payouts` table itself were created directly against the live database at
-- some point and were never captured in any migration file in this repo --
-- discovered while auditing earnings/withdrawal correctness (they don't
-- appear in `supabase/migrations` at all before this file, only referenced
-- from the Kotlin client and backend/types.ts). This migration records them
-- as-is so the migration history matches reality and future changes can be
-- diffed properly instead of silently re-diverging.
--
-- teacher_balance() is included here already carrying the fix applied in
-- 20260909040000 (see that file for what was wrong and why) rather than
-- the buggy version that predated it, since there is no value in
-- resurrecting a known-bad definition just to immediately replace it again.

CREATE OR REPLACE FUNCTION public.release_matured_earnings()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare n int;
begin
  with upd as (
    update public.teacher_ledger set status = 'AVAILABLE'
    where status = 'PENDING' and kind = 'EARNING' and available_at is not null and available_at <= now()
    returning 1
  ) select count(*) into n from upd;
  return n;
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

  -- lock the teacher's ledger rows so two concurrent requests cannot both pass
  perform 1 from public.teacher_ledger where teacher_id = auth.uid() for update;
  select coalesce(sum(amount),0) into v_avail from public.teacher_ledger
    where teacher_id = auth.uid() and status = 'AVAILABLE';

  if p_amount is null or p_amount <= 0 then raise exception 'INVALID_AMOUNT'; end if;
  if p_amount < v_min then raise exception 'BELOW_MINIMUM:%', v_min; end if;
  if p_amount > v_avail then raise exception 'INSUFFICIENT_BALANCE:%', v_avail; end if;
  if exists (select 1 from public.payouts where teacher_id = auth.uid() and status in ('PENDING','APPROVED')) then
    raise exception 'PAYOUT_ALREADY_OPEN';
  end if;

  insert into public.payouts(teacher_id, amount, currency, method, destination, note, status)
  values (auth.uid(), round(p_amount,2), v_cur, p_method, p_destination, p_note, 'PENDING')
  returning * into p;

  -- reserve the funds immediately so the balance cannot be withdrawn twice
  insert into public.teacher_ledger(teacher_id, payout_id, kind, amount, currency, status, note)
  values (auth.uid(), p.id, 'PAYOUT', -round(p_amount,2), v_cur, 'AVAILABLE', 'Withdrawal request');

  perform public.notify_staff('PAYOUT_REQUEST', 'Withdrawal requested',
    round(p_amount,2) || ' ' || v_cur, jsonb_build_object('payout_id', p.id, 'teacher_id', auth.uid()));
  perform public.write_audit('payout.request','payout',p.id::text, jsonb_build_object('amount',p_amount));
  return p;
end $function$;

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
  -- A paid-out reservation row leaves the 'AVAILABLE' bucket once it's marked
  -- PAID, so its negative offset would otherwise vanish. Subtracting the
  -- lifetime paid total here keeps that money excluded permanently instead
  -- of only while the reservation row happens to still say AVAILABLE.
  v_available := greatest(v_available - v_paid, 0);
  select coalesce(sum(o.total_amount),0) into v_gross from public.orders o
    where o.teacher_id = p_teacher and o.status in ('PAID','PARTIALLY_REFUNDED');
  return jsonb_build_object(
    'pending', round(v_pending,2), 'available', round(v_available,2), 'paid', round(v_paid,2),
    'gross', round(v_gross,2), 'currency', v_currency,
    'minimum_payout', public.setting_num('payout.minimum_amount', 1000)
  );
end $function$;

GRANT EXECUTE ON FUNCTION public.request_payout(numeric, text, text, text) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.request_payout(numeric, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.teacher_balance(uuid) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.teacher_balance(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.release_matured_earnings() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.release_matured_earnings() TO service_role;
