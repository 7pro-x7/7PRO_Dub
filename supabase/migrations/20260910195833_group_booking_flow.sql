-- 7PRO — "احجز في أقرب جروب": student-facing booking + payment funnel.
-- Adds only the front half; approval/earnings still run through the existing system.

alter table public.teacher_groups
  add column if not exists monthly_price numeric not null default 0,
  add column if not exists currency      text    not null default 'EGP',
  add column if not exists is_open       boolean not null default true,
  add column if not exists capacity      int     not null default 0,
  add column if not exists schedule      text;

comment on column public.teacher_groups.monthly_price is
  'Owner-set price for one billing cycle. 0 falls back to pricing.group_subscription_default.';
comment on column public.teacher_groups.is_open is
  'Owner-set: whether this group accepts new self-service bookings right now.';
comment on column public.teacher_groups.capacity is
  '0 means unlimited; otherwise booking is refused once approved members reach it.';

insert into public.app_settings(key, value, description)
values ('pricing.group_subscription_default', '0'::jsonb,
        'Fallback monthly price for a group that has no price of its own yet.')
on conflict (key) do nothing;

insert into public.app_settings(key, value, description)
values ('subscription.renewal_window_days', '3'::jsonb,
        'How many days before the renewal date the "renew now" banner appears.')
on conflict (key) do nothing;

-- ---------------------------------------------------------------- payment requests
create table if not exists public.subscription_payment_requests (
  id                  uuid primary key default gen_random_uuid(),
  subscription_id     uuid not null references public.teacher_subscriptions(id) on delete cascade,
  approval_request_id uuid not null unique references public.approval_requests(id) on delete cascade,
  user_id             uuid not null references public.profiles(id) on delete cascade,
  teacher_id          uuid not null references public.profiles(id) on delete cascade,
  group_name          text not null,
  kind                text not null default 'NEW' check (kind in ('NEW', 'RENEWAL')),
  brand               text not null,
  account_phone       text,
  sender_phone        text not null,
  proof_path          text not null,
  amount              numeric not null default 0,
  currency            text not null default 'EGP',
  note                text,
  status              text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  review_note         text,
  reviewed_by         uuid references public.profiles(id) on delete set null,
  reviewed_at         timestamptz,
  created_at          timestamptz not null default now()
);

comment on table public.subscription_payment_requests is
  'Wallet transfers for group-subscription bookings and renewals, awaiting owner/admin approval.';

create index if not exists subscription_payment_requests_status_idx
  on public.subscription_payment_requests(status, created_at desc);
create index if not exists subscription_payment_requests_user_idx
  on public.subscription_payment_requests(user_id, created_at desc);
create index if not exists subscription_payment_requests_sub_idx
  on public.subscription_payment_requests(subscription_id);

alter table public.subscription_payment_requests enable row level security;

drop policy if exists sub_payment_requests_read on public.subscription_payment_requests;
create policy sub_payment_requests_read on public.subscription_payment_requests
  for select to authenticated
  using (
    user_id = auth.uid()
    or teacher_id = auth.uid()
    or public.is_staff()
    or public.has_permission('finance.read')
    or public.has_permission('finance.manage')
  );

-- ---------------------------------------------------------------- catalogue
create or replace function public.booking_teachers()
returns table(
  teacher_id uuid, full_name text, avatar_url text, headline text,
  groups_count int, min_price numeric, currency text, students_count int, rating numeric
)
language sql stable security definer set search_path to 'public'
as $function$
  with g as (
    select tg.teacher_id as tid,
           count(*)::int as groups_count,
           min(case when tg.monthly_price > 0 then tg.monthly_price
                    else public.setting_num('pricing.group_subscription_default', 0) end) as min_price,
           min(tg.currency) as currency
    from public.teacher_groups tg
    where tg.approval_status = 'APPROVED' and tg.is_open
    group by tg.teacher_id
  )
  select p.id, coalesce(p.full_name, 'Teacher'), coalesce(tp.photo_url, p.avatar_url), tp.headline,
         g.groups_count, g.min_price, coalesce(g.currency, 'EGP'),
         coalesce(tp.students_count, 0), coalesce(tp.rating_avg, 0)
  from g
  join public.profiles p on p.id = g.tid
  join public.teacher_profiles tp on tp.id = g.tid
  where tp.status = 'APPROVED' and tp.accepting_new_students and p.status = 'ACTIVE'
  order by coalesce(tp.rating_avg, 0) desc, g.min_price asc;
