-- ============================================================================
-- Booking a group for more than one student
--
-- One parent account can now book several children in one go (and add another child later).
-- Each child is still ONE subscription — own seat, own price, own renewal date, own earnings
-- for the teacher — exactly like a student a teacher adds by hand. What changes:
--
--  * teacher_subscriptions.booked_by: the account that booked and pays. The FIRST subscription
--    for a (teacher, account) pair keeps student_user_id = the account (as before); extra
--    children get student_user_id NULL. That is deliberate: the teacher_students <-> subscription
--    sync is keyed on (teacher, student_user_id) and rewrites every subscription of that pair
--    with the same dates, so two subscriptions sharing one account+teacher would overwrite
--    each other's renewal dates.
--  * request_group_booking_batch(): checks the seats for ALL children up front and creates them
--    in one transaction (all or none).
--  * submit_subscription_payments(): ONE transfer + ONE proof covers every child in the list;
--    each child gets its own payment request (own amount) tied together by batch_id.
--  * review_subscription_payment(): approving or rejecting one request of a batch does the same
--    to the whole batch — it is one transfer.
--  * my_subscription_state(): looks at every child of the account and reports the most urgent
--    one, plus the list of subscriptions the payment screen should handle together.
-- ============================================================================

alter table public.teacher_subscriptions add column if not exists booked_by uuid references public.profiles(id) on delete set null;
create index if not exists idx_teacher_subscriptions_booked_by on public.teacher_subscriptions (booked_by) where booked_by is not null;

-- Every subscription booked through the app so far belongs to its account.
update public.teacher_subscriptions set booked_by = student_user_id
 where booked_by is null and student_user_id is not null
   and exists (select 1 from public.approval_requests r
                where r.target_type = 'subscription' and r.target_id = teacher_subscriptions.id
                  and coalesce((r.request_data->>'self_service')::boolean, false));

alter table public.subscription_payment_requests add column if not exists batch_id uuid;
create index if not exists subscription_payment_requests_batch_idx on public.subscription_payment_requests (batch_id) where batch_id is not null;

-- The parent app reads every child it booked, not only the one linked to the login.
drop policy if exists students_read_own_subscription on public.teacher_subscriptions;
create policy students_read_own_subscription on public.teacher_subscriptions
  for select to authenticated
  using (student_user_id = auth.uid() or booked_by = auth.uid());

create or replace function public._is_subscription_owner(p_sub public.teacher_subscriptions)
returns boolean
language sql stable
as $$ select (p_sub.student_user_id is not null and p_sub.student_user_id = auth.uid())
          or (p_sub.booked_by is not null and p_sub.booked_by = auth.uid()) $$;

-- ---------------------------------------------------------------- one group, for the details screen

create or replace function public.booking_group_info(p_group uuid)
returns table(group_id uuid, teacher_id uuid, name text, price numeric, currency text, capacity integer, members integer, seats_left integer)
language sql stable security definer set search_path to 'public'
as $$
  select tg.id, tg.teacher_id, tg.name,
    case when tg.monthly_price > 0 then tg.monthly_price else public.setting_num('pricing.group_subscription_default', 0) end,
    coalesce(nullif(tg.currency, ''), public.setting_text('pricing.base_currency', 'EGP')),
    tg.capacity,
    coalesce(m.members, 0)::int,
    case when tg.capacity > 0 then greatest(tg.capacity - coalesce(m.members, 0), 0)::int else null end
  from public.teacher_groups tg
  left join (
    select ts.teacher_id as tid, lower(ts.group_name) as gname, count(*)::int as members
    from public.teacher_subscriptions ts
    where ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED'
    group by ts.teacher_id, lower(ts.group_name)
  ) m on m.tid = tg.teacher_id and m.gname = lower(tg.name)
  where tg.id = p_group and tg.approval_status = 'APPROVED' and tg.is_open
$$;
grant execute on function public.booking_group_info(uuid) to authenticated;
revoke execute on function public.booking_group_info(uuid) from anon;

-- ---------------------------------------------------------------- booking

