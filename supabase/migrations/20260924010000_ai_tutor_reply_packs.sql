-- AI Tutor: plans become REPLY PACKS.
--
-- A pack gives a number of replies (plans.replies) that stay valid for plans.period_days (30 by
-- default) counted from the purchase. Unused replies simply expire at the end of that period,
-- whether the student used them or not. After that the student renews or buys another pack; every
-- purchase is its own pack with its own expiry (replies are spent from the pack that expires first).
--
-- Free replies (ai_tutor_config.per_user_daily, set by the owner, 0 = none) are separate and are
-- spent first each day, so paid replies are only touched once the free ones of the day are used.
--
-- The earlier "N replies a day for N days" columns (daily_replies) are kept but no longer used.

alter table public.ai_tutor_plans
  add column if not exists replies int not null default 100 check (replies between 1 and 1000000);
comment on column public.ai_tutor_plans.replies is 'Replies a purchase gives; they expire after period_days.';
alter table public.ai_tutor_plans alter column daily_replies drop not null;
alter table public.ai_tutor_plans alter column daily_replies drop default;
comment on column public.ai_tutor_plans.daily_replies is 'Legacy (daily-limit plans). Unused since reply packs.';

alter table public.ai_tutor_entitlements
  add column if not exists replies_total int not null default 0,
  add column if not exists replies_used  int not null default 0;
alter table public.ai_tutor_entitlements alter column daily_replies drop not null;
alter table public.ai_tutor_entitlements
  drop constraint if exists ai_tutor_entitlements_replies_ok,
  add  constraint ai_tutor_entitlements_replies_ok check (replies_used >= 0 and replies_used <= replies_total);

-- Which pack paid for a turn (null = a free reply); needed to hand a reply back when a turn fails.
alter table public.ai_tutor_turns
  add column if not exists grant_id uuid references public.ai_tutor_entitlements(id) on delete set null;

create or replace function public.ai_tutor_quote(p_user uuid, p_plan uuid, p_coupon_code text, p_country text)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare pl public.ai_tutor_plans; v_price numeric; v_coupon jsonb; v_total numeric;
begin
  select * into pl from public.ai_tutor_plans where id = p_plan and is_active;
  if not found then raise exception 'AI_PLAN_NOT_AVAILABLE'; end if;
  v_price := public.ai_tutor_effective_price(pl);
  v_coupon := public.ai_tutor_evaluate_coupon(p_coupon_code, p_user, p_plan, p_country, v_price, pl.currency);
  v_total := round(greatest(v_price - (v_coupon->>'discount')::numeric, 0), 2);
  return jsonb_build_object(
    'item_type', 'AI_TUTOR', 'course_id', null, 'live_service_id', null, 'live_plan_id', null, 'live_group_id', null,
    'ai_tutor_plan_id', pl.id, 'teacher_id', null,
    'country_code', upper(coalesce(p_country, '')), 'currency', pl.currency,
    'base_price', pl.price, 'list_price', v_price,
    'discount_amount', (v_coupon->>'discount')::numeric, 'total_amount', v_total,
    'coupon', v_coupon,
    'pricing_rule', jsonb_build_object('source', 'ai_tutor_plan', 'plan_id', pl.id, 'sale', v_price < pl.price),
    'seats_left', null, 'commission_rate', 0,
    'plan_name', pl.name, 'plan_name_ar', pl.name_ar, 'period_days', pl.period_days, 'replies', pl.replies
  );
end $$;