$function$;

create or replace function public.booking_groups(p_teacher uuid)
returns table(
  group_id uuid, teacher_id uuid, name text, level text, schedule text,
  price numeric, currency text, capacity int, members int, seats_left int
)
language sql stable security definer set search_path to 'public'
as $function$
  select tg.id, tg.teacher_id, tg.name, tg.level, tg.schedule,
         case when tg.monthly_price > 0 then tg.monthly_price
              else public.setting_num('pricing.group_subscription_default', 0) end,
         tg.currency, tg.capacity, coalesce(m.members, 0)::int,
         case when tg.capacity > 0 then greatest(tg.capacity - coalesce(m.members, 0), 0)::int else null end
  from public.teacher_groups tg
  left join (
    select ts.teacher_id as tid, lower(ts.group_name) as gname, count(*)::int as members
    from public.teacher_subscriptions ts
    where ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED'
    group by ts.teacher_id, lower(ts.group_name)
  ) m on m.tid = tg.teacher_id and m.gname = lower(tg.name)
  where tg.teacher_id = p_teacher and tg.approval_status = 'APPROVED' and tg.is_open
  order by coalesce(m.members, 0) asc, tg.name asc;
$function$;

create or replace function public.recommend_nearest_booking()
returns table(
  group_id uuid, teacher_id uuid, teacher_name text, name text, level text,
  price numeric, currency text
)
language plpgsql stable security definer set search_path to 'public'
as $function$
declare v_level text;
begin
  select a.level into v_level
  from public.test_attempts a
  where a.user_id = auth.uid() and a.status = 'SUBMITTED'
  order by a.submitted_at desc limit 1;

  return query
    with open_groups as (
      select tg.id, tg.teacher_id, p.full_name, tg.name, tg.level,
             case when tg.monthly_price > 0 then tg.monthly_price
                  else public.setting_num('pricing.group_subscription_default', 0) end as price,
             tg.currency, coalesce(m.members, 0) as members
      from public.teacher_groups tg
      join public.profiles p on p.id = tg.teacher_id
      join public.teacher_profiles tp on tp.id = tg.teacher_id
      left join (
        select ts.teacher_id as tid, lower(ts.group_name) as gname, count(*) as members
        from public.teacher_subscriptions ts
        where ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED'
        group by ts.teacher_id, lower(ts.group_name)
      ) m on m.tid = tg.teacher_id and m.gname = lower(tg.name)
      where tg.approval_status = 'APPROVED' and tg.is_open
        and tp.status = 'APPROVED' and tp.accepting_new_students and p.status = 'ACTIVE'
        and (tg.capacity = 0 or coalesce(m.members, 0) < tg.capacity)
    )
    select og.id, og.teacher_id, og.full_name, og.name, og.level, og.price, og.currency
    from open_groups og
    order by (v_level is not null and og.level = v_level) desc, og.members asc, og.price asc
    limit 1;
end;
$function$;