create or replace function public.request_group_booking_batch(
  p_group_id uuid, p_parent_name text, p_parent_phone text, p_students text[], p_note text default null
)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  g public.teacher_groups;
  v_price numeric; v_currency text; v_members int; v_phone text;
  v_parent text; v_teacher text; v_note text;
  v_names text[] := '{}'; v_name text; v_i int;
  v_sub_id uuid; v_request_id uuid; v_existing public.teacher_subscriptions; v_has_payment boolean;
  v_link_account boolean; v_linked boolean := false; v_items jsonb := '[]'::jsonb; v_new int := 0;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not coalesce((select status = 'ACTIVE' from public.profiles where id = auth.uid()), false) then
    raise exception 'ACCOUNT_NOT_ACTIVE';
  end if;

  select * into g from public.teacher_groups where id = p_group_id;
  if not found or g.approval_status <> 'APPROVED' or not g.is_open then raise exception 'GROUP_NOT_AVAILABLE'; end if;
  if not public.is_bookable_teacher(g.teacher_id) then raise exception 'TEACHER_NOT_AVAILABLE'; end if;

  v_parent := nullif(btrim(coalesce(p_parent_name, '')), '');
  if v_parent is null then raise exception 'PARENT_NAME_REQUIRED'; end if;
  v_phone := regexp_replace(coalesce(p_parent_phone, ''), '\D', '', 'g');
  if v_phone !~ '^01[0125][0-9]{8}$' then raise exception 'PARENT_PHONE_INVALID'; end if;
  v_note := nullif(btrim(coalesce(p_note, '')), '');

  -- the children: trimmed, none empty, no repeats, at most 5 per booking
  if p_students is null or coalesce(array_length(p_students, 1), 0) = 0 then raise exception 'STUDENT_NAME_REQUIRED'; end if;
  foreach v_name in array p_students loop
    v_name := nullif(btrim(coalesce(v_name, '')), '');
    if v_name is null then raise exception 'STUDENT_NAME_REQUIRED'; end if;
    if lower(v_name) = any (select lower(x) from unnest(v_names) x) then raise exception 'DUPLICATE_STUDENT'; end if;
    v_names := v_names || v_name;
  end loop;
  if array_length(v_names, 1) > 5 then raise exception 'TOO_MANY_STUDENTS'; end if;

  v_price := case when g.monthly_price > 0 then g.monthly_price
                  else public.setting_num('pricing.group_subscription_default', 0) end;
  v_currency := coalesce(nullif(g.currency, ''), public.setting_text('pricing.base_currency', 'EGP'));
  if v_price <= 0 then raise exception 'PRICE_NOT_SET'; end if;

  -- Which children still need a new seat? One already waiting for payment in this very group is
  -- simply picked up again (so retrying never creates a second seat); one already paid for or
  -- running is refused.
  for v_i in 1 .. array_length(v_names, 1) loop
    select * into v_existing from public.teacher_subscriptions ts
     where ts.teacher_id = g.teacher_id and lower(ts.group_name) = lower(g.name)
       and lower(ts.student_name) = lower(v_names[v_i])
       and (ts.booked_by = auth.uid() or ts.student_user_id = auth.uid())
       and ts.approval_status in ('PENDING', 'APPROVED') and ts.status <> 'PAUSED'
     order by ts.created_at desc limit 1;
    if found then
      select exists (select 1 from public.subscription_payment_requests q where q.subscription_id = v_existing.id and q.status <> 'REJECTED')
        into v_has_payment;
      if v_existing.approval_status = 'APPROVED' or v_has_payment then raise exception 'ALREADY_SUBSCRIBED'; end if;
    else
      v_new := v_new + 1;
    end if;
  end loop;

  -- Seats for every NEW child, checked together.
  if g.capacity > 0 then
    select count(*)::int into v_members
      from public.teacher_subscriptions ts
     where ts.teacher_id = g.teacher_id and lower(ts.group_name) = lower(g.name)
       and ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED';
    if v_members + v_new > g.capacity then raise exception 'GROUP_FULL'; end if;
  end if;

  -- The first subscription of this account with this teacher keeps the login link; further
  -- children are linked through booked_by only (see the note at the top of this file).
  v_link_account := not exists (
    select 1 from public.teacher_subscriptions ts
     where ts.teacher_id = g.teacher_id and ts.student_user_id = auth.uid()
       and ts.approval_status in ('PENDING', 'APPROVED')
  );

  for v_i in 1 .. array_length(v_names, 1) loop
    select * into v_existing from public.teacher_subscriptions ts
     where ts.teacher_id = g.teacher_id and lower(ts.group_name) = lower(g.name)
       and lower(ts.student_name) = lower(v_names[v_i])
       and (ts.booked_by = auth.uid() or ts.student_user_id = auth.uid())
       and ts.approval_status = 'PENDING' and ts.status <> 'PAUSED'
     order by ts.created_at desc limit 1;

    if found then
      v_sub_id := v_existing.id;
      select id into v_request_id from public.approval_requests
       where target_type = 'subscription' and target_id = v_sub_id and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
    else
      insert into public.teacher_subscriptions (
        teacher_id, group_name, parent_name, student_name, start_date,
        monthly_amount, currency, status, level, parent_phone, notes, student_user_id, booked_by, approval_status
      ) values (
        g.teacher_id, g.name, v_parent, v_names[v_i], current_date,
        v_price, v_currency, 'ACTIVE', g.level, v_phone, v_note,
        case when v_link_account and not v_linked then auth.uid() else null end, auth.uid(), 'PENDING'
      ) returning id into v_sub_id;
      if v_link_account then v_linked := true; end if;

      insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
      values (
        g.teacher_id, 'ADD_SUBSCRIPTION', 'subscription', v_sub_id,
        jsonb_build_object(
          'group_id', g.id, 'group_name', g.name, 'parent_name', v_parent, 'parent_phone', v_phone,
          'student_name', v_names[v_i], 'amount', v_price, 'currency', v_currency,
          'self_service', true, 'awaiting_payment', true
        )
      ) returning id into v_request_id;
    end if;

    v_items := v_items || jsonb_build_array(jsonb_build_object(
      'subscription_id', v_sub_id, 'request_id', v_request_id, 'student_name', v_names[v_i], 'amount', v_price));
  end loop;

  select full_name into v_teacher from public.profiles where id = g.teacher_id;

  return jsonb_build_object(
    'ok', true, 'items', v_items, 'count', array_length(v_names, 1),
    'total', v_price * array_length(v_names, 1), 'currency', v_currency,
    'teacher_id', g.teacher_id, 'teacher_name', coalesce(v_teacher, 'Teacher'), 'group_name', g.name
  );
