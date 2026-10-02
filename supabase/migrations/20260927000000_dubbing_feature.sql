-- Dubbing feature (YouTube / phone video, EN<->AR), fully wired into the EXISTING checkout
-- rails (quote_checkout / create_order / confirm_order_payment), exactly the way
-- 20260923225258_ai_tutor_paid_plans.sql wired in AI Tutor plans: a `dubbing_jobs.id` travels
-- in the same `p_live_plan_id` slot the AI Tutor plan id already reuses, so the Paymob gateway,
-- manual-wallet review, coupons-are-off-by-default, and free/100%-discount paths all keep
-- working unmodified — including backend/functions/checkout/index.ts, which needs NO changes
-- (item_type is a plain string at the Deno runtime; "DUBBING" passes straight through).
--
-- Review against the live schema before applying. Coupons are intentionally NOT wired for
-- DUBBING in this first pass (kept simple, per the ask to keep owner/admin control simple) —
-- extend dubbing_quote() the same way ai_tutor_evaluate_coupon() was added, if needed later.

-- 1) Owner/Admin-controlled settings (single row)
create table if not exists public.dubbing_settings (
  id                        int primary key default 1 check (id = 1),
  is_enabled                boolean not null default true,
  price_per_minute          numeric(10,2) not null default 5.00 check (price_per_minute >= 0),
  currency                  text not null default 'EGP' check (currency ~ '^[A-Z]{3}$'),
  min_billable_minutes      numeric(6,2) not null default 1 check (min_billable_minutes > 0),
  free_trial_minutes_total  numeric(6,2) not null default 0 check (free_trial_minutes_total >= 0),
  max_source_minutes        int not null default 60 check (max_source_minutes > 0),
  updated_at                timestamptz not null default now()
);
insert into public.dubbing_settings (id) values (1) on conflict (id) do nothing;

alter table public.dubbing_settings enable row level security;
grant select on public.dubbing_settings to authenticated;
grant update (is_enabled, price_per_minute, currency, min_billable_minutes,
              free_trial_minutes_total, max_source_minutes, updated_at)
  on public.dubbing_settings to authenticated;

drop policy if exists dubbing_settings_read on public.dubbing_settings;
create policy dubbing_settings_read on public.dubbing_settings
  for select to authenticated using (true);

drop policy if exists dubbing_settings_owner_write on public.dubbing_settings;
create policy dubbing_settings_owner_write on public.dubbing_settings
  for update to authenticated
  using (public.is_owner() or public.has_permission('finance.manage'))
  with check (public.is_owner() or public.has_permission('finance.manage'));

-- 2) Per-user free trial ledger — minutes actually consumed, updated only on payment confirm.
create table if not exists public.dubbing_free_trial_usage (
  user_id       uuid primary key references public.profiles(id) on delete cascade,
  minutes_used  numeric(8,2) not null default 0 check (minutes_used >= 0),
  updated_at    timestamptz not null default now()
);
alter table public.dubbing_free_trial_usage enable row level security;
grant select on public.dubbing_free_trial_usage to authenticated;
drop policy if exists dubbing_free_trial_usage_read on public.dubbing_free_trial_usage;
create policy dubbing_free_trial_usage_read on public.dubbing_free_trial_usage
  for select to authenticated
  using (user_id = auth.uid() or public.is_owner() or public.has_permission('finance.read'));