-- ---------------------------------------------------------------- booking
create or replace function public.request_group_booking(
  p_group_id uuid, p_parent_name text, p_parent_phone text, p_student_name text, p_note text default null
) returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  g public.teacher_groups;
  v_price numeric; v_currency text; v_members int; v_phone text;
  v_parent text; v_student text; v_sub_id uuid; v_request_id uuid; v_teacher text;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;

  if not coalesce((select status = 'ACTIVE' from public.profiles where id = auth.uid()), false) then
    raise exception 'ACCOUNT_NOT_ACTIVE';
  end if;

  select * into g from public.teacher_groups where id = p_group_id;
  if not found or g.approval_status <> 'APPROVED' or not g.is_open then
    raise exception 'GROUP_NOT_AVAILABLE';
  end if;

  if not public.is_bookable_teacher(g.teacher_id) then
    raise exception 'TEACHER_NOT_AVAILABLE';
  end if;

  if exists (
    select 1 from public.teacher_subscriptions
    where student_user_id = auth.uid()
      and approval_status in ('PENDING', 'APPROVED') and status <> 'PAUSED'
  ) then
    raise exception 'ALREADY_SUBSCRIBED';
  end if;

  v_parent  := nullif(btrim(coalesce(p_parent_name, '')), '');
  v_student := nullif(btrim(coalesce(p_student_name, '')), '');
  if v_parent is null then raise exception 'PARENT_NAME_REQUIRED'; end if;
  if v_student is null then raise exception 'STUDENT_NAME_REQUIRED'; end if;

  v_phone := regexp_replace(coalesce(p_parent_phone, ''), '\D', '', 'g');
  if v_phone !~ '^01[0125][0-9]{8}$' then raise exception 'PARENT_PHONE_INVALID'; end if;

  if g.capacity > 0 then
    select count(*)::int into v_members
    from public.teacher_subscriptions ts
    where ts.teacher_id = g.teacher_id and lower(ts.group_name) = lower(g.name)
      and ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED';
    if v_members >= g.capacity then raise exception 'GROUP_FULL'; end if;
  end if;

  v_price := case when g.monthly_price > 0 then g.monthly_price
                  else public.setting_num('pricing.group_subscription_default', 0) end;
  v_currency := coalesce(nullif(g.currency, ''), public.setting_text('pricing.base_currency', 'EGP'));
  if v_price <= 0 then raise exception 'PRICE_NOT_SET'; end if;

  insert into public.teacher_subscriptions (
    teacher_id, group_name, parent_name, student_name, start_date,
    monthly_amount, currency, status, level, parent_phone, notes, student_user_id, approval_status
  ) values (
    g.teacher_id, g.name, v_parent, v_student, current_date,
    v_price, v_currency, 'ACTIVE', g.level, v_phone,
    nullif(btrim(coalesce(p_note, '')), ''), auth.uid(), 'PENDING'
  ) returning id into v_sub_id;

  insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  values (
    g.teacher_id, 'ADD_SUBSCRIPTION', 'subscription', v_sub_id,
    jsonb_build_object(
      'group_id', g.id, 'group_name', g.name, 'parent_name', v_parent, 'parent_phone', v_phone,
      'student_name', v_student, 'amount', v_price, 'currency', v_currency,
      'self_service', true, 'awaiting_payment', true
    )
  ) returning id into v_request_id;

  select full_name into v_teacher from public.profiles where id = g.teacher_id;

  return jsonb_build_object(
    'ok', true, 'subscription_id', v_sub_id, 'request_id', v_request_id,
    'teacher_id', g.teacher_id, 'teacher_name', coalesce(v_teacher, 'Teacher'),
    'group_name', g.name, 'amount', v_price, 'currency', v_currency
  );
end;
$function$;

revoke all on function public.request_group_booking(uuid, text, text, text, text) from public;
revoke all on function public.request_group_booking(uuid, text, text, text, text) from anon;
grant execute on function public.request_group_booking(uuid, text, text, text, text) to authenticated;

