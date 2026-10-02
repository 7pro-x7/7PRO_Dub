-- ============================================================================
-- مشتركو الكورسات المدفوعة: قائمة بكل الطلاب على كورسات مدفوعة، بعددهم ومصدر دخولهم،
-- ومعاها تحكم (قفل / فتح / تمديد الاشتراك الشهري) من المالك أو أدمن معاه courses.grant.
--   المصدر: PAID (دفع فلوس) | MONTHLY (اشتراك شهري) | COUPON (كوبون) | GRANTED (منحة)
--           | FREE_ERA (سجّل وقت ما الكورس كان مجاني ولم يدفع)
--   الحالة: ACTIVE | EXPIRED (اشتراك شهري انتهى) | CLOSED (الوصول مقفول)
-- العرض: courses.grant أو finance.read. التحكم: courses.grant.
-- ============================================================================

create or replace function public._require_paid_students_view()
returns uuid language plpgsql stable security definer set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not (public.has_permission('courses.grant') or public.has_permission('finance.read')) then
    raise exception 'FORBIDDEN';
  end if;
  return auth.uid();
end $$;

-- مصدر الدخول + الحالة + المدفوع، لكل تسجيل في كورس مدفوع
create or replace view public._paid_course_students as
select e.id as enrollment_id, e.user_id, e.course_id, e.access_type, e.status as enrollment_status,
       e.expires_at, e.created_at as enrolled_at,
       case
         when e.access_type = 'GRANTED' then 'GRANTED'
         when e.access_type = 'MONTHLY' then 'MONTHLY'
         when o.paid_total > 0 then 'PAID'
         when o.had_coupon then 'COUPON'
         else 'FREE_ERA'
       end as source,
       case
         when e.status <> 'ACTIVE' then 'CLOSED'
         when e.expires_at is not null and e.expires_at <= now() then 'EXPIRED'
         else 'ACTIVE'
       end as state,
       coalesce(o.paid_total, 0) as paid_total,
       coalesce(o.currency, 'EGP') as currency,
       o.last_paid_at
  from public.enrollments e
  join public.courses c on c.id = e.course_id and not c.is_free
  left join lateral (
    select sum(od.total_amount - od.refunded_amount) as paid_total,
           bool_or(od.coupon_id is not null) as had_coupon,
           min(od.currency) as currency,
           max(od.paid_at) as last_paid_at
      from public.orders od
     where od.user_id = e.user_id and od.course_id = e.course_id and od.item_type = 'COURSE'
       and od.status in ('PAID', 'PARTIALLY_REFUNDED')
  ) o on true;
revoke all on public._paid_course_students from public, anon, authenticated;

create or replace function public.admin_paid_students_summary(p_course uuid default null)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
declare r jsonb;
begin
  perform public._require_paid_students_view();
  select jsonb_build_object(
    'students', count(distinct user_id),
    'buyers', count(distinct user_id) filter (where source in ('PAID', 'MONTHLY', 'COUPON')),
    'enrollments', count(*),
    'paid_total', coalesce(sum(paid_total), 0),
    'currency', coalesce(min(currency), 'EGP'),
    'by_source', jsonb_build_object(
      'PAID', count(*) filter (where source = 'PAID'),
      'MONTHLY', count(*) filter (where source = 'MONTHLY'),
      'COUPON', count(*) filter (where source = 'COUPON'),
      'GRANTED', count(*) filter (where source = 'GRANTED'),
      'FREE_ERA', count(*) filter (where source = 'FREE_ERA')),
    'by_state', jsonb_build_object(
      'ACTIVE', count(*) filter (where state = 'ACTIVE'),
      'EXPIRED', count(*) filter (where state = 'EXPIRED'),
      'CLOSED', count(*) filter (where state = 'CLOSED')))
    into r
    from public._paid_course_students
   where p_course is null or course_id = p_course;
  return r;
end $$;

create or replace function public.admin_paid_students(
  p_course uuid default null, p_source text default null, p_state text default null,
  p_query text default null, p_limit int default 200)
returns table (enrollment_id uuid, user_id uuid, full_name text, email text, avatar_url text,
               course_id uuid, course_title text, course_title_ar text, course_title_en text,
               source text, state text, access_type text, expires_at timestamptz,
               paid_total numeric, currency text, last_paid_at timestamptz, enrolled_at timestamptz)
language plpgsql stable security definer set search_path = public
as $$
declare
  v_q text := public._grant_norm(p_query);
  v_tokens text[] := coalesce(string_to_array(nullif(v_q, ''), ' '), '{}');