end;
$function$;

-- The original single-student call keeps working (and returns its old shape).
create or replace function public.request_group_booking(p_group_id uuid, p_parent_name text, p_parent_phone text, p_student_name text, p_note text default null)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare r jsonb;
begin
  r := public.request_group_booking_batch(p_group_id, p_parent_name, p_parent_phone, array[p_student_name], p_note);
  return jsonb_build_object(
    'ok', true,
    'subscription_id', r->'items'->0->>'subscription_id', 'request_id', r->'items'->0->>'request_id',
    'teacher_id', r->>'teacher_id', 'teacher_name', r->>'teacher_name',
    'group_name', r->>'group_name', 'amount', (r->>'total')::numeric, 'currency', r->>'currency'
  );
end;
$function$;

revoke all on function public.request_group_booking_batch(uuid, text, text, text[], text) from public, anon;
grant execute on function public.request_group_booking_batch(uuid, text, text, text[], text) to authenticated;

-- ---------------------------------------------------------------- what is owed, for the payment screen

-- For a list of subscriptions of the caller: which of them are waiting on a payment right now
-- (a new seat, or an opened renewal request), and for how much.
create or replace function public.booking_payment_state(p_ids uuid[])
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  v_items jsonb := '[]'::jsonb; v_total numeric := 0; v_currency text; v_kind text; v_req uuid; v_amount numeric;
  v_teacher text; v_group text; v_teacher_id uuid;