-- ---------------------------------------------------------------- paying
create or replace function public.submit_subscription_payment(
  p_subscription_id uuid, p_brand text, p_sender_phone text, p_proof_path text, p_note text default null
) returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  a public.manual_payment_accounts;
  r public.approval_requests;
  v_kind text; v_amount numeric; v_phone text; v_id uuid; v_student text;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;

  select * into s from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found or s.student_user_id is distinct from auth.uid() then
    raise exception 'SUBSCRIPTION_NOT_FOUND';
  end if;

  if s.approval_status = 'PENDING' then
    select * into r from public.approval_requests
     where target_type = 'subscription' and target_id = s.id
       and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
     order by created_at desc limit 1;
    v_kind := 'NEW';
    v_amount := s.monthly_amount;
  elsif s.approval_status = 'APPROVED' then
    select * into r from public.approval_requests
     where target_type = 'subscription' and target_id = s.id
       and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
     order by created_at desc limit 1;
    v_kind := 'RENEWAL';
    v_amount := coalesce((r.request_data->>'amount')::numeric, s.monthly_amount);
  else
    raise exception 'NOTHING_TO_PAY';
  end if;

  if r.id is null then raise exception 'NOTHING_TO_PAY'; end if;
  if coalesce(v_amount, 0) <= 0 then raise exception 'PRICE_NOT_SET'; end if;
  if coalesce(p_proof_path, '') = '' then raise exception 'PROOF_REQUIRED'; end if;
  if split_part(p_proof_path, '/', 1) <> auth.uid()::text then raise exception 'PROOF_INVALID'; end if;

  v_phone := regexp_replace(coalesce(p_sender_phone, ''), '\D', '', 'g');
  if v_phone !~ '^01[0125][0-9]{8}$' then raise exception 'SENDER_PHONE_INVALID'; end if;

  select * into a from public.manual_payment_accounts
   where brand = upper(p_brand) and is_enabled and coalesce(phone, '') <> '';
  if not found then raise exception 'MANUAL_METHOD_UNAVAILABLE'; end if;

  insert into public.subscription_payment_requests (
    subscription_id, approval_request_id, user_id, teacher_id, group_name, kind,
    brand, account_phone, sender_phone, proof_path, amount, currency, note, status
  ) values (
    s.id, r.id, auth.uid(), s.teacher_id, s.group_name, v_kind,
    a.brand, a.phone, v_phone, p_proof_path, v_amount, s.currency,
    nullif(btrim(coalesce(p_note, '')), ''), 'PENDING'
  )
  on conflict (approval_request_id) do update set
    brand = excluded.brand, account_phone = excluded.account_phone,
    sender_phone = excluded.sender_phone, proof_path = excluded.proof_path,
    amount = excluded.amount, currency = excluded.currency, note = excluded.note,
    status = 'PENDING', review_note = null, reviewed_by = null, reviewed_at = null,
    created_at = now()
  where public.subscription_payment_requests.status <> 'APPROVED'
  returning id into v_id;

  if v_id is null then raise exception 'PAYMENT_ALREADY_APPROVED'; end if;

  select coalesce(full_name, email, 'Student') into v_student from public.profiles where id = auth.uid();

  perform public.notify_staff(
    'SUBSCRIPTION_PAYMENT',
    case when v_kind = 'RENEWAL' then 'تجديد اشتراك بانتظار المراجعة' else 'حجز جروب بانتظار المراجعة' end,
    v_student || ' حوّل ' || v_amount || ' ' || s.currency || ' من ' || v_phone || ' (' || a.brand || ') — ' || s.group_name || '.',
    jsonb_build_object('request_id', v_id, 'subscription_id', s.id, 'user_id', auth.uid())
  );

  perform public.push_notification(
    s.teacher_id, 'SUBSCRIPTION_PAYMENT', 'طلب انضمام بعد الدفع',
    v_student || ' دفع للانضمام إلى ' || s.group_name || ' — بانتظار موافقة الإدارة.',
    jsonb_build_object('subscription_id', s.id)
  );

  return jsonb_build_object(
    'ok', true, 'request_id', v_id, 'subscription_id', s.id,
    'kind', v_kind, 'status', 'PENDING', 'amount', v_amount, 'currency', s.currency
  );
end;
$function$;

revoke all on function public.submit_subscription_payment(uuid, text, text, text, text) from public;
revoke all on function public.submit_subscription_payment(uuid, text, text, text, text) from anon;
grant execute on function public.submit_subscription_payment(uuid, text, text, text, text) to authenticated;

-- ---------------------------------------------------------------- reviewing
create or replace function public.review_subscription_payment(
  p_request_id uuid, p_approve boolean, p_note text default null
) returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  q public.subscription_payment_requests;
  v_req_status text; v_note text;
