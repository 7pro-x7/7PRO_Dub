create table if not exists public.ai_tutor_plans (
  id             uuid primary key default gen_random_uuid(),
  name           text not null check (length(btrim(name)) between 1 and 60),
  name_ar        text not null default '',
  description    text not null default '',
  description_ar text not null default '',
  price          numeric(10,2) not null check (price >= 0),
  sale_price     numeric(10,2) check (sale_price is null or (sale_price >= 0 and sale_price < price)),
  sale_ends_at   timestamptz,
  currency       text not null default 'EGP' check (currency ~ '^[A-Z]{3}$'),
  period_days    int  not null default 30 check (period_days between 1 and 3650),
  daily_replies  int  not null default 60 check (daily_replies between 1 and 1000),
  sort_order     int  not null default 0,
  is_active      boolean not null default true,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index if not exists ai_tutor_plans_active_idx on public.ai_tutor_plans(is_active, sort_order);

drop trigger if exists trg_ai_tutor_plans_touch on public.ai_tutor_plans;
create trigger trg_ai_tutor_plans_touch before update on public.ai_tutor_plans
  for each row execute function public.touch_updated_at();

alter table public.ai_tutor_plans enable row level security;
grant select, insert, update, delete on public.ai_tutor_plans to authenticated;

drop policy if exists ai_tutor_plans_read on public.ai_tutor_plans;
create policy ai_tutor_plans_read on public.ai_tutor_plans
  for select to authenticated using (is_active or public.is_owner());

drop policy if exists ai_tutor_plans_owner_write on public.ai_tutor_plans;
create policy ai_tutor_plans_owner_write on public.ai_tutor_plans
  for all to authenticated using (public.is_owner()) with check (public.is_owner());

create or replace function public.ai_tutor_effective_price(pl public.ai_tutor_plans)
returns numeric language sql stable set search_path to 'public' as $$
  select case when pl.sale_price is not null and (pl.sale_ends_at is null or pl.sale_ends_at > now())
              then pl.sale_price else pl.price end
$$;

create table if not exists public.ai_tutor_entitlements (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles(id) on delete cascade,
  plan_id       uuid not null references public.ai_tutor_plans(id) on delete restrict,
  order_id      uuid unique references public.orders(id) on delete set null,
  daily_replies int  not null,
  starts_at     timestamptz not null default now(),
  ends_at       timestamptz not null,
  status        text not null default 'ACTIVE' check (status in ('ACTIVE', 'REFUNDED', 'CANCELLED')),
  created_at    timestamptz not null default now()
);
create index if not exists ai_tutor_entitlements_user_idx on public.ai_tutor_entitlements(user_id, status, ends_at);
alter table public.ai_tutor_entitlements enable row level security;
grant select on public.ai_tutor_entitlements to authenticated;
drop policy if exists ai_tutor_entitlements_read on public.ai_tutor_entitlements;
create policy ai_tutor_entitlements_read on public.ai_tutor_entitlements
  for select to authenticated using (user_id = auth.uid() or public.is_owner());

alter table public.orders  add column if not exists ai_tutor_plan_id uuid references public.ai_tutor_plans(id) on delete restrict;
alter table public.coupons add column if not exists ai_tutor_scope text not null default 'NONE'
  check (ai_tutor_scope in ('NONE', 'ALSO', 'ONLY'));
comment on column public.coupons.ai_tutor_scope is
  'NONE = courses / live only (the behaviour before AI plans existed), ALSO = courses and AI plans, ONLY = AI plans only.';

create or replace function public.ai_tutor_evaluate_coupon(
  p_code text, p_user uuid, p_plan uuid, p_country text, p_amount numeric, p_currency text)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare c record; v_disc numeric := 0; v_uses int;
begin
  if p_code is null or length(trim(p_code)) = 0 then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'NO_CODE');
  end if;
  select * into c from public.coupons where upper(code) = upper(trim(p_code));
  if not found or not c.is_active then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'INVALID_CODE');
  end if;
  if c.ai_tutor_scope = 'NONE' then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'ITEM_NOT_ELIGIBLE');
  end if;
  if c.starts_at is not null and c.starts_at > now() then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'NOT_STARTED');
  end if;
  if c.expires_at is not null and c.expires_at < now() then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'EXPIRED');
  end if;
  if c.max_uses is not null and c.used_count >= c.max_uses then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'USAGE_LIMIT_REACHED');
  end if;
  select count(*) into v_uses from public.coupon_redemptions where coupon_id = c.id and user_id = p_user;
  if v_uses >= c.max_uses_per_user then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'ALREADY_USED');
  end if;
  if p_amount < c.min_purchase then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'MIN_PURCHASE_NOT_MET');
  end if;
  if array_length(c.country_codes, 1) is not null and not (upper(p_country) = any(c.country_codes)) then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'COUNTRY_NOT_ELIGIBLE');
  end if;
  if array_length(c.teacher_ids, 1) is not null or array_length(c.course_ids, 1) is not null
     or array_length(c.live_service_ids, 1) is not null then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'ITEM_NOT_ELIGIBLE');
  end if;
  if c.kind = 'PERCENT' then
    v_disc := p_amount * (c.amount / 100.0);
  else
    if c.currency is not null and c.currency <> p_currency then
      return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'CURRENCY_MISMATCH');
    end if;
    v_disc := c.amount;
  end if;
  v_disc := least(round(v_disc, 2), p_amount);
  return jsonb_build_object('valid', true, 'discount', v_disc, 'coupon_id', c.id, 'code', c.code, 'reason', 'OK');