begin
  if auth.uid() is null then return jsonb_build_object('items', '[]'::jsonb, 'total', 0, 'count', 0); end if;

  for s in select * from public.teacher_subscriptions where id = any (p_ids) order by created_at loop
    if not public._is_subscription_owner(s) then continue; end if;
    v_kind := null; v_req := null;

    if s.approval_status = 'PENDING' then
      select id into v_req from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      if v_req is not null
         and not exists (select 1 from public.subscription_payment_requests q where q.approval_request_id = v_req and q.status = 'PENDING') then
        v_kind := 'NEW'; v_amount := s.monthly_amount;
      end if;
    elsif s.approval_status = 'APPROVED' then
      select id, coalesce((request_data->>'amount')::numeric, s.monthly_amount) into v_req, v_amount
        from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      if v_req is not null
         and not exists (select 1 from public.subscription_payment_requests q where q.approval_request_id = v_req and q.status = 'PENDING') then
        v_kind := 'RENEWAL';
      end if;
    end if;

    if v_kind is not null then
      v_items := v_items || jsonb_build_array(jsonb_build_object(
        'subscription_id', s.id, 'student_name', s.student_name, 'kind', v_kind, 'amount', v_amount, 'currency', s.currency));
      v_total := v_total + v_amount; v_currency := s.currency; v_teacher_id := s.teacher_id; v_group := s.group_name;
    end if;
  end loop;

  if v_teacher_id is not null then select full_name into v_teacher from public.profiles where id = v_teacher_id; end if;

  return jsonb_build_object(
    'items', v_items, 'count', jsonb_array_length(v_items), 'total', v_total,
    'currency', coalesce(v_currency, 'EGP'), 'teacher_name', coalesce(v_teacher, ''), 'group_name', coalesce(v_group, ''),
    'is_renewal', coalesce((v_items->0->>'kind') = 'RENEWAL', false)
  );
end;
$function$;
grant execute on function public.booking_payment_state(uuid[]) to authenticated;
revoke execute on function public.booking_payment_state(uuid[]) from anon;

-- ---------------------------------------------------------------- one transfer, several children