begin
  if not public.is_staff() then raise exception 'FORBIDDEN'; end if;

  select * into q from public.subscription_payment_requests where id = p_request_id for update;
  if not found then raise exception 'REQUEST_NOT_FOUND'; end if;
  if q.status <> 'PENDING' then raise exception 'REQUEST_ALREADY_REVIEWED'; end if;

  v_note := nullif(btrim(coalesce(p_note, '')), '');
  select status into v_req_status from public.approval_requests where id = q.approval_request_id;

  if v_req_status = 'PENDING' then
    perform public.process_approval_request(
      q.approval_request_id,
      case when p_approve then 'APPROVED' else 'REJECTED' end,
      coalesce(v_note, '')
    );
  end if;

  update public.subscription_payment_requests
     set status = case when p_approve then 'APPROVED' else 'REJECTED' end,
         review_note = v_note, reviewed_by = auth.uid(), reviewed_at = now()
   where id = q.id;

  if p_approve then
    perform public.push_notification(
      q.user_id, 'SUBSCRIPTION_APPROVED',
      case when q.kind = 'RENEWAL' then 'تم تجديد اشتراكك' else 'تم تأكيد حجزك' end,
      'تم تأكيد الدفع وانضمامك إلى ' || q.group_name || '.',
      jsonb_build_object('subscription_id', q.subscription_id)
    );
  else
    perform public.push_notification(
      q.user_id, 'SUBSCRIPTION_REJECTED', 'لم يتم تأكيد التحويل',
      coalesce(v_note, 'لم نتمكن من تأكيد التحويل. يمكنك إعادة إرسال إثبات الدفع.'),
      jsonb_build_object('subscription_id', q.subscription_id)
    );
  end if;

  perform public.write_audit(
    'subscriptions.payment_review', 'subscription_payment_request', q.id::text,
    jsonb_build_object('approved', p_approve, 'subscription_id', q.subscription_id, 'amount', q.amount)
  );

  return jsonb_build_object(
    'ok', true, 'status', case when p_approve then 'APPROVED' else 'REJECTED' end,
    'subscription_id', q.subscription_id
  );
end;
$function$;

revoke all on function public.review_subscription_payment(uuid, boolean, text) from public;
revoke all on function public.review_subscription_payment(uuid, boolean, text) from anon;
grant execute on function public.review_subscription_payment(uuid, boolean, text) to authenticated;

-- ---------------------------------------------------------------- owner controls
create or replace function public.set_group_booking(
  p_group_id uuid, p_monthly_price numeric default null, p_currency text default null,
  p_is_open boolean default null, p_capacity int default null, p_schedule text default null
) returns public.teacher_groups
language plpgsql security definer set search_path to 'public'
as $function$
declare g public.teacher_groups;
begin
  if not (public.is_staff() or public.has_permission('settings.manage')) then
    raise exception 'FORBIDDEN';
  end if;
  if p_monthly_price is not null and p_monthly_price < 0 then raise exception 'PRICE_INVALID'; end if;
  if p_capacity is not null and p_capacity < 0 then raise exception 'CAPACITY_INVALID'; end if;

  update public.teacher_groups set
    monthly_price = coalesce(p_monthly_price, monthly_price),
    currency      = coalesce(nullif(btrim(coalesce(p_currency, '')), ''), currency),
    is_open       = coalesce(p_is_open, is_open),
    capacity      = coalesce(p_capacity, capacity),
    schedule      = coalesce(nullif(btrim(coalesce(p_schedule, '')), ''), schedule),
    updated_at    = now()
  where id = p_group_id
  returning * into g;

  if g.id is null then raise exception 'GROUP_NOT_FOUND'; end if;

  perform public.write_audit(
    'groups.booking_settings', 'group', g.id::text,
    jsonb_build_object('price', g.monthly_price, 'currency', g.currency, 'is_open', g.is_open, 'capacity', g.capacity)
  );
  return g;
end;
$function$;

revoke all on function public.set_group_booking(uuid, numeric, text, boolean, int, text) from public;
revoke all on function public.set_group_booking(uuid, numeric, text, boolean, int, text) from anon;
grant execute on function public.set_group_booking(uuid, numeric, text, boolean, int, text) to authenticated;

