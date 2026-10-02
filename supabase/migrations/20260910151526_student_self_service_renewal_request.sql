-- Student-facing renewal request: mirrors what a teacher's own renew_subscription already does
-- date-math-wise, but files it through approval_requests (RENEW_SUBSCRIPTION) since a student is
-- not a teacher/admin and must not be able to grant themselves another billing cycle unreviewed.
-- Reuses the subscription's own already-approved price — nothing invented here, unlike a brand
-- new group request, since this subscription was already priced at its first approval.
create or replace function public.request_subscription_renewal(p_subscription_id uuid)
returns uuid
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_sub public.teacher_subscriptions;
  v_new_date date;
  v_request_id uuid;
begin
  if auth.uid() is null then
    raise exception 'UNAUTHORIZED';
  end if;

  select * into v_sub from public.teacher_subscriptions where id = p_subscription_id for update;
  if not found or v_sub.student_user_id is distinct from auth.uid() then
    raise exception 'SUBSCRIPTION_NOT_FOUND';
  end if;
  if v_sub.approval_status <> 'APPROVED' then
    raise exception 'CANNOT_RENEW_PENDING';
  end if;
  if current_date < v_sub.next_renewal_date - interval '3 days' then
    raise exception 'NOT_DUE_YET:%', v_sub.next_renewal_date;
  end if;
  if exists (
    select 1 from public.approval_requests
    where target_type = 'subscription' and target_id = p_subscription_id
      and action_type = 'RENEW_SUBSCRIPTION' and status = 'PENDING'
  ) then
    raise exception 'ALREADY_REQUESTED';
  end if;

  v_new_date := v_sub.next_renewal_date + case when v_sub.billing_cycle = 'WEEKLY' then interval '1 week' else interval '1 month' end;

  insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  values (
    v_sub.teacher_id, 'RENEW_SUBSCRIPTION', 'subscription', p_subscription_id,
    jsonb_build_object(
      'previous_renewal_date', v_sub.next_renewal_date,
      'new_renewal_date', v_new_date,
      'amount', v_sub.monthly_amount,
      'currency', v_sub.currency,
      'self_service', true
    )
  ) returning id into v_request_id;

  perform public.push_notification(v_sub.teacher_id, 'RENEWAL_REQUEST',
    'طلب تجديد اشتراك', v_sub.student_name || ' طلب تجديد اشتراكه في ' || v_sub.group_name || '.',
    jsonb_build_object('subscription_id', p_subscription_id, 'request_id', v_request_id));

  perform public.notify_staff('RENEWAL_REQUEST', 'طلب تجديد اشتراك',
    v_sub.student_name || ' → ' || v_sub.group_name, jsonb_build_object('request_id', v_request_id));

  return v_request_id;
end;
$function$;

revoke all on function public.request_subscription_renewal(uuid) from public;
revoke all on function public.request_subscription_renewal(uuid) from anon;
grant execute on function public.request_subscription_renewal(uuid) to authenticated;

-- The student's own subscription rows need to be visible to them for the renewal banner —
-- confirm RLS already allows this (student_user_id = auth.uid() should already be covered by
-- an existing policy pattern used elsewhere; add one if missing, scoped to SELECT only).
do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'teacher_subscriptions'
      and policyname = 'students_read_own_subscription'
  ) then
    create policy students_read_own_subscription on public.teacher_subscriptions
      for select using (student_user_id = auth.uid());
  end if;
end $$;
;
