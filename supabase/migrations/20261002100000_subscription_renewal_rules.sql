-- ============================================================================
-- Subscription renewal rules
--
--  1. A renewal moves BOTH dates: start_date becomes the day the renewal takes effect and
--     next_renewal_date (the end) becomes exactly one month later — a full paid month.
--     (Before, only next_renewal_date moved, so start_date stayed on the very first day and the
--     two dates stopped describing the month that was actually paid for.)
--  2. Renewal is refused before the renewal date, however many times it is tried. Because a
--     renewal always lands the next date a full month AFTER TODAY, a second attempt — or a
--     hundredth — finds the date in the future and is refused. That also closes the "catch up"
--     loophole: a subscription overdue by several months used to be renewable again and again
--     (each tap = +1 month from the old date), crediting the teacher for months nobody paid.
--  3. A renewal is always one month. Nothing can renew further ahead: the end date is computed
--     here, never taken from the caller or from request data.
--  4. Students can't open a renewal request early either (the 3-day window is gone), and a
--     pending request is re-checked when it is approved, so a stale one can never be applied
--     on top of a subscription that was renewed or edited in the meantime.
--
-- "Today" is Cairo's date, not the database's UTC date, so a renewal due today opens at
-- midnight Cairo time and not at 02:00/03:00.
-- ============================================================================

create or replace function public._cairo_today()
returns date
language sql stable
as $$ select (now() at time zone 'Africa/Cairo')::date $$;

create or replace function public._subscription_period_end(p_start date, p_cycle text)
returns date
language sql immutable
as $$
  select (p_start + case when p_cycle = 'WEEKLY' then interval '1 week' else interval '1 month' end)::date
$$;

-- The first day of the month a renewal paid for. Nullable: renewals recorded before this
-- change simply don't have it, and the earning falls back to previous_renewal_date for them.
alter table public.subscription_renewals add column if not exists period_start date;

-- One renewal per subscription per start day, enforced by the database itself for every new
-- renewal (old rows have no period_start, so the one historical duplicate is left alone).
create unique index if not exists uq_subscription_renewals_period_start
  on public.subscription_renewals (subscription_id, period_start)
  where period_start is not null;

create or replace function public._on_subscription_renewed()
returns trigger
language plpgsql security definer set search_path to 'public'
as $function$
begin
  perform public._record_subscription_earning(
    NEW.subscription_id, NEW.id,
    coalesce(NEW.period_start, NEW.previous_renewal_date), NEW.new_renewal_date,
    'Subscription renewal'
  );
  return NEW;
end;
$function$;

-- The ONE place a renewal is applied. Used by the teacher/admin "renew" button and by the
-- approval of a student's paid renewal request, so they can't drift apart.
create or replace function public._apply_subscription_renewal(
  p_subscription_id uuid,
  p_requested_start date,
  p_renewed_by uuid,
  p_amount numeric default null,
  p_currency text default null,
  p_expected_previous date default null
)
returns date
language plpgsql security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  v_today date := public._cairo_today();
  v_start date;
  v_end date;
