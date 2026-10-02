-- ============================================================================
-- 1) Push-token locale
-- ============================================================================
alter table public.device_push_tokens add column if not exists locale text not null default 'ar';

create or replace function public.register_push_token(p_token text, p_platform text default 'android'::text, p_locale text default null)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if auth.uid() is null then
    raise exception 'UNAUTHORIZED';
  end if;

  insert into public.device_push_tokens (user_id, token, platform, locale)
  values (auth.uid(), p_token, p_platform, coalesce(nullif(p_locale, ''), 'ar'))
  on conflict (token) do update set
    user_id = excluded.user_id,
    platform = excluded.platform,
    locale = coalesce(nullif(excluded.locale, ''), public.device_push_tokens.locale),
    updated_at = now();
end;
$function$;

-- ============================================================================
-- 2) Owner-controlled notification toggles
-- ============================================================================
create or replace function public.notification_kind_enabled(p_kind text)
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $function$
  select coalesce(
    (select (value #>> '{}')::boolean from public.app_settings where key = 'notifications.' || p_kind || '.enabled'),
    true
  );
$function$;

-- ============================================================================
-- 3) "Nearest group" resolver
-- ============================================================================
create or replace function public.recommend_nearest_group(p_user uuid)
returns table(teacher_id uuid, teacher_name text, group_name text, level text)
language plpgsql
stable
security definer
set search_path to 'public'
as $function$
declare
  v_level text;
  v_recommended_teacher uuid;
begin
  select a.level into v_level
  from public.test_attempts a
  where a.user_id = p_user and a.status = 'SUBMITTED'
  order by a.submitted_at desc
  limit 1;

  if v_level is not null then
    select c.teacher_id into v_recommended_teacher
    from public.courses c
    where c.status = 'PUBLISHED' and c.level = v_level
    order by c.enrollments_count desc nulls last
    limit 1;
  end if;

  if v_recommended_teacher is not null then
    return query
      select tg.teacher_id, p.full_name, tg.name, tg.level
      from public.teacher_groups tg
      join public.profiles p on p.id = tg.teacher_id
      where tg.teacher_id = v_recommended_teacher and tg.approval_status = 'APPROVED'
      order by tg.updated_at desc
      limit 1;
    if found then return; end if;
  end if;

  return query
    select tg.teacher_id, p.full_name, tg.name, tg.level
    from public.teacher_groups tg
    join public.profiles p on p.id = tg.teacher_id
    left join (
      select ts.teacher_id as sub_teacher_id, lower(ts.group_name) as gname, count(*) as members
      from public.teacher_subscriptions ts
      where ts.status <> 'PAUSED'
      group by ts.teacher_id, lower(ts.group_name)
    ) m on m.sub_teacher_id = tg.teacher_id and m.gname = lower(tg.name)
    where tg.approval_status = 'APPROVED'
    order by coalesce(m.members, 0) asc, tg.updated_at desc
    limit 1;
end;
$function$;

-- ============================================================================
-- 4) Student front door: subscribe to nearest group.
--    monthly_amount is intentionally 0 — no per-group price exists anywhere in
--    this schema, so the teacher/admin sets it at approval time, exactly like
--    any other subscription entered by hand today.
-- ============================================================================
create or replace function public.request_group_subscription(p_teacher_id uuid, p_group_name text, p_note text default null)
returns uuid
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_student_name text;
  v_sub_id uuid;
  v_request_id uuid;
  v_currency text;
begin
  if auth.uid() is null then
    raise exception 'UNAUTHORIZED';
  end if;
  if not exists (
    select 1 from public.teacher_groups
    where teacher_id = p_teacher_id and lower(name) = lower(p_group_name) and approval_status = 'APPROVED'
  ) then
    raise exception 'GROUP_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.teacher_subscriptions
    where teacher_id = p_teacher_id and student_user_id = auth.uid() and approval_status in ('PENDING', 'APPROVED')
  ) then
    raise exception 'ALREADY_REQUESTED';
  end if;

  select coalesce(full_name, email, 'Student') into v_student_name from public.profiles where id = auth.uid();
  v_currency := public.setting_text('pricing.base_currency', 'EGP');

  insert into public.teacher_subscriptions (
    teacher_id, group_name, parent_name, student_name, start_date,
    monthly_amount, currency, status, notes, student_user_id, approval_status
  ) values (
    p_teacher_id, p_group_name, v_student_name, v_student_name, current_date,
    0, v_currency, 'ACTIVE',
    trim('طلب اشتراك ذاتي عبر التطبيق (أقرب جروب) — يرجى تحديد السعر ثم الموافقة. ' || coalesce(p_note, '')),
    auth.uid(), 'PENDING'
  ) returning id into v_sub_id;

  insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
  values (
    p_teacher_id, 'ADD_SUBSCRIPTION', 'subscription', v_sub_id,
    jsonb_build_object('group_name', p_group_name, 'student_name', v_student_name, 'self_service', true)
  ) returning id into v_request_id;

  perform public.push_notification(p_teacher_id, 'SUBSCRIPTION_REQUEST',
    'طلب اشتراك جديد', v_student_name || ' طلب الانضمام إلى جروب ' || p_group_name || '.',
    jsonb_build_object('subscription_id', v_sub_id, 'request_id', v_request_id));

  perform public.notify_staff('SUBSCRIPTION_REQUEST', 'طلب اشتراك ذاتي جديد',
    v_student_name || ' → ' || p_group_name, jsonb_build_object('request_id', v_request_id));

  return v_sub_id;
end;
$function$;

revoke all on function public.request_group_subscription(uuid, text, text) from public;
revoke all on function public.request_group_subscription(uuid, text, text) from anon;
grant execute on function public.request_group_subscription(uuid, text, text) to authenticated;

revoke all on function public.recommend_nearest_group(uuid) from public;
revoke all on function public.recommend_nearest_group(uuid) from anon;
grant execute on function public.recommend_nearest_group(uuid) to authenticated;

revoke all on function public.register_push_token(text, text, text) from public;
revoke all on function public.register_push_token(text, text, text) from anon;
grant execute on function public.register_push_token(text, text, text) to authenticated;

-- ============================================================================
-- 5) Student front door: request a renewal on an already-approved subscription.
--    Reuses the subscription's own already-approved price.
-- ============================================================================
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

-- ============================================================================
-- 6) Maintenance secret + scheduled sweep (pg_cron/pg_net), and the FCM Vault
--    secret already existed from the classroom-call-push work — see maintenance
--    edge function for how it's used.
-- ============================================================================
insert into public.app_settings (key, value)
values ('maintenance.secret', to_jsonb(encode(gen_random_bytes(24), 'hex')))
on conflict (key) do nothing;

create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;
grant usage on schema cron to postgres;

select cron.schedule(
  'maintenance-sweep',
  '*/30 * * * *',
  $cron$
  select net.http_post(
    url := 'https://usbfrqbjtpvnkmmcusdi.supabase.co/functions/v1/maintenance',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-maintenance-secret', (select value #>> '{}' from public.app_settings where key = 'maintenance.secret')
    ),
    body := '{}'::jsonb
  );
  $cron$
)
where not exists (select 1 from cron.job where jobname = 'maintenance-sweep');
