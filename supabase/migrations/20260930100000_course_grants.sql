-- ============================================================================
-- فتح كورس مدفوع لمستخدم بعينه (منحة يدوية) وقفله عليه
--   * المالك (أو أدمن معاه courses.grant) يفتح/يقفل لأي مستخدم.
--   * المعلم يفتح/يقفل لطلابه بس، بشرط: teacher_profiles.can_grant_courses = true
--     والكورس ضمن teacher_grant_courses الخاصة بيه (المالك هو اللي بيحددها).
--   * البحث بالاسم والبريد دقيق: كل كلمة مكتوبة لازم تكون موجودة (عربي بدون تشكيل/همزات).
-- المنحة بتتسجل في enrollments بـ access_type = 'GRANTED' فمفيش تحويل تلقائي
-- (مجاني→مدفوع / شهري فقط) بيقفلها. السحب بيرجّع الحالة PAYMENT_REQUIRED.
-- ============================================================================

alter table public.enrollments drop constraint if exists enrollments_access_type_check;
alter table public.enrollments
  add constraint enrollments_access_type_check check (access_type in ('FREE', 'ONE_TIME', 'MONTHLY', 'GRANTED'));

alter table public.teacher_profiles add column if not exists can_grant_courses boolean not null default false;

create table if not exists public.teacher_grant_courses (
  teacher_id uuid not null references public.teacher_profiles(id) on delete cascade,
  course_id  uuid not null references public.courses(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (teacher_id, course_id)
);
alter table public.teacher_grant_courses enable row level security;

create table if not exists public.course_grants (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade,
  course_id  uuid not null references public.courses(id) on delete cascade,
  granted_by uuid references public.profiles(id) on delete set null,
  granted_at timestamptz not null default now(),
  revoked_at timestamptz,
  revoked_by uuid references public.profiles(id) on delete set null
);
create unique index if not exists course_grants_one_active
  on public.course_grants (user_id, course_id) where revoked_at is null;
alter table public.course_grants enable row level security;

-- تطبيع النص للبحث: حروف صغيرة، توحيد الهمزات والياء/الألف المقصورة والتاء المربوطة، أرقام عربية→لاتينية، بدون تشكيل/تطويل
create or replace function public._grant_norm(t text)
returns text language sql immutable
as $$
  select btrim(regexp_replace(regexp_replace(
    translate(lower(coalesce(t, '')), 'أإآٱىئةؤ٠١٢٣٤٥٦٧٨٩', 'ااااييهو0123456789'),
    '[\u064B-\u065F\u0670\u0640]', '', 'g'), '\s+', ' ', 'g'));
$$;

-- مين يقدر يتصرف على (كورس، مستخدم): STAFF أو TEACHER، وإلا FORBIDDEN
create or replace function public._grant_caller_kind(p_course uuid, p_target uuid default null)
returns text
language plpgsql stable security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if public.has_permission('courses.grant') then return 'STAFF'; end if;
  if exists (select 1 from public.profiles where id = v_uid and role = 'TEACHER')
     and exists (select 1 from public.teacher_profiles tp
                  where tp.id = v_uid and tp.can_grant_courses and tp.status::text <> 'SUSPENDED')
     and exists (select 1 from public.teacher_grant_courses g where g.teacher_id = v_uid and g.course_id = p_course)
     and (p_target is null or exists (
            select 1 from public.teacher_students ts
             where ts.teacher_id = v_uid and ts.student_user_id = p_target))
  then
    return 'TEACHER';
  end if;
  raise exception 'FORBIDDEN';
end $$;

-- بحث دقيق بالاسم والبريد. المالك: كل المستخدمين (حرفين على الأقل). المعلم: طلابه بس.
create or replace function public.grant_course_search_users(p_course uuid, p_query text, p_limit int default 30)
returns table (user_id uuid, full_name text, email text, avatar_url text, has_access boolean, is_granted boolean)
language plpgsql stable security definer set search_path = public
as $$
declare
  v_kind text := public._grant_caller_kind(p_course, null);
  v_q text := public._grant_norm(p_query);
  v_tokens text[] := coalesce(string_to_array(nullif(v_q, ''), ' '), '{}');
begin
  if v_kind = 'STAFF' and length(v_q) < 2 then return; end if;
  return query
  select p.id, p.full_name, p.email, p.avatar_url,
         exists (select 1 from public.enrollments e
                  where e.user_id = p.id and e.course_id = p_course and e.status = 'ACTIVE'
                    and (e.expires_at is null or e.expires_at > now())),
         exists (select 1 from public.enrollments e
                  where e.user_id = p.id and e.course_id = p_course and e.status = 'ACTIVE'
                    and e.access_type = 'GRANTED')
    from public.profiles p
   where coalesce(p.is_guest, false) = false
     and p.status::text = 'ACTIVE'
     and (v_kind = 'STAFF' or exists (
            select 1 from public.teacher_students ts
             where ts.teacher_id = auth.uid() and ts.student_user_id = p.id))
     and not exists (select 1 from unnest(v_tokens) tok
                      where position(tok in public._grant_norm(coalesce(p.full_name, '') || ' ' || coalesce(p.email, ''))) = 0)
   order by (public._grant_norm(p.email) = v_q) desc,
            (public._grant_norm(p.full_name) = v_q) desc,
            (public._grant_norm(p.full_name) like v_q || '%') desc,
            p.full_name nulls last
   limit least(greatest(p_limit, 1), 50);
end $$;

create or replace function public.grant_course_access(p_user uuid, p_course uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_kind text := public._grant_caller_kind(p_course, p_user);
  c public.courses;
  e public.enrollments;
begin
  select * into c from public.courses where id = p_course;
  if not found then raise exception 'COURSE_NOT_FOUND'; end if;
  if c.is_free or coalesce(c.base_price, 0) <= 0 then raise exception 'COURSE_IS_FREE'; end if;
  if not exists (select 1 from public.profiles where id = p_user) then raise exception 'USER_NOT_FOUND'; end if;

  select * into e from public.enrollments where user_id = p_user and course_id = p_course for update;
  if found then
    -- اشترى فعلاً (دفعة واحدة أو اشتراك شهري ساري): مفيش داعي للمنحة
    if e.status = 'ACTIVE' and (e.expires_at is null or e.expires_at > now()) and e.access_type <> 'GRANTED' then
      return jsonb_build_object('ok', false, 'reason', 'ALREADY_HAS_ACCESS');
    end if;
    update public.enrollments
       set status = 'ACTIVE', access_type = 'GRANTED', expires_at = null
     where id = e.id;
  else
    insert into public.enrollments (user_id, course_id, status, access_type)
    values (p_user, p_course, 'ACTIVE', 'GRANTED');
  end if;

  update public.course_grants set granted_by = auth.uid(), granted_at = now()
   where user_id = p_user and course_id = p_course and revoked_at is null;
  if not found then
    insert into public.course_grants (user_id, course_id, granted_by) values (p_user, p_course, auth.uid());
  end if;

  perform public.write_audit('course.grant', 'course', p_course::text,
    jsonb_build_object('user', p_user, 'by', v_kind));
  perform public.push_notification(
    p_user, 'COURSE_ACCESS_GRANTED',
    'تم فتح كورس لك • A course was opened for you',
    'تم فتح «' || c.title || '» لك. — "' || c.title || '" is now open for you.',
    jsonb_build_object('course_id', p_course));
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.revoke_course_grant(p_user uuid, p_course uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_kind text := public._grant_caller_kind(p_course, case when public.has_permission('courses.grant') then null else p_user end);
  g public.course_grants;
begin
  select * into g from public.course_grants
   where user_id = p_user and course_id = p_course and revoked_at is null for update;
  if not found then return jsonb_build_object('ok', false, 'reason', 'NOT_GRANTED'); end if;
  -- المعلم يسحب اللي هو فتحه بس
  if v_kind = 'TEACHER' and g.granted_by is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;

  update public.course_grants set revoked_at = now(), revoked_by = auth.uid() where id = g.id;
  -- بيقفل بس لو لسه منحة؛ لو اشترى بعدها (access_type اتغيّر) مبنلمسوش
  update public.enrollments set status = 'PAYMENT_REQUIRED'
   where user_id = p_user and course_id = p_course and access_type = 'GRANTED' and status = 'ACTIVE';

  perform public.write_audit('course.grant.revoke', 'course', p_course::text,
    jsonb_build_object('user', p_user, 'by', v_kind));
  return jsonb_build_object('ok', true);
end $$;

-- اللي اتفتحلهم الكورس ده يدوياً ولسه مفتوح
create or replace function public.list_course_grants(p_course uuid)
returns table (user_id uuid, full_name text, email text, avatar_url text, granted_at timestamptz, granted_by_name text)
language plpgsql stable security definer set search_path = public
as $$
declare v_kind text := public._grant_caller_kind(p_course, null);
begin
  return query
  select p.id, p.full_name, p.email, p.avatar_url, g.granted_at, gb.full_name
    from public.course_grants g
    join public.profiles p on p.id = g.user_id
    join public.enrollments e on e.user_id = g.user_id and e.course_id = g.course_id
    left join public.profiles gb on gb.id = g.granted_by
   where g.course_id = p_course and g.revoked_at is null
     and e.access_type = 'GRANTED' and e.status = 'ACTIVE'
     and (v_kind = 'STAFF' or g.granted_by = auth.uid())
   order by g.granted_at desc;
end $$;

-- المالك: يفعّل الصلاحية للمعلم ويحدد الكورسات (p_course_ids = null يعني سيب القائمة زي ما هي)
create or replace function public.set_teacher_grant_permission(p_teacher uuid, p_enabled boolean, p_course_ids uuid[] default null)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  if not public.has_permission('teachers.manage') then raise exception 'FORBIDDEN'; end if;
  update public.teacher_profiles set can_grant_courses = coalesce(p_enabled, can_grant_courses) where id = p_teacher;
  if not found then raise exception 'TEACHER_NOT_FOUND'; end if;
  if p_course_ids is not null then
    delete from public.teacher_grant_courses where teacher_id = p_teacher;
    insert into public.teacher_grant_courses (teacher_id, course_id)
    select p_teacher, c.id from public.courses c where c.id = any (p_course_ids);
  end if;
  perform public.write_audit('teacher.grant_permission', 'teacher', p_teacher::text,
    jsonb_build_object('enabled', p_enabled, 'courses', p_course_ids));
end $$;

create or replace function public.get_teacher_grant_permission(p_teacher uuid)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
begin
  if not public.has_permission('teachers.manage') then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'enabled', coalesce((select can_grant_courses from public.teacher_profiles where id = p_teacher), false),
    'course_ids', coalesce((select jsonb_agg(course_id) from public.teacher_grant_courses where teacher_id = p_teacher), '[]'::jsonb));
end $$;

-- المعلم: هل معاه الصلاحية، وأنهي كورسات
create or replace function public.my_grant_scope()
returns jsonb
language sql stable security definer set search_path = public
as $$
  select jsonb_build_object(
    'enabled', coalesce((select tp.can_grant_courses and tp.status::text <> 'SUSPENDED'
                           from public.teacher_profiles tp where tp.id = auth.uid()), false),
    'courses', coalesce((
      select jsonb_agg(jsonb_build_object('id', c.id, 'title', c.title, 'title_ar', c.title_ar, 'title_en', c.title_en) order by c.title)
        from public.teacher_grant_courses g join public.courses c on c.id = g.course_id
       where g.teacher_id = auth.uid()), '[]'::jsonb));
$$;

revoke all on function public._grant_norm(text) from public, anon;
revoke all on function public._grant_caller_kind(uuid, uuid) from public, anon, authenticated;
revoke all on function public.grant_course_search_users(uuid, text, int) from public, anon;
revoke all on function public.grant_course_access(uuid, uuid) from public, anon;
revoke all on function public.revoke_course_grant(uuid, uuid) from public, anon;
revoke all on function public.list_course_grants(uuid) from public, anon;
revoke all on function public.set_teacher_grant_permission(uuid, boolean, uuid[]) from public, anon;
revoke all on function public.get_teacher_grant_permission(uuid) from public, anon;
revoke all on function public.my_grant_scope() from public, anon;
grant execute on function public._grant_norm(text) to authenticated;
grant execute on function public.grant_course_search_users(uuid, text, int) to authenticated;
grant execute on function public.grant_course_access(uuid, uuid) to authenticated;
grant execute on function public.revoke_course_grant(uuid, uuid) to authenticated;
grant execute on function public.list_course_grants(uuid) to authenticated;
grant execute on function public.set_teacher_grant_permission(uuid, boolean, uuid[]) to authenticated;
grant execute on function public.get_teacher_grant_permission(uuid) to authenticated;
grant execute on function public.my_grant_scope() to authenticated;
