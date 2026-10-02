-- 7PRO — Paymob master switch + manual wallet payments (owner/admin approved)

insert into public.app_settings(key, value, description)
values (
  'payments.paymob_enabled',
  'true'::jsonb,
  'Master switch for the Paymob gateway. When false the app offers only the manual wallet transfer route.'
)
on conflict (key) do nothing;

create table if not exists public.manual_payment_accounts (
  id           uuid primary key default gen_random_uuid(),
  brand        text not null unique check (brand = upper(brand) and length(brand) between 2 and 24),
  phone        text not null default '',
  holder_name  text,
  instructions text,
  is_enabled   boolean not null default true,
  sort_order   int not null default 0,
  updated_by   uuid references public.profiles(id) on delete set null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.manual_payment_accounts is
  'Wallet numbers the platform receives manual transfers on. Managed by owner/admin only.';

insert into public.manual_payment_accounts(brand, sort_order, is_enabled)
values ('VODAFONE', 1, true), ('ORANGE', 2, true), ('ETISALAT', 3, true), ('WE', 4, true)
on conflict (brand) do nothing;

alter table public.manual_payment_accounts enable row level security;

drop policy if exists manual_accounts_read on public.manual_payment_accounts;
create policy manual_accounts_read on public.manual_payment_accounts
  for select to authenticated
  using (
    (is_enabled and coalesce(phone, '') <> '')
    or public.has_permission('settings.manage')
    or public.has_permission('finance.manage')
  );

drop policy if exists manual_accounts_write on public.manual_payment_accounts;
create policy manual_accounts_write on public.manual_payment_accounts
  for all to authenticated
  using (public.has_permission('settings.manage') or public.has_permission('finance.manage'))
  with check (public.has_permission('settings.manage') or public.has_permission('finance.manage'));

create table if not exists public.manual_payment_requests (
  id            uuid primary key default gen_random_uuid(),
  order_id      uuid not null unique references public.orders(id) on delete cascade,
  user_id       uuid not null references public.profiles(id) on delete cascade,
  course_id     uuid references public.courses(id) on delete set null,
  teacher_id    uuid references public.profiles(id) on delete set null,
  brand         text not null,
  account_phone text,
  sender_phone  text not null,
  proof_path    text not null,
  amount        numeric not null default 0,
  currency      text not null default 'EGP',
  note          text,
  status        text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  review_note   text,
  reviewed_by   uuid references public.profiles(id) on delete set null,
  reviewed_at   timestamptz,
  created_at    timestamptz not null default now()
);

comment on table public.manual_payment_requests is
  'Learner-submitted wallet transfer proofs awaiting owner/admin approval.';

create index if not exists manual_payment_requests_status_idx
  on public.manual_payment_requests(status, created_at desc);
create index if not exists manual_payment_requests_user_idx
  on public.manual_payment_requests(user_id, created_at desc);

alter table public.manual_payment_requests enable row level security;

drop policy if exists manual_requests_read on public.manual_payment_requests;
create policy manual_requests_read on public.manual_payment_requests
  for select to authenticated
  using (
    user_id = auth.uid()
    or public.has_permission('finance.read')
    or public.has_permission('finance.manage')
  );

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
  'payment-proofs', 'payment-proofs', false, 5242880,
  array['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']
)
on conflict (id) do update set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists payment_proofs_insert on storage.objects;
create policy payment_proofs_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'payment-proofs'
    and (storage.foldername(name))[1] = (auth.uid())::text
  );

drop policy if exists payment_proofs_read on storage.objects;
create policy payment_proofs_read on storage.objects
  for select to authenticated
  using (
    bucket_id = 'payment-proofs'
    and (
      (storage.foldername(name))[1] = (auth.uid())::text
      or public.has_permission('finance.read')
      or public.has_permission('finance.manage')
    )
  );

drop policy if exists payment_proofs_delete on storage.objects;
create policy payment_proofs_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'payment-proofs'
    and ((storage.foldername(name))[1] = (auth.uid())::text or public.has_permission('finance.manage'))
  );