create or replace function public.submit_subscription_payments(
  p_subscription_ids uuid[], p_brand text, p_sender_phone text, p_proof_path text, p_note text default null
)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  a public.manual_payment_accounts;
  r public.approval_requests;
  v_ids uuid[]; v_batch uuid := gen_random_uuid();
  v_kind text; v_amount numeric; v_total numeric := 0; v_count int := 0;
  v_phone text; v_student text; v_note text; v_first_group text; v_currency text; v_sub uuid; v_req_id uuid;
  v_names text[] := '{}'; v_teachers uuid[] := '{}'; v_first_req uuid;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  select array_agg(distinct x) into v_ids from unnest(coalesce(p_subscription_ids, '{}')) x;
  if v_ids is null or array_length(v_ids, 1) is null then raise exception 'NOTHING_TO_PAY'; end if;
  if array_length(v_ids, 1) > 10 then raise exception 'TOO_MANY_STUDENTS'; end if;

  if coalesce(p_proof_path, '') = '' then raise exception 'PROOF_REQUIRED'; end if;
  if split_part(p_proof_path, '/', 1) <> auth.uid()::text then raise exception 'PROOF_INVALID'; end if;
  v_phone := regexp_replace(coalesce(p_sender_phone, ''), '\D', '', 'g');
  if v_phone !~ '^01[0125][0-9]{8}$' then raise exception 'SENDER_PHONE_INVALID'; end if;
  select * into a from public.manual_payment_accounts
   where brand = upper(p_brand) and is_enabled and coalesce(phone, '') <> '';
  if not found then raise exception 'MANUAL_METHOD_UNAVAILABLE'; end if;

  for v_sub in select unnest(v_ids) loop
    select * into s from public.teacher_subscriptions where id = v_sub for update;
    if not found or not public._is_subscription_owner(s) then raise exception 'SUBSCRIPTION_NOT_FOUND'; end if;

    if s.approval_status = 'PENDING' then
      select * into r from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      v_kind := 'NEW'; v_amount := s.monthly_amount;
    elsif s.approval_status = 'APPROVED' then
      select * into r from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      v_kind := 'RENEWAL';
      v_amount := coalesce((r.request_data->>'amount')::numeric, s.monthly_amount);
    else
      raise exception 'NOTHING_TO_PAY';
    end if;
    if r.id is null then raise exception 'NOTHING_TO_PAY'; end if;
    if coalesce(v_amount, 0) <= 0 then raise exception 'PRICE_NOT_SET'; end if;

    v_count := v_count + 1; v_total := v_total + v_amount; v_currency := s.currency;
    v_names := v_names || s.student_name; v_teachers := v_teachers || s.teacher_id;
    if v_first_group is null then v_first_group := s.group_name; end if;
  end loop;

  v_note := nullif(btrim(coalesce(p_note, '')), '');
  if v_count > 1 then
    v_note := '[دفعة واحدة لـ ' || v_count || ' طلاب — الإجمالي ' || v_total || ' ' || v_currency || '] ' || coalesce(v_note, '');
  end if;

  for v_sub in select unnest(v_ids) loop
    select * into s from public.teacher_subscriptions where id = v_sub;
    if s.approval_status = 'PENDING' then
      select * into r from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      v_kind := 'NEW'; v_amount := s.monthly_amount;
    else
      select * into r from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      v_kind := 'RENEWAL'; v_amount := coalesce((r.request_data->>'amount')::numeric, s.monthly_amount);
    end if;

    v_req_id := null;
    insert into public.subscription_payment_requests (
      subscription_id, approval_request_id, user_id, teacher_id, group_name, kind,
      brand, account_phone, sender_phone, proof_path, amount, currency, note, status, batch_id
    ) values (
      s.id, r.id, auth.uid(), s.teacher_id, s.group_name, v_kind,
      a.brand, a.phone, v_phone, p_proof_path, v_amount, s.currency, v_note, 'PENDING',
      case when v_count > 1 then v_batch end
    )
    on conflict (approval_request_id) do update set
      brand = excluded.brand, account_phone = excluded.account_phone,
      sender_phone = excluded.sender_phone, proof_path = excluded.proof_path,
      amount = excluded.amount, currency = excluded.currency, note = excluded.note,
      batch_id = excluded.batch_id,
      status = 'PENDING', review_note = null, reviewed_by = null, reviewed_at = null, created_at = now()
    where public.subscription_payment_requests.status <> 'APPROVED'
    returning id into v_req_id;
    if v_req_id is null then raise exception 'PAYMENT_ALREADY_APPROVED'; end if;
    if v_first_req is null then v_first_req := v_req_id; end if;
  end loop;

  select coalesce(full_name, email, 'Student') into v_student from public.profiles where id = auth.uid();

  perform public.notify_staff(
    'SUBSCRIPTION_PAYMENT',
    case when v_kind = 'RENEWAL' then 'تجديد اشتراك بانتظار المراجعة' else 'حجز جروب بانتظار المراجعة' end,
    v_student || ' حوّل ' || v_total || ' ' || v_currency || ' من ' || v_phone || ' (' || a.brand || ') — ' ||
      v_first_group || case when v_count > 1 then ' — ' || v_count || ' طلاب: ' || array_to_string(v_names, '، ') else '' end || '.',
    jsonb_build_object('request_id', v_first_req, 'subscription_id', v_ids[1], 'user_id', auth.uid(), 'batch_id', case when v_count > 1 then v_batch end)
  );

  perform public.push_notification(t, 'SUBSCRIPTION_PAYMENT',
    case when v_kind = 'RENEWAL' then 'طلب تجديد بعد الدفع' else 'طلب انضمام بعد الدفع' end,
    v_student || ' دفع ' || case when v_kind = 'RENEWAL' then 'لتجديد ' else 'للانضمام إلى ' end || v_first_group || ' — بانتظار موافقة الإدارة.',
    jsonb_build_object('subscription_id', v_ids[1]))
  from (select distinct unnest(v_teachers) t) x;

  return jsonb_build_object('ok', true, 'count', v_count, 'total', v_total, 'currency', v_currency, 'kind', v_kind, 'status', 'PENDING');
end;
$function$;
revoke all on function public.submit_subscription_payments(uuid[], text, text, text, text) from public, anon;
grant execute on function public.submit_subscription_payments(uuid[], text, text, text, text) to authenticated;

-- The single-subscription call (still used by anything older) goes through the same code.
create or replace function public.submit_subscription_payment(p_subscription_id uuid, p_brand text, p_sender_phone text, p_proof_path text, p_note text default null)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare r jsonb;
begin
  r := public.submit_subscription_payments(array[p_subscription_id], p_brand, p_sender_phone, p_proof_path, p_note);
  return r || jsonb_build_object('subscription_id', p_subscription_id);
end;
$function$;