begin
  select * into s from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found then raise exception 'SUBSCRIPTION_NOT_FOUND'; end if;
  if s.approval_status <> 'APPROVED' then raise exception 'CANNOT_RENEW_PENDING'; end if;

  -- Prepared against an older state of this subscription (renewed or edited since): refuse.
  if p_expected_previous is not null and p_expected_previous is distinct from s.next_renewal_date then
    raise exception 'RENEWAL_STALE';
  end if;

  -- Never before the renewal date.
  if v_today < s.next_renewal_date then
    raise exception 'NOT_DUE_YET:%', s.next_renewal_date;
  end if;

  -- The month starts the day the renewal was asked for (never in the future, never before the
  -- renewal date) and lasts exactly one month.
  v_start := least(coalesce(p_requested_start, v_today), v_today);
  if v_start < s.next_renewal_date then v_start := s.next_renewal_date; end if;
  v_end := public._subscription_period_end(v_start, s.billing_cycle);

  begin
    insert into public.subscription_renewals (
      subscription_id, renewed_by, previous_renewal_date, new_renewal_date, period_start, amount, currency
    ) values (
      s.id, p_renewed_by, s.next_renewal_date, v_end, v_start,
      coalesce(p_amount, s.monthly_amount), coalesce(p_currency, s.currency)
    );
  exception when unique_violation then
    raise exception 'ALREADY_RENEWED';
  end;

  -- Trusted system change: skip the "teacher edited a date, needs approval" gate. Both dates
  -- change together, so the start-date realign trigger (which only fires when start moves
  -- alone) correctly stays out of the way.
  perform set_config('app.bypass_date_approval', 'true', true);
  update public.teacher_subscriptions
     set start_date = v_start, next_renewal_date = v_end, status = 'ACTIVE'
   where id = s.id;
  perform set_config('app.bypass_date_approval', 'false', true);

  return v_end;
end;
$function$;

revoke all on function public._apply_subscription_renewal(uuid, date, uuid, numeric, text, date) from public, anon, authenticated;

-- Teacher / admin / owner "renew" button.
create or replace function public.renew_subscription(p_subscription_id uuid)
returns void
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_role text;
  v_teacher uuid;
begin
  select role::text into v_role from public.profiles where id = auth.uid();
  if v_role is null then raise exception 'PROFILE_NOT_FOUND'; end if;
  if v_role not in ('TEACHER', 'ADMIN', 'OWNER') then raise exception 'FORBIDDEN'; end if;

  select teacher_id into v_teacher from public.teacher_subscriptions where id = p_subscription_id;
  if not found then raise exception 'SUBSCRIPTION_NOT_FOUND'; end if;
  if v_teacher <> auth.uid() and v_role not in ('ADMIN', 'OWNER') then raise exception 'FORBIDDEN'; end if;

  perform public._apply_subscription_renewal(p_subscription_id, null, auth.uid());
end;
$function$;

-- A student opening a renewal request (before paying for it).
create or replace function public.request_subscription_renewal(p_subscription_id uuid)
returns uuid
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_sub public.teacher_subscriptions;
  v_today date := public._cairo_today();
  v_start date;
  v_end date;
  v_request_id uuid;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;

  select * into v_sub from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found or v_sub.student_user_id is distinct from auth.uid() then
    raise exception 'SUBSCRIPTION_NOT_FOUND';
  end if;
  if v_sub.approval_status <> 'APPROVED' then raise exception 'CANNOT_RENEW_PENDING'; end if;

  -- Not before the renewal date (no early window any more).
  if v_today < v_sub.next_renewal_date then
    raise exception 'NOT_DUE_YET:%', v_sub.next_renewal_date;
  end if;

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
      'previous_renewal_date', v_sub.next_renewal_date,
      'new_start_date', v_start,
      'new_renewal_date', v_end,
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

-- What the student's banner shows. "Renewal due" now starts ON the renewal date, not 3 days before.
create or replace function public.my_subscription_state()
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  s public.teacher_subscriptions;
  q public.subscription_payment_requests;
  v_req_id uuid; v_amount numeric; v_state text; v_teacher text; v_days int;
begin
  if auth.uid() is null then return jsonb_build_object('state', 'NONE'); end if;

  select * into s from public.teacher_subscriptions
  where student_user_id = auth.uid() and status <> 'PAUSED'
  order by case approval_status when 'APPROVED' then 0 when 'PENDING' then 1 else 2 end,
           created_at desc
  limit 1;

  if not found then return jsonb_build_object('state', 'NONE'); end if;

  select full_name into v_teacher from public.profiles where id = s.teacher_id;
  v_days := s.next_renewal_date - public._cairo_today();
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
    elsif v_days <= 0 then
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