create or replace function public.submit_manual_payment(
  p_user         uuid,
  p_order_id     uuid,
  p_brand        text,
  p_sender_phone text,
  p_proof_path   text,
  p_note         text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  o public.orders;
  a public.manual_payment_accounts;
  v_id uuid;
  v_student text;
  v_phone text;
begin
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if o.user_id <> p_user then raise exception 'FORBIDDEN'; end if;
  if o.status = 'PAID' then raise exception 'ORDER_ALREADY_PAID'; end if;
  if o.status not in ('PENDING', 'FAILED') then raise exception 'ORDER_NOT_PAYABLE:%', o.status; end if;
  if coalesce(p_proof_path, '') = '' then raise exception 'PROOF_REQUIRED'; end if;

  if split_part(p_proof_path, '/', 1) <> p_user::text then raise exception 'PROOF_INVALID'; end if;

  v_phone := regexp_replace(coalesce(p_sender_phone, ''), '\D', '', 'g');
  if v_phone !~ '^01[0125][0-9]{8}$' then raise exception 'SENDER_PHONE_INVALID'; end if;

  select * into a from public.manual_payment_accounts
    where brand = upper(p_brand) and is_enabled and coalesce(phone, '') <> '';
  if not found then raise exception 'MANUAL_METHOD_UNAVAILABLE'; end if;

  insert into public.manual_payment_requests(
    order_id, user_id, course_id, teacher_id, brand, account_phone, sender_phone,
    proof_path, amount, currency, note, status
  )
  values (
    o.id, o.user_id, o.course_id, o.teacher_id, a.brand, a.phone, v_phone,
    p_proof_path, o.total_amount, o.currency, nullif(btrim(coalesce(p_note, '')), ''), 'PENDING'
  )
  on conflict (order_id) do update set
    brand         = excluded.brand,
    account_phone = excluded.account_phone,
    sender_phone  = excluded.sender_phone,
    proof_path    = excluded.proof_path,
    amount        = excluded.amount,
    currency      = excluded.currency,
    note          = excluded.note,
    status        = 'PENDING',
    review_note   = null,
    reviewed_by   = null,
    reviewed_at   = null,
    created_at    = now()
  where public.manual_payment_requests.status <> 'APPROVED'
  returning id into v_id;

  if v_id is null then raise exception 'REQUEST_ALREADY_APPROVED'; end if;

  update public.orders
     set provider = 'MANUAL', status = 'PENDING', failure_reason = null, checkout_url = null
   where id = o.id;

  select full_name into v_student from public.profiles where id = o.user_id;

  perform public.notify_staff(
    'MANUAL_PAYMENT',
    'Payment proof to review',
    coalesce(v_student, 'A learner') || ' sent ' || o.total_amount || ' ' || o.currency ||
      ' from ' || v_phone || ' (' || a.brand || ').',
    jsonb_build_object('request_id', v_id, 'order_id', o.id, 'user_id', o.user_id)
  );

  return jsonb_build_object('ok', true, 'request_id', v_id, 'order_id', o.id, 'status', 'PENDING');
end $$;

revoke all on function public.submit_manual_payment(uuid, uuid, text, text, text, text) from public;
revoke all on function public.submit_manual_payment(uuid, uuid, text, text, text, text) from anon;
revoke all on function public.submit_manual_payment(uuid, uuid, text, text, text, text) from authenticated;
grant execute on function public.submit_manual_payment(uuid, uuid, text, text, text, text) to service_role;

create or replace function public.review_manual_payment(
  p_request_id uuid,
  p_approve    boolean,
  p_note       text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  r public.manual_payment_requests;
  v_course text;
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
    perform public.push_notification(
      r.user_id, 'PAYMENT_APPROVED', 'Payment approved',
      'Your transfer was confirmed. ' || coalesce(v_course, 'Your course') || ' is now open.',
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

revoke all on function public.review_manual_payment(uuid, boolean, text) from public;
revoke all on function public.review_manual_payment(uuid, boolean, text) from anon;
grant execute on function public.review_manual_payment(uuid, boolean, text) to authenticated;
grant execute on function public.review_manual_payment(uuid, boolean, text) to service_role;

revoke all on function public.confirm_order_payment(uuid, text, text, text, jsonb) from public;
revoke all on function public.confirm_order_payment(uuid, text, text, text, jsonb) from anon;
revoke all on function public.confirm_order_payment(uuid, text, text, text, jsonb) from authenticated;
grant execute on function public.confirm_order_payment(uuid, text, text, text, jsonb) to service_role;

revoke all on function public.fail_order_payment(uuid, text, text, text, text, jsonb) from public;
revoke all on function public.fail_order_payment(uuid, text, text, text, text, jsonb) from anon;
revoke all on function public.fail_order_payment(uuid, text, text, text, text, jsonb) from authenticated;
grant execute on function public.fail_order_payment(uuid, text, text, text, text, jsonb) to service_role;

revoke all on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) from public;
revoke all on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) from anon;
revoke all on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) from authenticated;
grant execute on function public.create_order(uuid, text, uuid, uuid, uuid, text, text, text) to service_role;

revoke all on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) from public;
revoke all on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) from anon;
revoke all on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) from authenticated;
grant execute on function public.quote_checkout(uuid, text, uuid, uuid, uuid, text, text) to service_role;

revoke all on function public.push_notification(uuid, text, text, text, jsonb) from public;
revoke all on function public.push_notification(uuid, text, text, text, jsonb) from anon;
revoke all on function public.push_notification(uuid, text, text, text, jsonb) from authenticated;
grant execute on function public.push_notification(uuid, text, text, text, jsonb) to service_role;;