end $$;

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
    'plan_name', pl.name, 'plan_name_ar', pl.name_ar, 'period_days', pl.period_days, 'daily_replies', pl.daily_replies
  );
end $$;

do $$
begin
  if to_regprocedure('public._quote_checkout_base(uuid,text,uuid,uuid,uuid,text,text)') is null then
    alter function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) rename to _quote_checkout_base;
  end if;
  if to_regprocedure('public._create_order_base(uuid,text,uuid,uuid,uuid,text,text,text)') is null then
    alter function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) rename to _create_order_base;
  end if;
  if to_regprocedure('public._evaluate_coupon_base(text,uuid,text,uuid,uuid,text,numeric,text)') is null then
    alter function public.evaluate_coupon(text, uuid, text, uuid, uuid, text, numeric, text) rename to _evaluate_coupon_base;
  end if;
  if to_regprocedure('public._confirm_order_payment_base(uuid,text,text,text,jsonb)') is null then
    alter function public.confirm_order_payment(uuid, text, text, text, jsonb) rename to _confirm_order_payment_base;
  end if;
end $$;

revoke all on function public._quote_checkout_base(uuid, text, uuid, uuid, uuid, text, text) from public, anon, authenticated;
revoke all on function public._create_order_base(uuid, text, uuid, uuid, uuid, text, text, text) from public, anon, authenticated;
revoke all on function public._evaluate_coupon_base(text, uuid, text, uuid, uuid, text, numeric, text) from public, anon, authenticated;
revoke all on function public._confirm_order_payment_base(uuid, text, text, text, jsonb) from public, anon, authenticated;

create or replace function public.quote_checkout(
  p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid, p_live_group_id uuid, p_coupon_code text, p_country text)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if p_item_type = 'AI_TUTOR' then
    return public.ai_tutor_quote(p_user, p_live_plan_id, p_coupon_code, p_country);
  end if;
  return public._quote_checkout_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country);
end $$;

create or replace function public.create_order(
  p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid, p_live_group_id uuid,
  p_coupon_code text, p_country text, p_idempotency_key text)
returns public.orders language plpgsql security definer set search_path to 'public' as $$
declare q jsonb; o public.orders; v_existing public.orders;
begin
  if p_item_type <> 'AI_TUTOR' then
    return public._create_order_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country, p_idempotency_key);
  end if;

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
end $$;

create or replace function public.evaluate_coupon(
  p_code text, p_user uuid, p_item_type text, p_item_id uuid, p_teacher uuid, p_country text, p_amount numeric, p_currency text)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare c public.coupons;
begin
  if p_item_type = 'AI_TUTOR' then
    return public.ai_tutor_evaluate_coupon(p_code, p_user, p_item_id, p_country, p_amount, p_currency);
  end if;
  select * into c from public.coupons where upper(code) = upper(trim(coalesce(p_code, '')));
  if found and c.ai_tutor_scope = 'ONLY' then
    return jsonb_build_object('valid', false, 'discount', 0, 'reason', 'ITEM_NOT_ELIGIBLE');
  end if;
  return public._evaluate_coupon_base(p_code, p_user, p_item_type, p_item_id, p_teacher, p_country, p_amount, p_currency);
end $$;