-- 3) Jobs table. No direct insert/update grant to authenticated — every mutation goes through
-- a SECURITY DEFINER function below, same reasoning as the rest of the money-adjacent schema.
create table if not exists public.dubbing_jobs (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references public.profiles(id) on delete cascade,
  order_id            uuid references public.orders(id) on delete set null,
  source_type         text not null check (source_type in ('YOUTUBE', 'UPLOAD')),
  source_url          text,                  -- youtube link
  source_storage_path text,                  -- uploaded phone video (Supabase Storage key)
  direction           text not null check (direction in ('EN_TO_AR', 'AR_TO_EN')),
  source_seconds      numeric(8,2) not null check (source_seconds > 0),
  minutes_billed      numeric(6,2),
  minutes_free        numeric(6,2) not null default 0,
  amount_charged      numeric(10,2),
  currency            text,
  status              text not null default 'PENDING_QUOTE'
                        check (status in ('PENDING_QUOTE','AWAITING_PAYMENT','QUEUED',
                                           'PROCESSING','DONE','FAILED','CANCELLED')),
  output_storage_path text,
  error_message       text,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  started_at          timestamptz,
  completed_at        timestamptz
);
create index if not exists dubbing_jobs_user_idx on public.dubbing_jobs(user_id, created_at desc);
create index if not exists dubbing_jobs_status_idx on public.dubbing_jobs(status);
create index if not exists dubbing_jobs_order_idx on public.dubbing_jobs(order_id);

drop trigger if exists trg_dubbing_jobs_touch on public.dubbing_jobs;
create trigger trg_dubbing_jobs_touch before update on public.dubbing_jobs
  for each row execute function public.touch_updated_at();

alter table public.dubbing_jobs enable row level security;
grant select on public.dubbing_jobs to authenticated;
drop policy if exists dubbing_jobs_read on public.dubbing_jobs;
create policy dubbing_jobs_read on public.dubbing_jobs
  for select to authenticated
  using (user_id = auth.uid() or public.is_owner() or public.has_permission('finance.read'));

-- The dubbing-server writes job status with the service_role key (see dub_routes.py) —
-- RLS does not apply to service_role, no extra policy needed for that.

alter table public.orders add column if not exists dubbing_job_id uuid
  references public.dubbing_jobs(id) on delete set null;

-- 4) Create a job (status PENDING_QUOTE). Called before quote_checkout, same shape as the
-- app creating a cart-like draft. source_seconds is measured client-side for uploads
-- (Android MediaMetadataRetriever) or via the dubbing-server's /v1/dub/probe-youtube for
-- YouTube links, so the price is known before anything is charged.
create or replace function public.dubbing_create_job(
  p_source_type text, p_source_url text, p_source_storage_path text,
  p_direction text, p_source_seconds numeric)
returns public.dubbing_jobs language plpgsql security definer set search_path to 'public' as $$
declare s public.dubbing_settings; j public.dubbing_jobs;
begin
  select * into s from public.dubbing_settings where id = 1;
  if not found or not s.is_enabled then raise exception 'DUBBING_DISABLED'; end if;
  if p_source_type not in ('YOUTUBE', 'UPLOAD') then raise exception 'INVALID_SOURCE_TYPE'; end if;
  if p_direction not in ('EN_TO_AR', 'AR_TO_EN') then raise exception 'INVALID_DIRECTION'; end if;
  if p_source_type = 'YOUTUBE' and coalesce(btrim(p_source_url), '') = '' then
    raise exception 'YOUTUBE_URL_REQUIRED';
  end if;
  if p_source_type = 'UPLOAD' and coalesce(btrim(p_source_storage_path), '') = '' then
    raise exception 'UPLOAD_PATH_REQUIRED';
  end if;
  if p_source_seconds is null or p_source_seconds <= 0 then raise exception 'INVALID_DURATION'; end if;
  if p_source_seconds > s.max_source_minutes * 60 then raise exception 'VIDEO_TOO_LONG'; end if;

  insert into public.dubbing_jobs (user_id, source_type, source_url, source_storage_path,
    direction, source_seconds)
  values (auth.uid(), p_source_type, p_source_url, p_source_storage_path, p_direction, p_source_seconds)
  returning * into j;
  return j;
end $$;
revoke all on function public.dubbing_create_job(text, text, text, text, numeric) from public, anon;
grant execute on function public.dubbing_create_job(text, text, text, text, numeric) to authenticated;