create or replace function public.ai_tutor_confirm_order(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare o public.orders; pl public.ai_tutor_plans; v_end timestamptz;
begin
  insert into public.payment_events(provider, provider_event_id, order_id, event_type, payload)
  values (p_provider, p_event_id, p_order_id, 'PAYMENT_SUCCEEDED', coalesce(p_payload, '{}'::jsonb))
  on conflict (provider, provider_event_id) do nothing;
  if not found then
    return jsonb_build_object('ok', true, 'duplicate', true);
  end if;

  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if o.status = 'PAID' then
    return jsonb_build_object('ok', true, 'duplicate', true, 'order_id', o.id);
  end if;
  if o.status not in ('PENDING', 'FAILED') then
    raise exception 'ORDER_NOT_PAYABLE:%', o.status;
  end if;

  select * into pl from public.ai_tutor_plans where id = o.ai_tutor_plan_id;
  if not found then raise exception 'AI_PLAN_NOT_AVAILABLE'; end if;

  update public.orders set
    status = 'PAID', provider = p_provider, provider_ref = p_provider_ref, paid_at = now(),
    teacher_amount = 0, platform_amount = total_amount, failure_reason = null
  where id = o.id returning * into o;

  if o.coupon_id is not null and o.discount_amount > 0 then
    insert into public.coupon_redemptions(coupon_id, user_id, order_id, discount_amount, currency)
    values (o.coupon_id, o.user_id, o.id, o.discount_amount, o.currency);
    update public.coupons set used_count = used_count + 1 where id = o.coupon_id;
  end if;

  -- Every purchase is its own pack: valid from now for period_days, whatever the student already has.
  v_end := now() + make_interval(days => pl.period_days);
  insert into public.ai_tutor_entitlements(user_id, plan_id, order_id, replies_total, starts_at, ends_at)
  values (o.user_id, pl.id, o.id, pl.replies, now(), v_end)
  on conflict (order_id) do nothing;

  perform public.push_notification(o.user_id, 'SUBSCRIPTION_ACTIVE', 'AI Tutor replies added',
    pl.replies || ' replies (' || pl.name || ') are ready until ' || to_char(v_end, 'DD Mon YYYY') || '.',
    jsonb_build_object('order_id', o.id, 'ai_tutor_plan_id', pl.id));
  perform public.notify_staff('PAYMENT', 'Payment received',
    o.total_amount || ' ' || o.currency || ' (AI_TUTOR)', jsonb_build_object('order_id', o.id));

  update public.payment_events set processed_at = now()
   where provider = p_provider and provider_event_id = p_event_id;
  return jsonb_build_object('ok', true, 'order_id', o.id, 'status', 'PAID');
end $$;

create or replace function public.ai_tutor_status()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_free_used int; v_free_left int; v_global int; v_guest boolean; v_role text;
  v_credits int; v_credits_until timestamptz; v_plan text;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into c from public.ai_tutor_config where id;
  select coalesce(is_guest, false), role into v_guest, v_role from public.profiles where id = v_uid;

  if v_role = 'OWNER' then
    return jsonb_build_object(
      'enabled', c.enabled and c.worker_url <> '',
      'per_user_daily', c.per_user_daily,
      'used', 0,
      'remaining', 999999,
      'free_remaining', 999999,
      'credits', 0,
      'global_available', true,
      'worker_url', c.worker_url,
      'avatar_url', c.avatar_url,
      'avatar_version', c.avatar_version,
      'resets_at', ((v_day + 1)::timestamp at time zone 'UTC')
    );
  end if;

  select coalesce(sum(e.replies_total - e.replies_used), 0), min(e.ends_at) into v_credits, v_credits_until
    from public.ai_tutor_entitlements e
   where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now()
     and e.replies_used < e.replies_total;
  select p.name into v_plan
    from public.ai_tutor_entitlements e join public.ai_tutor_plans p on p.id = e.plan_id
   where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now()
     and e.replies_used < e.replies_total
   order by e.ends_at asc limit 1;

  select count(*) into v_free_used from public.ai_tutor_turns
   where day = v_day and user_id = v_uid and kind = 'turn' and grant_id is null;
  v_free_left := greatest(c.per_user_daily - v_free_used, 0);
  select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = 'turn';

  return jsonb_build_object(
    'enabled', c.enabled and c.worker_url <> '' and not coalesce(v_guest, false),
    'per_user_daily', c.per_user_daily,
    'used', v_free_used,
    'remaining', v_free_left + v_credits,
    'free_remaining', v_free_left,
    'credits', v_credits,
    'credits_until', v_credits_until,
    'global_available', v_global < c.global_daily,
    'worker_url', c.worker_url,
    'avatar_url', c.avatar_url,
    'avatar_version', c.avatar_version,
    'resets_at', ((v_day + 1)::timestamp at time zone 'UTC'),
    'plan_name', v_plan,
    'plan_until', v_credits_until
  );
end $$;

create or replace function public.ai_tutor_take_turn(p_kind text default 'turn')
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean; v_role text; v_ticket uuid;
  v_free_used int; v_grant uuid; v_credits int; v_free_left int;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_kind not in ('turn','open') then raise exception 'INVALID_KIND'; end if;
  select * into c from public.ai_tutor_config where id;
  select coalesce(is_guest, false), role into v_guest, v_role from public.profiles where id = v_uid;
  if not c.enabled then
    return jsonb_build_object('allowed', false, 'reason', 'DISABLED');
  end if;

  if v_role = 'OWNER' then
    insert into public.ai_tutor_turns(user_id, kind, day) values (v_uid, p_kind, v_day) returning id into v_ticket;
    return jsonb_build_object('allowed', true, 'ticket', v_ticket, 'remaining', 999999);
  end if;

  if coalesce(v_guest, false) then
    return jsonb_build_object('allowed', false, 'reason', 'DISABLED');
  end if;

  perform pg_advisory_xact_lock(hashtext('ai_tutor:' || v_uid::text));

  if p_kind = 'open' then
    select count(*) into v_used from public.ai_tutor_turns where day = v_day and user_id = v_uid and kind = 'open';
    if v_used >= c.per_user_opens then
      return jsonb_build_object('allowed', false, 'reason', 'USER_LIMIT', 'remaining', 0);
    end if;
    select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = 'open';
    if v_global >= c.global_opens then
      return jsonb_build_object('allowed', false, 'reason', 'GLOBAL_LIMIT');
    end if;
    insert into public.ai_tutor_turns(user_id, kind, day) values (v_uid, 'open', v_day) returning id into v_ticket;
    return jsonb_build_object('allowed', true, 'ticket', v_ticket, 'remaining', null);
  end if;

  -- A reply is paid for with the day's free replies first, then with a pack (the one that expires first).
  select count(*) into v_free_used from public.ai_tutor_turns
   where day = v_day and user_id = v_uid and kind = 'turn' and grant_id is null;
  if v_free_used < c.per_user_daily then
    v_grant := null;
  else
    select e.id into v_grant from public.ai_tutor_entitlements e
     where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now()
       and e.replies_used < e.replies_total
     order by e.ends_at asc, e.created_at asc
     limit 1 for update;
    if v_grant is null then
      return jsonb_build_object('allowed', false, 'reason', 'USER_LIMIT', 'remaining', 0);
    end if;
  end if;

  select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = 'turn';
  if v_global >= c.global_daily then
    return jsonb_build_object('allowed', false, 'reason', 'GLOBAL_LIMIT');
  end if;

  if v_grant is not null then
    update public.ai_tutor_entitlements set replies_used = replies_used + 1 where id = v_grant;
  end if;
  insert into public.ai_tutor_turns(user_id, kind, day, grant_id) values (v_uid, 'turn', v_day, v_grant) returning id into v_ticket;

  if random() < 0.02 then
    delete from public.ai_tutor_turns where day < v_day - 45;
  end if;

  v_free_left := greatest(c.per_user_daily - v_free_used - case when v_grant is null then 1 else 0 end, 0);
  select coalesce(sum(e.replies_total - e.replies_used), 0) into v_credits
    from public.ai_tutor_entitlements e
   where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now();
  return jsonb_build_object('allowed', true, 'ticket', v_ticket, 'remaining', v_free_left + v_credits);
end $$;

-- A failed turn hands its reply back: a free one simply disappears from today's count, a paid one
-- returns to its pack.
create or replace function public.ai_tutor_refund_turn(p_ticket uuid)
returns void language plpgsql security definer set search_path to 'public' as $$
declare v_grant uuid; v_found boolean;
begin
  delete from public.ai_tutor_turns
   where id = p_ticket and user_id = auth.uid() and created_at > now() - interval '3 minutes'
   returning grant_id into v_grant;
  v_found := found;
  if v_found and v_grant is not null then
    update public.ai_tutor_entitlements set replies_used = greatest(replies_used - 1, 0) where id = v_grant;
  end if;
end $$;

create or replace function public.ai_tutor_sales_summary()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not public.is_owner() then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'active_subscribers', (select count(distinct user_id) from public.ai_tutor_entitlements
                            where status = 'ACTIVE' and starts_at <= now() and ends_at > now()
                              and replies_used < replies_total),
    'paid_orders', (select count(*) from public.orders
                     where item_type = 'AI_TUTOR' and status in ('PAID', 'PARTIALLY_REFUNDED')),
    'revenue', (select coalesce(jsonb_object_agg(currency, s), '{}'::jsonb)
                  from (select currency, sum(total_amount - refunded_amount) s from public.orders
                         where item_type = 'AI_TUTOR' and status in ('PAID', 'PARTIALLY_REFUNDED')
                         group by currency) x),
    'replies_sold', (select coalesce(sum(replies_total), 0) from public.ai_tutor_entitlements where status = 'ACTIVE'),
    'replies_used', (select coalesce(sum(replies_used), 0) from public.ai_tutor_entitlements where status = 'ACTIVE'),
    'replies_expired', (select coalesce(sum(replies_total - replies_used), 0) from public.ai_tutor_entitlements
                         where status = 'ACTIVE' and ends_at <= now())
  );
