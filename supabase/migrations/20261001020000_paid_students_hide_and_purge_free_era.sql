-- 7PRO — تبسيط شاشة «مشتركو الكورسات المدفوعة»:
--   1) نسخة احتياطية من تسجيلات «كان مجاني» المقفولة (دخلوا وقت ما الكورس كان مجاني ولم يدفعوا)
--   2) مسحها من جدول enrollments (مفيش جداول مرتبطة بيه، وتأكدنا قبل المسح)
--   3) الشاشة (قايمة + أرقام) ماعادتش تعرض «كان مجاني» أبدًا
-- للاسترجاع: insert into public.enrollments select * from public.enrollments_purged_free_era_20261001;

create table if not exists public.enrollments_purged_free_era_20261001 as
select e.* from public.enrollments e
join public._paid_course_students s on s.enrollment_id = e.id
where s.source = 'FREE_ERA' and s.state = 'CLOSED';
alter table public.enrollments_purged_free_era_20261001 enable row level security;
revoke all on public.enrollments_purged_free_era_20261001 from public, anon, authenticated;

delete from public.enrollments
 where id in (select id from public.enrollments_purged_free_era_20261001);

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
      'FREE_ERA', 0),
    'by_state', jsonb_build_object(
      'ACTIVE', count(*) filter (where state = 'ACTIVE'),
      'EXPIRED', count(*) filter (where state = 'EXPIRED'),
      'CLOSED', count(*) filter (where state = 'CLOSED')))
    into r
    from public._paid_course_students
   where (p_course is null or course_id = p_course) and source <> 'FREE_ERA';
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
   where s.source <> 'FREE_ERA'
     and (p_course is null or s.course_id = p_course)
     and (p_source is null or s.source = p_source)
     and (p_state is null or s.state = p_state)
     and not exists (select 1 from unnest(v_tokens) tok
                      where position(tok in public._grant_norm(coalesce(p.full_name, '') || ' ' || coalesce(p.email, ''))) = 0)
   order by case s.state when 'ACTIVE' then 0 when 'EXPIRED' then 1 else 2 end,
            coalesce(s.last_paid_at, s.enrolled_at) desc
   limit least(greatest(coalesce(p_limit, 200), 1), 500);
end $$;