create or replace function public.set_teacher_booking_available(p_teacher uuid, p_available boolean)
returns void
language plpgsql security definer set search_path to 'public'
as $function$
begin
  if not (public.is_staff() or public.has_permission('teachers.manage')) then
    raise exception 'FORBIDDEN';
  end if;
  update public.teacher_profiles
     set accepting_new_students = p_available, updated_at = now()
   where id = p_teacher;
  if not found then raise exception 'TEACHER_NOT_FOUND'; end if;

  perform public.write_audit(
    'teachers.booking_availability', 'teacher', p_teacher::text,
    jsonb_build_object('available', p_available)
  );
end;
$function$;

revoke all on function public.set_teacher_booking_available(uuid, boolean) from public;
revoke all on function public.set_teacher_booking_available(uuid, boolean) from anon;
grant execute on function public.set_teacher_booking_available(uuid, boolean) to authenticated;

create or replace function public.admin_booking_groups()
returns table(
  group_id uuid, teacher_id uuid, teacher_name text, name text, level text, schedule text,
  monthly_price numeric, currency text, is_open boolean, capacity int, members int, available boolean
)
language sql stable security definer set search_path to 'public'
as $function$
  select tg.id, tg.teacher_id, coalesce(p.full_name, 'Teacher'), tg.name, tg.level, tg.schedule,
         tg.monthly_price, tg.currency, tg.is_open, tg.capacity,
         coalesce(m.members, 0)::int, coalesce(tp.accepting_new_students, false)
  from public.teacher_groups tg
  join public.profiles p on p.id = tg.teacher_id
  left join public.teacher_profiles tp on tp.id = tg.teacher_id
  left join (
    select ts.teacher_id as tid, lower(ts.group_name) as gname, count(*)::int as members
    from public.teacher_subscriptions ts
    where ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED'
    group by ts.teacher_id, lower(ts.group_name)
  ) m on m.tid = tg.teacher_id and m.gname = lower(tg.name)
  where public.is_staff() or public.has_permission('settings.manage')
  order by coalesce(p.full_name, 'Teacher') asc, tg.name asc;
$function$;

revoke all on function public.admin_booking_groups() from public;
revoke all on function public.admin_booking_groups() from anon;
grant execute on function public.admin_booking_groups() to authenticated;

-- ---------------------------------------------------------------- banner truth
create or replace function public.my_subscription_state()
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  q public.subscription_payment_requests;
  v_req_id uuid; v_amount numeric; v_state text; v_teacher text; v_window int; v_days int;
begin
  if auth.uid() is null then return jsonb_build_object('state', 'NONE'); end if;

  select * into s from public.teacher_subscriptions
  where student_user_id = auth.uid() and status <> 'PAUSED'
  order by case approval_status when 'APPROVED' then 0 when 'PENDING' then 1 else 2 end,
           created_at desc
  limit 1;

  if not found then return jsonb_build_object('state', 'NONE'); end if;

  select full_name into v_teacher from public.profiles where id = s.teacher_id;
  v_window := public.setting_num('subscription.renewal_window_days', 3)::int;
  v_days := s.next_renewal_date - current_date;
  v_amount := s.monthly_amount;

  if s.approval_status = 'REJECTED' then
    v_state := 'REJECTED';
  elsif s.approval_status = 'PENDING' then
    select id into v_req_id from public.approval_requests
     where target_type = 'subscription' and target_id = s.id
       and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
     order by created_at desc limit 1;
    select * into q from public.subscription_payment_requests
     where subscription_id = s.id and kind = 'NEW'
     order by created_at desc limit 1;
    v_state := case when q.id is not null and q.status = 'PENDING' then 'PENDING_REVIEW'
                    else 'AWAITING_PAYMENT' end;
  else
    select id into v_req_id from public.approval_requests
     where target_type = 'subscription' and target_id = s.id
       and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
     order by created_at desc limit 1;
    if v_req_id is not null then
      select * into q from public.subscription_payment_requests
       where approval_request_id = v_req_id order by created_at desc limit 1;
      v_state := case when q.id is not null and q.status = 'PENDING' then 'RENEWAL_PENDING_REVIEW'
                      else 'RENEWAL_AWAITING_PAYMENT' end;
    elsif v_days <= v_window then
      v_state := 'RENEWAL_DUE';
    else
      v_state := 'ACTIVE';
    end if;
  end if;

  return jsonb_build_object(
    'state', v_state,
    'subscription_id', s.id,
    'teacher_id', s.teacher_id,
    'teacher_name', coalesce(v_teacher, 'Teacher'),
    'group_name', s.group_name,
    'student_name', s.student_name,
    'amount', v_amount,
    'currency', s.currency,
    'next_renewal_date', s.next_renewal_date,
    'days_left', v_days,
    'approval_request_id', v_req_id,
    'payment_status', q.status,
    'review_note', coalesce(q.review_note, s.review_note)
  );