create or replace function public.ai_tutor_confirm_order(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare o public.orders; pl public.ai_tutor_plans; v_start timestamptz; v_end timestamptz;
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

  select coalesce(max(ends_at), now()) into v_start
    from public.ai_tutor_entitlements where user_id = o.user_id and status = 'ACTIVE' and ends_at > now();
  v_end := v_start + make_interval(days => pl.period_days);
  insert into public.ai_tutor_entitlements(user_id, plan_id, order_id, daily_replies, starts_at, ends_at)
  values (o.user_id, pl.id, o.id, pl.daily_replies, now(), v_end)
  on conflict (order_id) do nothing;

  perform public.push_notification(o.user_id, 'SUBSCRIPTION_ACTIVE', 'AI Tutor plan active',
    pl.name || ' is active until ' || to_char(v_end, 'DD Mon YYYY') || '.',
    jsonb_build_object('order_id', o.id, 'ai_tutor_plan_id', pl.id));
  perform public.notify_staff('PAYMENT', 'Payment received',
    o.total_amount || ' ' || o.currency || ' (AI_TUTOR)', jsonb_build_object('order_id', o.id));

  update public.payment_events set processed_at = now()
   where provider = p_provider and provider_event_id = p_event_id;
  return jsonb_build_object('ok', true, 'order_id', o.id, 'status', 'PAID');
end $$;

create or replace function public.confirm_order_payment(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb default '{}'::jsonb)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare v_type text;
begin
  select item_type into v_type from public.orders where id = p_order_id;
  if v_type = 'AI_TUTOR' then
    return public.ai_tutor_confirm_order(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  end if;
  return public._confirm_order_payment_base(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
end $$;

revoke all on function public.ai_tutor_quote(uuid, uuid, text, text) from public, anon, authenticated;
revoke all on function public.ai_tutor_evaluate_coupon(text, uuid, uuid, text, numeric, text) from public, anon, authenticated;
revoke all on function public.ai_tutor_confirm_order(uuid, text, text, text, jsonb) from public, anon, authenticated;
revoke all on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) from public, anon, authenticated;
revoke all on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.confirm_order_payment(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.ai_tutor_quote(uuid, uuid, text, text) to service_role;
grant execute on function public.ai_tutor_evaluate_coupon(text, uuid, uuid, text, numeric, text) to service_role;
grant execute on function public.ai_tutor_confirm_order(uuid, text, text, text, jsonb) to service_role;
grant execute on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) to service_role;
grant execute on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) to service_role;
grant execute on function public.confirm_order_payment(uuid, text, text, text, jsonb) to service_role;

create or replace function public.ai_tutor_orders_refund_sync()
returns trigger language plpgsql security definer set search_path to 'public' as $$
begin
  if new.item_type = 'AI_TUTOR' and new.status in ('REFUNDED', 'CHARGEBACK') and old.status is distinct from new.status then
    update public.ai_tutor_entitlements set status = 'REFUNDED' where order_id = new.id;
  end if;
  return new;
end $$;
drop trigger if exists trg_ai_tutor_orders_refund_sync on public.orders;
create trigger trg_ai_tutor_orders_refund_sync after update of status on public.orders
  for each row execute function public.ai_tutor_orders_refund_sync();

create or replace function public.review_manual_payment(p_request_id uuid, p_approve boolean, p_note text default null)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare
  r public.manual_payment_requests;
  v_course text;
  v_ai text;
begin
  if not (public.is_owner() or public.has_permission('finance.manage')) then
    raise exception 'FORBIDDEN';
  end if;

  select * into r from public.manual_payment_requests where id = p_request_id for update;
  if not found then raise exception 'REQUEST_NOT_FOUND'; end if;
  if r.status <> 'PENDING' then raise exception 'REQUEST_ALREADY_REVIEWED'; end if;

  if p_approve then
    perform public.confirm_order_payment(
      r.order_id,
      'MANUAL',
      'manual_' || r.id::text,
      'manual_' || r.id::text,
      jsonb_build_object(
        'brand', r.brand,
        'sender_phone', r.sender_phone,
        'account_phone', r.account_phone,
        'proof_path', r.proof_path,
        'reviewed_by', auth.uid()
      )
    );

    update public.manual_payment_requests
       set status = 'APPROVED', review_note = nullif(btrim(coalesce(p_note, '')), ''),
           reviewed_by = auth.uid(), reviewed_at = now()
     where id = r.id;

    select title into v_course from public.courses where id = r.course_id;
    select pl.name into v_ai from public.orders o join public.ai_tutor_plans pl on pl.id = o.ai_tutor_plan_id where o.id = r.order_id;
    perform public.push_notification(
      r.user_id, 'PAYMENT_APPROVED', 'Payment approved',
      case when v_ai is not null then 'Your transfer was confirmed. ' || v_ai || ' is now active.'
           else 'Your transfer was confirmed. ' || coalesce(v_course, 'Your course') || ' is now open.' end,
      jsonb_build_object('order_id', r.order_id, 'course_id', r.course_id)
    );
  else
    perform public.fail_order_payment(
      r.order_id,
      'MANUAL',
      'manual_rejected_' || r.id::text || '_' || extract(epoch from now())::bigint::text,
      'PAYMENT_FAILED',
      coalesce(nullif(btrim(coalesce(p_note, '')), ''), 'The transfer could not be confirmed.'),
      jsonb_build_object('request_id', r.id, 'reviewed_by', auth.uid())
    );

    update public.manual_payment_requests
       set status = 'REJECTED', review_note = nullif(btrim(coalesce(p_note, '')), ''),
           reviewed_by = auth.uid(), reviewed_at = now()
     where id = r.id;
  end if;

  perform public.write_audit(
    'payments.manual_review', 'manual_payment_request', r.id::text,
    jsonb_build_object('approved', p_approve, 'order_id', r.order_id, 'amount', r.amount)
  );

  return jsonb_build_object(
    'ok', true,
    'status', case when p_approve then 'APPROVED' else 'REJECTED' end,
    'order_id', r.order_id
  );
end $$;

create or replace function public.ai_tutor_status()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean; v_role text;
  v_pro int; v_pro_until timestamptz; v_plan text; v_limit int;
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
      'global_available', true,
      'worker_url', c.worker_url,
      'avatar_url', c.avatar_url,
      'avatar_version', c.avatar_version,
      'resets_at', ((v_day + 1)::timestamp at time zone 'UTC')
    );
  end if;

  select coalesce(max(e.daily_replies), 0), max(e.ends_at) into v_pro, v_pro_until
    from public.ai_tutor_entitlements e
   where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now();
  select p.name into v_plan
    from public.ai_tutor_entitlements e join public.ai_tutor_plans p on p.id = e.plan_id
   where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now()
   order by e.daily_replies desc, e.ends_at desc limit 1;
  v_limit := greatest(c.per_user_daily, v_pro);

  select count(*) into v_used from public.ai_tutor_turns where day = v_day and user_id = v_uid and kind = 'turn';
  select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = 'turn';
  return jsonb_build_object(
    'enabled', c.enabled and c.worker_url <> '' and not coalesce(v_guest, false),
    'per_user_daily', v_limit,
    'used', v_used,
    'remaining', greatest(v_limit - v_used, 0),
    'global_available', v_global < c.global_daily,
    'worker_url', c.worker_url,
    'avatar_url', c.avatar_url,
    'avatar_version', c.avatar_version,
    'resets_at', ((v_day + 1)::timestamp at time zone 'UTC'),
    'plan_name', v_plan,
    'plan_until', v_pro_until
  );
