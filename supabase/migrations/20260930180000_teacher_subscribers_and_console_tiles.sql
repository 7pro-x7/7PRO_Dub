-- ============================================================================
-- 1) لوحة المالك: أرقام جديدة في owner_analytics للتايلز الجديدة
--      paid_buyers         : اللي اشتروا/اشتركوا/كوبون في كورسات مدفوعة
--      teacher_subscribers : مشتركو المعلمين (موافَق عليهم وغير موقوفين)
--      support_waiting     : محادثات دعم مستنية
--      open_grants         : كورسات مفتوحة يدويًا
-- 2) مشتركو المعلمين: قائمة + ملخص + تعديل التواريخ (التحكم التاني بدوال موجودة: التجديد/الإيقاف/الموافقة/الحذف)
--    الصلاحية: teachers.manage
-- ============================================================================

do $$
declare d text;
begin
  d := pg_get_functiondef('public.owner_analytics'::regproc);
  if d not like '%''paid_buyers''%' then
    d := replace(d,
      $q$'coupon_usage', (select coalesce(sum(used_count),0) from public.coupons)$q$,
      $q$'coupon_usage', (select coalesce(sum(used_count),0) from public.coupons),
    'paid_buyers', (select count(distinct user_id) from public._paid_course_students where source in ('PAID','MONTHLY','COUPON')),
    'teacher_subscribers', (select count(*) from public.teacher_subscriptions where approval_status = 'APPROVED' and status <> 'PAUSED'),
    'support_waiting', (select count(*) from public.support_chats where status = 'WAITING'),
    'open_grants', (select count(*) from public.course_grants where revoked_at is null)$q$);
    execute d;
  end if;
end $$;

create or replace function public._require_teacher_subscribers()
returns uuid language plpgsql stable security definer set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.has_permission('teachers.manage') then raise exception 'FORBIDDEN'; end if;
  return auth.uid();
end $$;

-- الحالة بنفس منطق المعلم: PENDING/REJECTED من الموافقة، PAUSED، وإلا من تاريخ التجديد (OVERDUE/DUE خلال 7 أيام/ACTIVE)
create or replace function public._teacher_sub_state(p_approval text, p_status text, p_next date)
returns text language sql stable set search_path = public
as $$
  select case
    when p_approval = 'PENDING' then 'PENDING'
    when p_approval = 'REJECTED' then 'REJECTED'
    when p_status = 'PAUSED' then 'PAUSED'
    else public._subscription_status_for(p_next)
  end;
$$;

create or replace function public.admin_teacher_subscribers_summary(p_teacher uuid default null)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
declare r jsonb;
begin
  perform public._require_teacher_subscribers();
  select jsonb_build_object(
    'total', count(*) filter (where st in ('ACTIVE','DUE','OVERDUE','PAUSED')),
    'students', count(distinct coalesce(student_user_id::text, lower(student_name) || '|' || coalesce(parent_phone, '')))
                  filter (where st in ('ACTIVE','DUE','OVERDUE','PAUSED')),
    'teachers', count(distinct teacher_id) filter (where st in ('ACTIVE','DUE','OVERDUE','PAUSED')),
    'monthly_value', coalesce(sum(case billing_cycle when 'WEEKLY' then monthly_amount * 52.0 / 12.0
                                                     when 'QUARTERLY' then monthly_amount / 3.0
                                                     when 'YEARLY' then monthly_amount / 12.0
                                                     else monthly_amount end) filter (where st in ('ACTIVE','DUE','OVERDUE')), 0),
    'currency', coalesce(min(currency), 'EGP'),
    'by_state', jsonb_build_object(
      'ACTIVE', count(*) filter (where st = 'ACTIVE'),
      'DUE', count(*) filter (where st = 'DUE'),
      'OVERDUE', count(*) filter (where st = 'OVERDUE'),
      'PAUSED', count(*) filter (where st = 'PAUSED'),
      'PENDING', count(*) filter (where st = 'PENDING'),
      'REJECTED', count(*) filter (where st = 'REJECTED')))
    into r
    from (select s.*, public._teacher_sub_state(s.approval_status, s.status, s.next_renewal_date) st
            from public.teacher_subscriptions s
           where p_teacher is null or s.teacher_id = p_teacher) x;
  return r;
end $$;

create or replace function public.admin_teacher_subscribers(
  p_teacher uuid default null, p_state text default null, p_query text default null, p_limit int default 300)