begin
  perform public._require_paid_students_view();
  return query
  select s.enrollment_id, s.user_id, p.full_name, p.email, p.avatar_url,
         s.course_id, c.title, c.title_ar, c.title_en,
         s.source, s.state, s.access_type, s.expires_at, s.paid_total, s.currency, s.last_paid_at, s.enrolled_at
    from public._paid_course_students s
    join public.profiles p on p.id = s.user_id
    join public.courses c on c.id = s.course_id
   where (p_course is null or s.course_id = p_course)
     and (p_source is null or s.source = p_source)
     and (p_state is null or s.state = p_state)
     and not exists (select 1 from unnest(v_tokens) tok
                      where position(tok in public._grant_norm(coalesce(p.full_name, '') || ' ' || coalesce(p.email, ''))) = 0)
   order by case s.state when 'ACTIVE' then 0 when 'EXPIRED' then 1 else 2 end,
            coalesce(s.last_paid_at, s.enrolled_at) desc
   limit least(greatest(coalesce(p_limit, 200), 1), 500);
end $$;

-- التحكم: CLOSE (يقفل الوصول) | OPEN (يفتحه) | EXTEND (يمدد اشتراك شهري p_days يوم، الافتراضي 30)
create or replace function public.admin_enrollment_action(p_enrollment uuid, p_action text, p_days int default 30)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  e public.enrollments;
  c public.courses;
  v_action text := upper(coalesce(p_action, ''));
  v_new_exp timestamptz;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.has_permission('courses.grant') then raise exception 'FORBIDDEN'; end if;
  if v_action not in ('CLOSE', 'OPEN', 'EXTEND') then raise exception 'INVALID_ACTION'; end if;

  select * into e from public.enrollments where id = p_enrollment for update;
  if not found then raise exception 'ENROLLMENT_NOT_FOUND'; end if;
  select * into c from public.courses where id = e.course_id;
  if c.is_free then raise exception 'COURSE_IS_FREE'; end if;

  if v_action = 'CLOSE' then
    update public.enrollments set status = 'PAYMENT_REQUIRED' where id = e.id;
    update public.course_grants set revoked_at = now(), revoked_by = v_uid
     where user_id = e.user_id and course_id = e.course_id and revoked_at is null;

  elsif v_action = 'OPEN' then
    if e.access_type = 'MONTHLY' and e.expires_at is not null and e.expires_at <= now() then
      raise exception 'SUBSCRIPTION_EXPIRED_EXTEND_INSTEAD';
    end if;
    update public.enrollments set status = 'ACTIVE' where id = e.id;
    if e.access_type = 'GRANTED'
       and not exists (select 1 from public.course_grants g where g.user_id = e.user_id and g.course_id = e.course_id and g.revoked_at is null) then
      insert into public.course_grants (user_id, course_id, granted_by) values (e.user_id, e.course_id, v_uid);
    end if;

  else
    if e.access_type <> 'MONTHLY' then raise exception 'NOT_MONTHLY'; end if;
    if p_days is null or p_days < 1 or p_days > 366 then raise exception 'INVALID_DAYS'; end if;
    v_new_exp := greatest(coalesce(e.expires_at, now()), now()) + make_interval(days => p_days);
    update public.enrollments set status = 'ACTIVE', expires_at = v_new_exp where id = e.id;
  end if;

  perform public.write_audit('enrollment.' || lower(v_action), 'enrollment', e.id::text,
    jsonb_build_object('user', e.user_id, 'course', e.course_id, 'days', case when v_action = 'EXTEND' then p_days end));
  perform public.push_notification(
    e.user_id, 'ENROLLMENT',
    case v_action when 'CLOSE' then 'تم إيقاف الوصول لكورس • Course access closed'
                  when 'OPEN' then 'تم فتح كورس لك • A course was opened for you'
                  else 'تم تمديد اشتراكك • Your subscription was extended' end,
    case v_action when 'CLOSE' then '«' || c.title || '» اتقفل عليك. تواصل مع الدعم لو محتاج مساعدة. — "' || c.title || '" was closed for you.'
                  when 'OPEN' then 'تم فتح «' || c.title || '» لك. — "' || c.title || '" is now open for you.'
                  else 'تم تمديد اشتراكك في «' || c.title || '». — Your "' || c.title || '" subscription was extended.' end,
    jsonb_build_object('course_id', e.course_id));

  return jsonb_build_object('ok', true, 'expires_at', v_new_exp);
end $$;

revoke all on function public._require_paid_students_view() from public, anon, authenticated;
revoke all on function public.admin_paid_students_summary(uuid) from public, anon;
revoke all on function public.admin_paid_students(uuid, text, text, text, int) from public, anon;
revoke all on function public.admin_enrollment_action(uuid, text, int) from public, anon;
grant execute on function public.admin_paid_students_summary(uuid) to authenticated;
grant execute on function public.admin_paid_students(uuid, text, text, text, int) to authenticated;
grant execute on function public.admin_enrollment_action(uuid, text, int) to authenticated;