-- ---------------------------------------------------------------- review: a batch is one transfer

create or replace function public.review_subscription_payment(p_request_id uuid, p_approve boolean, p_note text default null)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  q public.subscription_payment_requests;
  v_batch uuid; v_req_status text; v_note text; v_ids uuid[]; v_n int := 0; v_group text;
begin
  if not public.is_staff() then raise exception 'FORBIDDEN'; end if;

  select * into q from public.subscription_payment_requests where id = p_request_id for update;
  if not found then raise exception 'REQUEST_NOT_FOUND'; end if;
  if q.status <> 'PENDING' then raise exception 'REQUEST_ALREADY_REVIEWED'; end if;
  v_batch := q.batch_id;
  v_note := nullif(btrim(coalesce(p_note, '')), '');

  -- This request, plus every other pending one that came with the same transfer.
  select array_agg(x.id order by x.created_at) into v_ids from (
    select id, created_at from public.subscription_payment_requests
     where status = 'PENDING' and (id = p_request_id or (v_batch is not null and batch_id = v_batch))
     for update
  ) x;

  for q in select * from public.subscription_payment_requests where id = any (v_ids) order by created_at loop
    select status into v_req_status from public.approval_requests where id = q.approval_request_id;
    if v_req_status = 'PENDING' then
      perform public.process_approval_request(q.approval_request_id,
        case when p_approve then 'APPROVED' else 'REJECTED' end, coalesce(v_note, ''));
    end if;

    update public.subscription_payment_requests
       set status = case when p_approve then 'APPROVED' else 'REJECTED' end,
           review_note = v_note, reviewed_by = auth.uid(), reviewed_at = now()
     where id = q.id;
    v_n := v_n + 1;
    if v_group is null then v_group := q.group_name; end if;

    perform public.write_audit('subscriptions.payment_review', 'subscription_payment_request', q.id::text,
      jsonb_build_object('approved', p_approve, 'subscription_id', q.subscription_id, 'amount', q.amount, 'batch', v_batch));
  end loop;

  -- One message to the parent for the whole transfer.
  select * into q from public.subscription_payment_requests where id = p_request_id;
  if p_approve then
    perform public.push_notification(
      q.user_id, 'SUBSCRIPTION_APPROVED',
      case when q.kind = 'RENEWAL' then 'تم تجديد اشتراكك' else 'تم تأكيد حجزك' end,
      'تم تأكيد الدفع وانضمامك إلى ' || v_group || case when v_n > 1 then ' (' || v_n || ' طلاب)' else '' end || '.',
      jsonb_build_object('subscription_id', q.subscription_id)
    );
  else
    perform public.push_notification(
      q.user_id, 'SUBSCRIPTION_REJECTED', 'لم يتم تأكيد التحويل',
      coalesce(v_note, 'لم نتمكن من تأكيد التحويل. يمكنك إعادة إرسال إثبات الدفع.'),
      jsonb_build_object('subscription_id', q.subscription_id)
    );
  end if;

  return jsonb_build_object('ok', true, 'status', case when p_approve then 'APPROVED' else 'REJECTED' end,
                            'subscription_id', q.subscription_id, 'count', v_n);
end;
$function$;

-- ---------------------------------------------------------------- renewal: any child of the account

create or replace function public.request_subscription_renewal(p_subscription_id uuid)
returns uuid
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_sub public.teacher_subscriptions;
  v_today date := public._cairo_today();
  v_start date; v_end date; v_request_id uuid;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;

  select * into v_sub from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found or not public._is_subscription_owner(v_sub) then raise exception 'SUBSCRIPTION_NOT_FOUND'; end if;
  if v_sub.approval_status <> 'APPROVED' then raise exception 'CANNOT_RENEW_PENDING'; end if;

  if v_today < v_sub.next_renewal_date then raise exception 'NOT_DUE_YET:%', v_sub.next_renewal_date; end if;

  select id into v_request_id from public.approval_requests
   where target_type = 'subscription' and target_id = p_subscription_id
     and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
   order by created_at desc limit 1;
  if v_request_id is not null then return v_request_id; end if;

  v_start := v_today;
  v_end := public._subscription_period_end(v_start, v_sub.billing_cycle);

  insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  values (
    v_sub.teacher_id, 'RENEW_SUBSCRIPTION', 'subscription', p_subscription_id,
    jsonb_build_object(
      'previous_renewal_date', v_sub.next_renewal_date, 'new_start_date', v_start, 'new_renewal_date', v_end,
      'amount', v_sub.monthly_amount, 'currency', v_sub.currency, 'self_service', true, 'awaiting_payment', true
    )
  ) returning id into v_request_id;

  perform public.push_notification(v_sub.teacher_id, 'RENEWAL_REQUEST',
    'طلب تجديد اشتراك', v_sub.student_name || ' طلب تجديد اشتراكه في ' || v_sub.group_name || '.',
    jsonb_build_object('subscription_id', p_subscription_id, 'request_id', v_request_id));

  return v_request_id;
