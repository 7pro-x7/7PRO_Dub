-- ============================================================================
-- Monthly course subscriptions: the same renewal rules as group subscriptions
--
-- The bug (seen on a real account): a monthly course joined on 30-09-2026 showed "ends
-- 27-07-2027". It was paid once (one month) and the admin "Extend 30 days" button had been
-- pressed 9 times — it added 30 days on top of whatever was left, with no check at all, so each
-- press pushed the end date a month further. A student's own payment did the same: paying while
-- the subscription was still running added a month onto the time left, so paying again and again
-- stacked months ahead.
--
-- Rules now:
--   * Renewing (a student's payment, or the admin button) is refused before the renewal date —
--     the day the subscription ends — however many times it is tried (NOT_DUE_YET).
--   * A renewal is exactly one month, counted from the day it happens: the end date becomes
--     "today + 1 month". It is never added onto time that is still left, so it can't be pushed
--     further ahead and a repeat attempt finds the new end date in the future and is refused.
--   * The database does the date arithmetic; the amount of days the app sends is ignored.
--   * "Today" is Cairo's date, same as for group subscriptions.
--   * The one broken row is repaired: capped back to the month that was actually paid for.
-- ============================================================================

-- ---------------------------------------------------------------- student payment path

create or replace function public.create_order(p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid, p_live_group_id uuid, p_coupon_code text, p_country text, p_idempotency_key text)
 returns orders
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare q jsonb; o public.orders; v_existing public.orders; v_enr public.enrollments;
begin
  if p_item_type = 'AI_TUTOR' then
    if p_idempotency_key is not null then
      select * into v_existing from public.orders where idempotency_key = p_idempotency_key;
      if found then return v_existing; end if;
    end if;
    if not coalesce((select status = 'ACTIVE' and not coalesce(is_guest, false) from public.profiles where id = p_user), false) then
      raise exception 'ACCOUNT_NOT_ACTIVE';
    end if;
    q := public.quote_checkout(p_user, 'AI_TUTOR', null, p_live_plan_id, null, p_coupon_code, p_country);
    insert into public.orders(
      user_id, teacher_id, item_type, ai_tutor_plan_id,
      country_code, currency, base_price, list_price, discount_amount, total_amount,
      coupon_id, pricing_rule, commission_rate, status, idempotency_key
    ) values (
      p_user, null, 'AI_TUTOR', p_live_plan_id,
      q->>'country_code', q->>'currency', (q->>'base_price')::numeric, (q->>'list_price')::numeric,
      (q->>'discount_amount')::numeric, (q->>'total_amount')::numeric,
      nullif(q#>>'{coupon,coupon_id}', '')::uuid, q->'pricing_rule', 0, 'PENDING', p_idempotency_key
    ) returning * into o;
    return o;
  end if;

  if p_item_type = 'DUBBING' then
    if p_idempotency_key is not null then
      select * into v_existing from public.orders where idempotency_key = p_idempotency_key;
      if found then return v_existing; end if;
    end if;
    if not coalesce((select status = 'ACTIVE' and not coalesce(is_guest, false) from public.profiles where id = p_user), false) then
      raise exception 'ACCOUNT_NOT_ACTIVE';
    end if;
    q := public.quote_checkout(p_user, 'DUBBING', null, p_live_plan_id, null, p_coupon_code, p_country);

    insert into public.orders(
      user_id, teacher_id, item_type, dubbing_job_id,
      country_code, currency, base_price, list_price, discount_amount, total_amount,
      coupon_id, pricing_rule, commission_rate, status, idempotency_key
    ) values (
      p_user, null, 'DUBBING', p_live_plan_id,
      q->>'country_code', q->>'currency', (q->>'base_price')::numeric, (q->>'list_price')::numeric,
      (q->>'discount_amount')::numeric, (q->>'total_amount')::numeric,
      null, q->'pricing_rule', 0, 'PENDING', p_idempotency_key
    ) returning * into o;

    update public.dubbing_jobs set
      order_id = o.id, status = 'AWAITING_PAYMENT',
      minutes_billed = (q->>'billable_minutes')::numeric,
      minutes_free = (q->>'free_minutes_applied')::numeric,
      amount_charged = (q->>'total_amount')::numeric,
      currency = q->>'currency'
    where id = p_live_plan_id;
    return o;
  end if;

  if p_item_type = 'COURSE_MONTHLY' then
    if p_idempotency_key is not null then
      select * into v_existing from public.orders where idempotency_key = p_idempotency_key;
      if found then return v_existing; end if;
    end if;
    if not coalesce((select status = 'ACTIVE' from public.profiles where id = p_user), false) then
      raise exception 'ACCOUNT_NOT_ACTIVE';
    end if;

    -- Not before the renewal date: a subscription that is still running can't be paid for again.
    select * into v_enr from public.enrollments where user_id = p_user and course_id = p_course_id;
    if found and v_enr.access_type = 'MONTHLY' and v_enr.status = 'ACTIVE'
       and v_enr.expires_at is not null and v_enr.expires_at > now()
       and public._cairo_today() < (v_enr.expires_at at time zone 'Africa/Cairo')::date then
      raise exception 'NOT_DUE_YET:%', (v_enr.expires_at at time zone 'Africa/Cairo')::date;
    end if;

    q := public.quote_checkout(p_user, 'COURSE_MONTHLY', p_course_id, null, null, p_coupon_code, p_country);

    insert into public.orders(
      user_id, teacher_id, item_type, course_id, billing_period,
      country_code, currency, base_price, list_price, discount_amount, total_amount,
      coupon_id, pricing_rule, commission_rate, status, idempotency_key
    ) values (
      p_user, (q->>'teacher_id')::uuid, 'COURSE', p_course_id, 'MONTH',
      q->>'country_code', q->>'currency', (q->>'base_price')::numeric, (q->>'list_price')::numeric,
      (q->>'discount_amount')::numeric, (q->>'total_amount')::numeric,
      nullif(q#>>'{coupon,coupon_id}', '')::uuid, q->'pricing_rule',
      (q->>'commission_rate')::numeric, 'PENDING', p_idempotency_key
    ) returning * into o;
    return o;
  end if;

  return public._create_order_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country, p_idempotency_key);
end $function$;

create or replace function public._course_monthly_confirm(p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  o public.orders; e public.enrollments; v_res jsonb;
  v_had boolean; v_new_exp timestamptz;
begin
  select * into o from public.orders where id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;

  select * into e from public.enrollments where user_id = o.user_id and course_id = o.course_id;
  v_had := found;

  v_res := public._confirm_order_payment_base(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  if coalesce((v_res ->> 'duplicate')::boolean, false) then
    return v_res;
  end if;

  -- One full paid month from the day of payment — never added onto time that is still left.
  -- (It used to be "previous end + 1 month", so paying again before the end stacked months.)
  v_new_exp := now() + interval '1 month';

  update public.enrollments
     set access_type = 'MONTHLY', status = 'ACTIVE', expires_at = v_new_exp
   where user_id = o.user_id and course_id = o.course_id;

  if v_had then
    update public.courses set enrollments_count = greatest(enrollments_count - 1, 0) where id = o.course_id;
  end if;

  return v_res || jsonb_build_object('expires_at', v_new_exp);
end $function$;

-- ---------------------------------------------------------------- admin "extend" button

create or replace function public.admin_enrollment_action(p_enrollment uuid, p_action text, p_days integer default 30)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  v_uid uuid := auth.uid();
  e public.enrollments;
  c public.courses;
  v_action text := upper(coalesce(p_action, ''));
  v_new_exp timestamptz;
  v_end_day date;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.has_permission('courses.grant') then raise exception 'FORBIDDEN'; end if;
  if v_action not in ('CLOSE', 'OPEN', 'EXTEND') then raise exception 'INVALID_ACTION'; end if;

  select * into e from public.enrollments where id = p_enrollment for update;
  if not found then raise exception 'ENROLLMENT_NOT_FOUND'; end if;
  select * into c from public.courses where id = e.course_id;
  if c.is_free then raise exception 'COURSE_IS_FREE'; end if;

  if v_action = 'CLOSE' then
    update public.enrollments set status = 'PAYMENT_REQUIRED' where id = e.id;
    update public.course_grants set revoked_at = now(), revoked_by = v_uid
     where user_id = e.user_id and course_id = e.course_id and revoked_at is null;

  elsif v_action = 'OPEN' then
    if e.access_type = 'MONTHLY' and e.expires_at is not null and e.expires_at <= now() then
      raise exception 'SUBSCRIPTION_EXPIRED_EXTEND_INSTEAD';
    end if;
    update public.enrollments set status = 'ACTIVE' where id = e.id;
    if e.access_type = 'GRANTED'
       and not exists (select 1 from public.course_grants g where g.user_id = e.user_id and g.course_id = e.course_id and g.revoked_at is null) then
      insert into public.course_grants (user_id, course_id, granted_by) values (e.user_id, e.course_id, v_uid);
    end if;

  else
    if e.access_type <> 'MONTHLY' then raise exception 'NOT_MONTHLY'; end if;

    -- Not before the renewal date, however many times it is pressed. A subscription that has
    -- ended (or never had an end date) can always be renewed; one that is still running can't.
    if e.expires_at is not null and e.status <> 'EXPIRED' then
      v_end_day := (e.expires_at at time zone 'Africa/Cairo')::date;
      if public._cairo_today() < v_end_day then
        raise exception 'NOT_DUE_YET:%', v_end_day;
      end if;
    end if;

    -- Exactly one month from today. The number of days the app sends is ignored.
    v_new_exp := now() + interval '1 month';
    update public.enrollments set status = 'ACTIVE', expires_at = v_new_exp where id = e.id;
  end if;

  perform public.write_audit('enrollment.' || lower(v_action), 'enrollment', e.id::text,
    jsonb_build_object('user', e.user_id, 'course', e.course_id, 'months', case when v_action = 'EXTEND' then 1 end));
  perform public.push_notification(
    e.user_id, 'ENROLLMENT',
    case v_action when 'CLOSE' then 'تم إيقاف الوصول لكورس • Course access closed'
                  when 'OPEN' then 'تم فتح كورس لك • A course was opened for you'
                  else 'تم تجديد اشتراكك • Your subscription was renewed' end,
    case v_action when 'CLOSE' then '«' || c.title || '» اتقفل عليك. تواصل مع الدعم لو محتاج مساعدة. — "' || c.title || '" was closed for you.'
                  when 'OPEN' then 'تم فتح «' || c.title || '» لك. — "' || c.title || '" is now open for you.'
                  else 'تم تجديد اشتراكك في «' || c.title || '» لمدة شهر. — Your "' || c.title || '" subscription was renewed for one month.' end,
    jsonb_build_object('course_id', e.course_id));

  return jsonb_build_object('ok', true, 'expires_at', v_new_exp);
end $function$;

-- ---------------------------------------------------------------- repair the stacked end date

-- A monthly subscription can no longer end more than one month from now. Any row that does
-- (only ever the product of the bug above) goes back to the month that was actually paid for:
-- the last paid monthly order + 1 month (never earlier than now, so nobody loses access they
-- paid for, and never later than what is stored).
do $$
declare r record; v_paid timestamptz; v_cap timestamptz; v_new timestamptz;
begin
  for r in
    select * from public.enrollments
     where access_type = 'MONTHLY' and expires_at is not null
       and expires_at > now() + interval '1 month' + interval '1 day'
  loop
    select max(o.paid_at) into v_paid
      from public.orders o
     where o.user_id = r.user_id and o.course_id = r.course_id
       and o.billing_period = 'MONTH' and o.paid_at is not null;

    v_cap := case when v_paid is not null then greatest(v_paid + interval '1 month', now())
                  else now() + interval '1 month' end;
    v_new := least(r.expires_at, v_cap);

    update public.enrollments set expires_at = v_new where id = r.id;

    begin
      insert into public.audit_logs (action, target_type, target_id, metadata)
      values ('enrollment.expiry_repaired', 'enrollment', r.id::text,
              jsonb_build_object('user', r.user_id, 'course', r.course_id, 'was', r.expires_at, 'now', v_new));
    exception when others then null;
    end;
  end loop;
end $$;
