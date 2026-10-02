-- 7PRO — صفحة الشخص في «مشتركو الكورسات المدفوعة»: فلتر بالشخص (p_user) عشان تجيب كل كورساته.
-- بنستبدل الدالة بنفس الاسم (بدل overload) عشان PostgREST ما يتلخبطش بين نسختين.
drop function if exists public.admin_paid_students(uuid, text, text, text, int);

create or replace function public.admin_paid_students(
  p_course uuid default null, p_source text default null, p_state text default null,
  p_query text default null, p_limit int default 200, p_user uuid default null)
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
     and (p_user is null or s.user_id = p_user)
     and (p_course is null or s.course_id = p_course)
     and (p_source is null or s.source = p_source)
     and (p_state is null or s.state = p_state)
     and not exists (select 1 from unnest(v_tokens) tok
                      where position(tok in public._grant_norm(coalesce(p.full_name, '') || ' ' || coalesce(p.email, ''))) = 0)
   order by case s.state when 'ACTIVE' then 0 when 'EXPIRED' then 1 else 2 end,
            coalesce(s.last_paid_at, s.enrolled_at) desc
   limit least(greatest(coalesce(p_limit, 200), 1), 500);
end $$;

revoke all on function public.admin_paid_students(uuid, text, text, text, int, uuid) from public, anon;
grant execute on function public.admin_paid_students(uuid, text, text, text, int, uuid) to authenticated;