-- 5) Quote — reuses `p_live_plan_id` to carry the dubbing_job_id, exactly like AI_TUTOR does.
-- Returns the SAME generic jsonb shape quote_checkout()/ai_tutor_quote() already return, so
-- the existing PriceQuote decoder on the Android side keeps working unchanged (extra fields
-- are ignored — Backend.json has ignoreUnknownKeys = true).
create or replace function public.dubbing_quote(p_user uuid, p_job_id uuid, p_coupon_code text, p_country text)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare
  s public.dubbing_settings;
  j public.dubbing_jobs;
  v_used numeric;
  v_total_minutes numeric;
  v_free_available numeric;
  v_free_applied numeric;
  v_billable numeric;
  v_amount numeric;
begin
  select * into s from public.dubbing_settings where id = 1;
  if not found or not s.is_enabled then raise exception 'DUBBING_DISABLED'; end if;

  select * into j from public.dubbing_jobs where id = p_job_id and user_id = p_user;
  if not found then raise exception 'DUBBING_JOB_NOT_FOUND'; end if;
  if j.status not in ('PENDING_QUOTE', 'AWAITING_PAYMENT') then
    raise exception 'DUBBING_JOB_NOT_QUOTABLE:%', j.status;
  end if;

  v_total_minutes := greatest(ceil(j.source_seconds / 60.0), s.min_billable_minutes);
  select coalesce(minutes_used, 0) into v_used
    from public.dubbing_free_trial_usage where user_id = p_user;
  v_free_available := greatest(s.free_trial_minutes_total - coalesce(v_used, 0), 0);
  v_free_applied := least(v_free_available, v_total_minutes);
  v_billable := v_total_minutes - v_free_applied;
  v_amount := round(v_billable * s.price_per_minute, 2);

  return jsonb_build_object(
    'item_type', 'DUBBING', 'course_id', null, 'live_service_id', null,
    'live_plan_id', j.id, 'live_group_id', null, 'ai_tutor_plan_id', null, 'teacher_id', null,
    'country_code', upper(coalesce(p_country, '')), 'currency', s.currency,
    'base_price', round(v_total_minutes * s.price_per_minute, 2), 'list_price', v_amount,
    'discount_amount', 0, 'total_amount', v_amount,
    'coupon', jsonb_build_object('valid', false, 'discount', 0, 'reason', 'NOT_SUPPORTED'),
    'pricing_rule', jsonb_build_object('source', 'dubbing_settings', 'per_minute', s.price_per_minute),
    'seats_left', null, 'commission_rate', 0,
    'dubbing_job_id', j.id, 'source_minutes', v_total_minutes,
    'free_minutes_applied', v_free_applied, 'billable_minutes', v_billable
  );
end $$;
revoke all on function public.dubbing_quote(uuid, uuid, text, text) from public, anon, authenticated;
grant execute on function public.dubbing_quote(uuid, uuid, text, text) to service_role;

-- 6) Wire into the shared dispatch functions (created by the AI Tutor migration; re-defining
-- them here just widens the branch, no renaming needed since `_..._base` already exists).
create or replace function public.quote_checkout(
  p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid, p_live_group_id uuid,
  p_coupon_code text, p_country text)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
begin
  if p_item_type = 'AI_TUTOR' then
    return public.ai_tutor_quote(p_user, p_live_plan_id, p_coupon_code, p_country);
  elsif p_item_type = 'DUBBING' then
    return public.dubbing_quote(p_user, p_live_plan_id, p_coupon_code, p_country);
  end if;
  return public._quote_checkout_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country);
end $$;

create or replace function public.create_order(
  p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid, p_live_group_id uuid,
  p_coupon_code text, p_country text, p_idempotency_key text)
returns public.orders language plpgsql security definer set search_path to 'public' as $$
declare q jsonb; o public.orders; v_existing public.orders;
begin
  if p_item_type = 'AI_TUTOR' then
    -- unchanged from the AI Tutor migration
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

  return public._create_order_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country, p_idempotency_key);
end $$;