returns table (subscription_id uuid, teacher_id uuid, teacher_name text, student_name text, parent_name text,
               parent_phone text, group_name text, level text, start_date date, next_renewal_date date, days_left int,
               monthly_amount numeric, currency text, billing_cycle text, state text, notes text,
               student_user_id uuid, account_name text, account_email text, account_avatar text, created_at timestamptz)
language plpgsql stable security definer set search_path = public
as $$
declare
  v_q text := public._grant_norm(p_query);
  v_tokens text[] := coalesce(string_to_array(nullif(v_q, ''), ' '), '{}');
begin
  perform public._require_teacher_subscribers();
  return query
  with x as (
    select s.*, public._teacher_sub_state(s.approval_status, s.status, s.next_renewal_date) as st,
           t.full_name as tname, a.full_name as aname, a.email as aemail, a.avatar_url as aavatar
      from public.teacher_subscriptions s
      left join public.profiles t on t.id = s.teacher_id
      left join public.profiles a on a.id = s.student_user_id
     where (p_teacher is null or s.teacher_id = p_teacher)
  )
  select x.id, x.teacher_id, x.tname, x.student_name, x.parent_name, x.parent_phone, x.group_name, x.level,
         x.start_date, x.next_renewal_date, (x.next_renewal_date - current_date)::int,
         x.monthly_amount, x.currency, x.billing_cycle, x.st, x.notes,
         x.student_user_id, x.aname, x.aemail, x.aavatar, x.created_at
    from x
   where (case when p_state is null then x.st <> 'REJECTED' else x.st = upper(p_state) end)
     and not exists (
       select 1 from unnest(v_tokens) tok
        where position(tok in public._grant_norm(
                coalesce(x.student_name, '') || ' ' || coalesce(x.parent_name, '') || ' ' || coalesce(x.parent_phone, '') || ' ' ||
                coalesce(x.group_name, '') || ' ' || coalesce(x.tname, '') || ' ' || coalesce(x.aname, '') || ' ' || coalesce(x.aemail, ''))) = 0)
   order by case x.st when 'OVERDUE' then 0 when 'DUE' then 1 when 'PENDING' then 2 when 'ACTIVE' then 3 when 'PAUSED' then 4 else 5 end,
            x.next_renewal_date asc, x.student_name asc
   limit least(greatest(coalesce(p_limit, 300), 1), 500);
end $$;

-- تعديل تواريخ اشتراك (بداية / التجديد القادم) مع سجل مراجعة
create or replace function public.admin_set_subscription_dates(p_subscription uuid, p_start date, p_next date)
returns void
language plpgsql security definer set search_path = public
as $$
declare s public.teacher_subscriptions;
begin
  perform public._require_teacher_subscribers();
  select * into s from public.teacher_subscriptions where id = p_subscription for update;
  if not found then raise exception 'SUBSCRIPTION_NOT_FOUND'; end if;
  if p_start is null or p_next is null then raise exception 'INVALID_DATE'; end if;
  if p_next < p_start then raise exception 'RENEWAL_BEFORE_START'; end if;
  -- بنبعت التاريخين مع بعض، فمفيش إعادة ضبط تلقائية لتاريخ التجديد لما تتغير البداية
  update public.teacher_subscriptions set start_date = p_start, next_renewal_date = p_next where id = p_subscription;
  perform public.write_audit('subscription.dates', 'subscription', p_subscription::text,
    jsonb_build_object('start', jsonb_build_array(s.start_date, p_start), 'next', jsonb_build_array(s.next_renewal_date, p_next)));
end $$;

revoke all on function public._require_teacher_subscribers() from public, anon, authenticated;
revoke all on function public._teacher_sub_state(text, text, date) from public, anon;
revoke all on function public.admin_teacher_subscribers_summary(uuid) from public, anon;
revoke all on function public.admin_teacher_subscribers(uuid, text, text, int) from public, anon;
revoke all on function public.admin_set_subscription_dates(uuid, date, date) from public, anon;
grant execute on function public._teacher_sub_state(text, text, date) to authenticated;
grant execute on function public.admin_teacher_subscribers_summary(uuid) to authenticated;
grant execute on function public.admin_teacher_subscribers(uuid, text, text, int) to authenticated;
grant execute on function public.admin_set_subscription_dates(uuid, date, date) to authenticated;