end;
$function$;

revoke all on function public.my_subscription_state() from public;
revoke all on function public.my_subscription_state() from anon;
grant execute on function public.my_subscription_state() to authenticated;

-- ---------------------------------------------------------------- renewal entry point
create or replace function public.request_subscription_renewal(p_subscription_id uuid)
returns uuid
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_sub public.teacher_subscriptions;
  v_new_date date; v_request_id uuid; v_window int;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;

  select * into v_sub from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found or v_sub.student_user_id is distinct from auth.uid() then
    raise exception 'SUBSCRIPTION_NOT_FOUND';
  end if;
  if v_sub.approval_status <> 'APPROVED' then raise exception 'CANNOT_RENEW_PENDING'; end if;

  v_window := public.setting_num('subscription.renewal_window_days', 3)::int;
  if v_sub.next_renewal_date - current_date > v_window then
    raise exception 'NOT_DUE_YET:%', v_sub.next_renewal_date;
  end if;

  select id into v_request_id from public.approval_requests
   where target_type = 'subscription' and target_id = p_subscription_id
     and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
   order by created_at desc limit 1;
  if v_request_id is not null then return v_request_id; end if;

  v_new_date := v_sub.next_renewal_date
    + case when v_sub.billing_cycle = 'WEEKLY' then interval '1 week' else interval '1 month' end;

  insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  values (
    v_sub.teacher_id, 'RENEW_SUBSCRIPTION', 'subscription', p_subscription_id,
    jsonb_build_object(
      'previous_renewal_date', v_sub.next_renewal_date, 'new_renewal_date', v_new_date,
      'amount', v_sub.monthly_amount, 'currency', v_sub.currency,
      'self_service', true, 'awaiting_payment', true
    )
  ) returning id into v_request_id;

  perform public.push_notification(v_sub.teacher_id, 'RENEWAL_REQUEST',
    'طلب تجديد اشتراك', v_sub.student_name || ' طلب تجديد اشتراكه في ' || v_sub.group_name || '.',
    jsonb_build_object('subscription_id', p_subscription_id, 'request_id', v_request_id));

  return v_request_id;
end;
$function$;

revoke all on function public.request_subscription_renewal(uuid) from public;
revoke all on function public.request_subscription_renewal(uuid) from anon;
grant execute on function public.request_subscription_renewal(uuid) to authenticated;

-- ---------------------------------------------------------------- legacy shim
create or replace function public.request_group_subscription(
  p_teacher_id uuid, p_group_name text, p_note text default null
) returns uuid
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_group_id uuid; v_name text; v_phone text; v_result jsonb;
begin
  select id into v_group_id from public.teacher_groups
   where teacher_id = p_teacher_id and lower(name) = lower(p_group_name)
     and approval_status = 'APPROVED'
   limit 1;
  if v_group_id is null then raise exception 'GROUP_NOT_FOUND'; end if;

  select coalesce(full_name, email, 'Student'), coalesce(nullif(phone, ''), '01000000000')
    into v_name, v_phone
  from public.profiles where id = auth.uid();

  v_result := public.request_group_booking(v_group_id, v_name, v_phone, v_name, p_note);
  return (v_result->>'subscription_id')::uuid;
end;
$function$;;