end $$;

create or replace function public.ai_tutor_take_turn(p_kind text default 'turn')
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean; v_role text; v_ticket uuid; v_limit int; v_glimit int; v_pro int;
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

  select coalesce(max(e.daily_replies), 0) into v_pro
    from public.ai_tutor_entitlements e
   where e.user_id = v_uid and e.status = 'ACTIVE' and e.starts_at <= now() and e.ends_at > now();

  v_limit  := case when p_kind = 'turn' then greatest(c.per_user_daily, v_pro) else c.per_user_opens end;
  v_glimit := case when p_kind = 'turn' then c.global_daily   else c.global_opens   end;

  perform pg_advisory_xact_lock(hashtext('ai_tutor:' || v_uid::text));

  select count(*) into v_used from public.ai_tutor_turns where day = v_day and user_id = v_uid and kind = p_kind;
  if v_used >= v_limit then
    return jsonb_build_object('allowed', false, 'reason', 'USER_LIMIT', 'remaining', 0);
  end if;
  select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = p_kind;
  if v_global >= v_glimit then
    return jsonb_build_object('allowed', false, 'reason', 'GLOBAL_LIMIT');
  end if;

  insert into public.ai_tutor_turns(user_id, kind, day) values (v_uid, p_kind, v_day) returning id into v_ticket;

  if random() < 0.02 then
    delete from public.ai_tutor_turns where day < v_day - 45;
  end if;

  return jsonb_build_object(
    'allowed', true,
    'ticket', v_ticket,
    'remaining', case when p_kind = 'turn' then greatest(v_limit - v_used - 1, 0) else null end
  );
end $$;

create or replace function public.ai_tutor_sales_summary()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not public.is_owner() then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'active_subscribers', (select count(distinct user_id) from public.ai_tutor_entitlements
                            where status = 'ACTIVE' and starts_at <= now() and ends_at > now()),
    'paid_orders', (select count(*) from public.orders
                     where item_type = 'AI_TUTOR' and status in ('PAID', 'PARTIALLY_REFUNDED')),
    'revenue', (select coalesce(jsonb_object_agg(currency, s), '{}'::jsonb)
                  from (select currency, sum(total_amount - refunded_amount) s from public.orders
                         where item_type = 'AI_TUTOR' and status in ('PAID', 'PARTIALLY_REFUNDED')
                         group by currency) x)
  );
end $$;
revoke all on function public.ai_tutor_sales_summary() from public, anon;
grant execute on function public.ai_tutor_sales_summary() to authenticated;