end;
$function$;

-- ---------------------------------------------------------------- banner state across all children

create or replace function public.my_subscription_state()
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  q public.subscription_payment_requests;
  v_best jsonb := null; v_best_rank int := 99; v_rank int;
  v_req_id uuid; v_state text; v_teacher text; v_days int; v_today date := public._cairo_today();
  v_ids uuid[]; v_pay_ids uuid[]; v_total_children int := 0;
  v_cur jsonb;
begin
  if auth.uid() is null then return jsonb_build_object('state', 'NONE'); end if;

  for s in
    select * from public.teacher_subscriptions
     where (student_user_id = auth.uid() or booked_by = auth.uid()) and status <> 'PAUSED'
     order by next_renewal_date, created_at
  loop
    v_total_children := v_total_children + 1;
    q := null; v_req_id := null;
    v_days := s.next_renewal_date - v_today;

    if s.approval_status = 'REJECTED' then
      v_state := 'REJECTED'; v_rank := 6;
    elsif s.approval_status = 'PENDING' then
      select id into v_req_id from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'ADD_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      select * into q from public.subscription_payment_requests
       where subscription_id = s.id and kind = 'NEW' order by created_at desc limit 1;
      if q.id is not null and q.status = 'PENDING' then v_state := 'PENDING_REVIEW'; v_rank := 4;
      else v_state := 'AWAITING_PAYMENT'; v_rank := 1; end if;
    else
      select id into v_req_id from public.approval_requests
       where target_type = 'subscription' and target_id = s.id and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
       order by created_at desc limit 1;
      if v_req_id is not null then
        select * into q from public.subscription_payment_requests where approval_request_id = v_req_id order by created_at desc limit 1;
        if q.id is not null and q.status = 'PENDING' then v_state := 'RENEWAL_PENDING_REVIEW'; v_rank := 4;
        else v_state := 'RENEWAL_AWAITING_PAYMENT'; v_rank := 2; end if;
      elsif v_days <= 0 then
        v_state := 'RENEWAL_DUE'; v_rank := 3;
      else
        v_state := 'ACTIVE'; v_rank := 5;
      end if;
    end if;

    -- every child in the same state as the most urgent one is handled together
    if v_rank < v_best_rank then
      v_best_rank := v_rank; v_pay_ids := array[s.id];
      select full_name into v_teacher from public.profiles where id = s.teacher_id;
      v_best := jsonb_build_object(
        'state', v_state, 'subscription_id', s.id, 'teacher_id', s.teacher_id,
        'teacher_name', coalesce(v_teacher, 'Teacher'), 'group_name', s.group_name, 'student_name', s.student_name,
        'amount', s.monthly_amount, 'currency', s.currency, 'next_renewal_date', s.next_renewal_date,
        'days_left', v_days, 'approval_request_id', v_req_id, 'payment_status', q.status,
        'review_note', coalesce(q.review_note, s.review_note));
    elsif v_rank = v_best_rank
          and s.teacher_id = (v_best->>'teacher_id')::uuid
          and lower(s.group_name) = lower(v_best->>'group_name') then
      v_pay_ids := v_pay_ids || s.id;
    end if;
  end loop;

  if v_best is null then return jsonb_build_object('state', 'NONE'); end if;

  return v_best || jsonb_build_object(
    'subscription_ids', to_jsonb(v_pay_ids), 'students_count', v_total_children,
    'amount', (v_best->>'amount')::numeric * array_length(v_pay_ids, 1));
end;
$function$;