end $$;

-- Draft packs (switched off): the owner reviews the prices and turns them on in the app.
insert into public.ai_tutor_plans(name, name_ar, description, description_ar, price, currency, period_days, replies, sort_order, is_active)
select v.name, v.name_ar, v.d, v.d_ar, v.price, 'EGP', 30, v.replies, v.ord, false
  from (values
    ('30 replies',    'تجريبية 30 رد',  'Valid for 30 days from purchase, used or not.', 'صالحة 30 يوم من الشراء، استخدمتها أو لا.', 19::numeric,   30, 1),
    ('100 replies',   'باقة 100 رد',    'Valid for 30 days from purchase, used or not.', 'صالحة 30 يوم من الشراء، استخدمتها أو لا.', 45::numeric,  100, 2),
    ('300 replies',   'باقة 300 رد',    'Valid for 30 days from purchase, used or not.', 'صالحة 30 يوم من الشراء، استخدمتها أو لا.', 119::numeric, 300, 3),
    ('1,000 replies', 'باقة 1000 رد',   'Valid for 30 days from purchase, used or not.', 'صالحة 30 يوم من الشراء، استخدمتها أو لا.', 369::numeric, 1000, 4)
  ) as v(name, name_ar, d, d_ar, price, replies, ord)
 where not exists (select 1 from public.ai_tutor_plans);

revoke all on function public.ai_tutor_quote(uuid, uuid, text, text) from public, anon, authenticated;
revoke all on function public.ai_tutor_confirm_order(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.ai_tutor_quote(uuid, uuid, text, text) to service_role;
grant execute on function public.ai_tutor_confirm_order(uuid, text, text, text, jsonb) to service_role;
revoke all on function public.ai_tutor_sales_summary() from public, anon;
grant execute on function public.ai_tutor_sales_summary() to authenticated;