-- 7) Payment confirmed (Paymob webhook, or a manual-wallet transfer the owner/admin approved
-- via review_manual_payment — same call, same table, zero new payment code): flip the job to
-- QUEUED and consume the free-trial minutes it used.
create or replace function public.dubbing_confirm_order(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare o public.orders; j public.dubbing_jobs;
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

  select * into j from public.dubbing_jobs where order_id = o.id for update;
  if not found then raise exception 'DUBBING_JOB_NOT_FOUND'; end if;

  update public.orders set
    status = 'PAID', provider = p_provider, provider_ref = p_provider_ref, paid_at = now(),
    teacher_amount = 0, platform_amount = total_amount, failure_reason = null
  where id = o.id returning * into o;

  insert into public.dubbing_free_trial_usage (user_id, minutes_used)
  values (j.user_id, j.minutes_free)
  on conflict (user_id) do update
    set minutes_used = public.dubbing_free_trial_usage.minutes_used + excluded.minutes_used,
        updated_at = now();

  update public.dubbing_jobs set status = 'QUEUED' where id = j.id;

  perform public.push_notification(o.user_id, 'DUBBING_READY', 'Dubbing job queued',
    'Your video is queued for dubbing.', jsonb_build_object('order_id', o.id, 'dubbing_job_id', j.id));
  perform public.notify_staff('PAYMENT', 'Payment received',
    o.total_amount || ' ' || o.currency || ' (DUBBING)', jsonb_build_object('order_id', o.id));

  return jsonb_build_object('ok', true, 'order_id', o.id, 'status', 'PAID', 'dubbing_job_id', j.id);
end $$;
revoke all on function public.dubbing_confirm_order(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.dubbing_confirm_order(uuid, text, text, text, jsonb) to service_role;

create or replace function public.confirm_order_payment(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb default '{}'::jsonb)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare v_type text;
begin
  select item_type into v_type from public.orders where id = p_order_id;
  if v_type = 'AI_TUTOR' then
    return public.ai_tutor_confirm_order(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  elsif v_type = 'DUBBING' then
    return public.dubbing_confirm_order(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  end if;
  return public._confirm_order_payment_base(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
end $$;

revoke all on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) from public, anon, authenticated;
revoke all on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.confirm_order_payment(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) to service_role;
grant execute on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) to service_role;
grant execute on function public.confirm_order_payment(uuid, text, text, text, jsonb) to service_role;

-- Refund/chargeback sync, mirroring ai_tutor_orders_refund_sync.
create or replace function public.dubbing_orders_refund_sync()
returns trigger language plpgsql security definer set search_path to 'public' as $$
begin
  if new.item_type = 'DUBBING' and new.status in ('REFUNDED', 'CHARGEBACK') and old.status is distinct from new.status then
    update public.dubbing_jobs set status = 'CANCELLED' where order_id = new.id and status not in ('DONE');
  end if;
  return new;
end $$;
drop trigger if exists trg_dubbing_orders_refund_sync on public.orders;
create trigger trg_dubbing_orders_refund_sync after update of status on public.orders
  for each row execute function public.dubbing_orders_refund_sync();

-- 8) Called by the dubbing-server (service_role) once processing genuinely finishes.
create or replace function public.dubbing_complete_job(p_job uuid, p_output_path text, p_actual_seconds numeric default null)
returns void language plpgsql security definer set search_path to 'public' as $$
begin
  update public.dubbing_jobs
    set status = 'DONE', output_storage_path = p_output_path, completed_at = now(),
        source_seconds = coalesce(p_actual_seconds, source_seconds)
    where id = p_job;
  if not found then raise exception 'DUBBING_JOB_NOT_FOUND'; end if;
end $$;
revoke all on function public.dubbing_complete_job(uuid, text, numeric) from public, anon, authenticated;
grant execute on function public.dubbing_complete_job(uuid, text, numeric) to service_role;

create or replace function public.dubbing_fail_job(p_job uuid, p_error text)
returns void language plpgsql security definer set search_path to 'public' as $$
begin
  update public.dubbing_jobs set status = 'FAILED', error_message = p_error where id = p_job;
end $$;
revoke all on function public.dubbing_fail_job(uuid, text) from public, anon, authenticated;
grant execute on function public.dubbing_fail_job(uuid, text) to service_role;
