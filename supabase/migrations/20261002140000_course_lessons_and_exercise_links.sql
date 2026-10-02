-- ============================================================================
-- الدروس والتمارين داخل الدورة
--   1) الدرس المقروء: كتل (عنوان/فقرة/صورة/قائمة) في lessons.blocks، مع السماح
--      بتحميل PDF و TXT لكل درس (allow_pdf / allow_txt).
--   2) ربط التمارين بالدورات (أكثر من دورة لنفس التمرين) في جدول
--      course_exercise_links بدل عمود placement_tests.course_id الواحد.
--      العمود القديم بيفضل متزامن تلقائيًا (أول دورة مربوطة) عشان أي كود قديم
--      وصفحات الويب يفضلوا شغالين زي ما هم.
--   3) الصلاحيات:
--      * المالك، والأدمن اللي معاه course_content.manage: يربط أي تمرين بأي دورة،
--        ويضيف ويعدّل الدروس في أي دورة.
--      * المعلم: يربط تمارينه هو بدوراته هو فقط، وبشرط إن المالك مفعّل له
--        can_manage_course_exercises (المفتاح الموجود من قبل).
--   4) الطالب يقدر يفتح التمرين المربوط لو مشترك في أي دورة من الدورات المربوطة.
-- ============================================================================

-- ---------------------------------------------------------------- 1. lessons
alter table public.lessons
  add column if not exists blocks jsonb,
  add column if not exists allow_pdf boolean not null default true,
  add column if not exists allow_txt boolean not null default true;

do $$ begin
  alter table public.lessons
    add constraint lessons_blocks_is_array check (blocks is null or jsonb_typeof(blocks) = 'array');
exception when duplicate_object then null; end $$;

-- ------------------------------------------------- 2. content permission reads
drop policy if exists courses_read on public.courses;
create policy courses_read on public.courses for select
  using (status = 'PUBLISHED'::content_status or teacher_id = auth.uid()
         or public.has_permission('courses.manage') or public.has_permission('course_content.manage'));

drop policy if exists sections_read on public.course_sections;
create policy sections_read on public.course_sections for select
  using (exists (select 1 from public.courses c where c.id = course_sections.course_id
                 and (c.teacher_id = auth.uid() or public.has_permission('courses.manage')
                      or public.has_permission('course_content.manage') or c.status = 'PUBLISHED'::content_status)));

drop policy if exists lessons_read on public.lessons;
create policy lessons_read on public.lessons for select
  using (exists (select 1 from public.courses c where c.id = lessons.course_id
                 and (c.teacher_id = auth.uid() or public.has_permission('courses.manage')
                      or public.has_permission('course_content.manage') or c.status = 'PUBLISHED'::content_status)));

drop policy if exists lessons_content_staff_write on public.lessons;
create policy lessons_content_staff_write on public.lessons for all
  using (public.has_permission('course_content.manage'))
  with check (public.has_permission('course_content.manage'));

drop policy if exists sections_content_staff_write on public.course_sections;
create policy sections_content_staff_write on public.course_sections for all
  using (public.has_permission('course_content.manage'))
  with check (public.has_permission('course_content.manage'));

-- ------------------------------------------------------ 3. course_exercise_links
create table if not exists public.course_exercise_links (
  id          uuid primary key default gen_random_uuid(),
  course_id   uuid not null references public.courses(id) on delete cascade,
  exercise_id uuid not null references public.placement_tests(id) on delete cascade,
  section_id  uuid references public.course_sections(id) on delete set null,
  sort_order  integer not null default 0,
  created_by  uuid references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now(),
  unique (course_id, exercise_id)
);
create index if not exists course_exercise_links_exercise_idx on public.course_exercise_links(exercise_id);
create index if not exists course_exercise_links_course_idx on public.course_exercise_links(course_id, sort_order);

alter table public.course_exercise_links enable row level security;
grant select on public.course_exercise_links to authenticated;

-- Reads only; every write goes through the permission-checked functions below.
drop policy if exists cel_read on public.course_exercise_links;
create policy cel_read on public.course_exercise_links for select
  using (
    public.has_permission('course_content.manage')
    or public.has_permission('courses.manage')
    or public.has_permission('tests.manage')
    or exists (select 1 from public.courses c where c.id = course_exercise_links.course_id
               and (c.teacher_id = auth.uid() or c.status = 'PUBLISHED'::content_status))
    or exists (select 1 from public.placement_tests t where t.id = course_exercise_links.exercise_id
               and t.owner_id = auth.uid())
  );

-- A link's unit must be one of the same course's units.
create or replace function public.check_course_exercise_link_section()
returns trigger language plpgsql set search_path = public as $$
begin
  if NEW.section_id is not null and not exists (
    select 1 from public.course_sections s where s.id = NEW.section_id and s.course_id = NEW.course_id
  ) then
    raise exception 'SECTION_NOT_IN_COURSE';
  end if;
  return NEW;
end $$;

drop trigger if exists trg_check_course_exercise_link_section on public.course_exercise_links;
create trigger trg_check_course_exercise_link_section
  before insert or update of section_id, course_id on public.course_exercise_links
  for each row execute function public.check_course_exercise_link_section();

-- Keep the legacy placement_tests.course_id pointing at the first linked course (or null),
-- so older app builds and the web pages keep treating linked exercises as course-only.
create or replace function public.check_exercise_course_permission()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare can_use boolean;
begin
  if coalesce(current_setting('app.exercise_link_sync', true), 'off') = 'on' then
    return NEW;
  end if;
  if TG_OP = 'UPDATE' and NEW.course_id is not distinct from OLD.course_id then
    return NEW;
  end if;
  if NEW.course_id is null then
    return NEW;
  end if;
  if public.has_permission('tests.manage') or public.has_permission('course_content.manage') then
    return NEW;
  end if;
  select coalesce(can_manage_course_exercises, false) into can_use
  from public.teacher_profiles where id = NEW.owner_id;
  if not coalesce(can_use, false) then
    raise exception 'COURSE_EXERCISES_NOT_ALLOWED';
  end if;
  return NEW;
end $$;

create or replace function public.sync_exercise_primary_course()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_ex uuid; v_course uuid;
begin
  if TG_OP = 'DELETE' then v_ex := OLD.exercise_id; else v_ex := NEW.exercise_id; end if;
  select l.course_id into v_course from public.course_exercise_links l
   where l.exercise_id = v_ex order by l.created_at, l.id limit 1;
  perform set_config('app.exercise_link_sync', 'on', true);
  update public.placement_tests set course_id = v_course
   where id = v_ex and course_id is distinct from v_course;
  perform set_config('app.exercise_link_sync', 'off', true);
  return null;
end $$;

drop trigger if exists trg_sync_exercise_primary_course on public.course_exercise_links;
create trigger trg_sync_exercise_primary_course
  after insert or delete or update of course_id, exercise_id on public.course_exercise_links
  for each row execute function public.sync_exercise_primary_course();

-- Bring over any exercise already filed under a course the old way.
insert into public.course_exercise_links (course_id, exercise_id, sort_order, created_at)
select t.course_id, t.id, t.sort_order, coalesce(t.updated_at, now())
from public.placement_tests t
where t.course_id is not null and t.kind = 'EXERCISE'
on conflict (course_id, exercise_id) do nothing;

-- ------------------------------------------------------------ 4. permissions
create or replace function public.has_exercise_course_access(p_test uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.course_exercise_links l
    where l.exercise_id = p_test and public.has_active_enrollment(auth.uid(), l.course_id)
  );
$$;

-- Whether the caller may link (and unlink) this exercise in this course.
create or replace function public.can_link_exercise_to_course(p_exercise uuid, p_course uuid)
returns boolean language plpgsql stable security definer set search_path = public as $$
declare v_owner uuid; v_teacher uuid;
begin
  if auth.uid() is null then return false; end if;
  select owner_id into v_owner from public.placement_tests where id = p_exercise and kind = 'EXERCISE';
  if not found then return false; end if;
  select teacher_id into v_teacher from public.courses where id = p_course;
  if not found then return false; end if;
  if public.has_permission('course_content.manage') then return true; end if;
  return v_owner = auth.uid()
     and v_teacher = auth.uid()
     and public.is_approved_teacher(auth.uid())
     and coalesce((select can_manage_course_exercises from public.teacher_profiles where id = auth.uid()), false);
end $$;

-- Whether the caller may change this course's own content (order of its exercises).
create or replace function public.can_edit_course_content(p_course uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.has_permission('course_content.manage')
      or public.has_permission('courses.manage')
      or exists (select 1 from public.courses c where c.id = p_course and c.teacher_id = auth.uid());
$$;

drop policy if exists tests_read on public.placement_tests;
create policy tests_read on public.placement_tests for select
  using (owner_id = auth.uid() or public.has_permission('tests.manage')
         or (status = 'PUBLISHED'::test_status
             and (course_id is null or public.has_exercise_course_access(id))));

-- --------------------------------------------------------------- 5. functions
create or replace function public.course_exercise_list(p_course_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  c public.courses;
  v_manage boolean;
  v_can_link boolean;
  v_can_link_any boolean := public.has_permission('course_content.manage');
begin
  select * into c from public.courses where id = p_course_id;
  if not found then raise exception 'COURSE_NOT_FOUND'; end if;
  v_manage := public.can_edit_course_content(p_course_id);
  if not v_manage and c.status <> 'PUBLISHED'::content_status then raise exception 'COURSE_NOT_FOUND'; end if;

  v_can_link := v_can_link_any or (
    c.teacher_id = auth.uid() and public.is_approved_teacher(auth.uid())
    and coalesce((select can_manage_course_exercises from public.teacher_profiles where id = auth.uid()), false)
  );

  return jsonb_build_object(
    'can_manage', v_manage,
    'can_link', v_can_link,
    'can_link_any', v_can_link_any,
    'items', coalesce((
      select jsonb_agg(jsonb_build_object(
        'exercise_id', t.id,
        'section_id', l.section_id,
        'sort_order', l.sort_order,
        'title', t.title, 'title_ar', t.title_ar, 'title_en', t.title_en,
        'question_count', t.question_count,
        'time_limit_seconds', t.time_limit_seconds,
        'status', t.status,
        'is_locked', coalesce(t.is_locked, false),
        'owner_id', t.owner_id,
        'owner_name', coalesce(p.full_name, ''),
        'course_count', (select count(*) from public.course_exercise_links x where x.exercise_id = t.id),
        'can_unlink', public.can_link_exercise_to_course(t.id, p_course_id)
      ) order by l.sort_order, l.created_at)
      from public.course_exercise_links l
      join public.placement_tests t on t.id = l.exercise_id
      left join public.profiles p on p.id = t.owner_id
      where l.course_id = p_course_id
        and (v_manage or t.status = 'PUBLISHED')
    ), '[]'::jsonb)
  );
end $$;

create or replace function public.linkable_exercises(p_course_id uuid, p_scope text default 'MINE', p_search text default null)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_any boolean := public.has_permission('course_content.manage');
  v_all boolean;
  v_q text := nullif(btrim(coalesce(p_search, '')), '');
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not exists (select 1 from public.courses where id = p_course_id) then raise exception 'COURSE_NOT_FOUND'; end if;
  v_all := v_any and upper(coalesce(p_scope, 'MINE')) = 'ALL';

  return coalesce((
    select jsonb_agg(row_to_json(r)::jsonb order by r.mine desc, r.sort_order, r.created_at desc)
    from (
      select t.id, t.title, t.title_ar, t.title_en, t.question_count, t.status,
             t.owner_id, coalesce(p.full_name, '') as owner_name, t.sort_order, t.created_at,
             (t.owner_id = auth.uid()) as mine,
             (select count(*) from public.course_exercise_links x where x.exercise_id = t.id) as course_count,
             exists (select 1 from public.course_exercise_links x
                     where x.exercise_id = t.id and x.course_id = p_course_id) as already_linked
      from public.placement_tests t
      left join public.profiles p on p.id = t.owner_id
      where t.kind = 'EXERCISE'
        and (v_all or t.owner_id = auth.uid())
        and (v_q is null
             or t.title ilike '%' || v_q || '%'
             or coalesce(t.title_ar, '') ilike '%' || v_q || '%'
             or coalesce(t.title_en, '') ilike '%' || v_q || '%'
             or coalesce(p.full_name, '') ilike '%' || v_q || '%')
      order by (t.owner_id = auth.uid()) desc, t.sort_order, t.created_at desc
      limit 300
    ) r
  ), '[]'::jsonb);
end $$;

create or replace function public.link_exercises_to_course(p_course_id uuid, p_exercise_ids uuid[], p_section_id uuid default null)
returns integer language plpgsql security definer set search_path = public as $$
declare v_ex uuid; v_next integer; v_added integer := 0; v_n integer;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not exists (select 1 from public.courses where id = p_course_id) then raise exception 'COURSE_NOT_FOUND'; end if;
  if p_section_id is not null and not exists (
    select 1 from public.course_sections where id = p_section_id and course_id = p_course_id
  ) then raise exception 'SECTION_NOT_IN_COURSE'; end if;

  foreach v_ex in array coalesce(p_exercise_ids, '{}') loop
    if not public.can_link_exercise_to_course(v_ex, p_course_id) then
      raise exception 'COURSE_EXERCISES_NOT_ALLOWED';
    end if;
  end loop;

  select coalesce(max(sort_order), -1) + 1 into v_next from public.course_exercise_links where course_id = p_course_id;
  foreach v_ex in array coalesce(p_exercise_ids, '{}') loop
    insert into public.course_exercise_links (course_id, exercise_id, section_id, sort_order, created_by)
    values (p_course_id, v_ex, p_section_id, v_next, auth.uid())
    on conflict (course_id, exercise_id) do nothing;
    get diagnostics v_n = row_count;
    if v_n > 0 then v_added := v_added + 1; v_next := v_next + 1; end if;
  end loop;

  if v_added > 0 then
    perform public.write_audit('course.exercises_link', 'course', p_course_id::text,
      jsonb_build_object('exercises', p_exercise_ids, 'section_id', p_section_id));
  end if;
  return v_added;
end $$;

create or replace function public.unlink_exercise_from_course(p_course_id uuid, p_exercise_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.can_link_exercise_to_course(p_exercise_id, p_course_id) then
    raise exception 'COURSE_EXERCISES_NOT_ALLOWED';
  end if;
  delete from public.course_exercise_links where course_id = p_course_id and exercise_id = p_exercise_id;
  perform public.write_audit('course.exercise_unlink', 'course', p_course_id::text,
    jsonb_build_object('exercise', p_exercise_id));
end $$;

-- New order for a course's exercises (ids in display order); ids not in the course are ignored.
create or replace function public.reorder_course_exercises(p_course_id uuid, p_exercise_ids uuid[])
returns void language plpgsql security definer set search_path = public as $$
declare i integer;
begin
  if not public.can_edit_course_content(p_course_id) then raise exception 'FORBIDDEN'; end if;
  for i in 1 .. coalesce(array_length(p_exercise_ids, 1), 0) loop
    update public.course_exercise_links set sort_order = i - 1
     where course_id = p_course_id and exercise_id = p_exercise_ids[i];
  end loop;
end $$;

-- Legacy single-course filing (older app builds): a course adds a link, null clears this
-- exercise's links the caller is allowed to remove.
create or replace function public.set_exercise_course(p_exercise_id uuid, p_course_id uuid)
returns public.placement_tests language plpgsql security definer set search_path = public as $$
declare t public.placement_tests;
begin
  select * into t from public.placement_tests where id = p_exercise_id and kind = 'EXERCISE';
  if not found then raise exception 'EXERCISE_NOT_FOUND'; end if;
  if p_course_id is null then
    delete from public.course_exercise_links l
     where l.exercise_id = p_exercise_id and public.can_link_exercise_to_course(p_exercise_id, l.course_id);
  else
    perform public.link_exercises_to_course(p_course_id, array[p_exercise_id], null);
  end if;
  select * into t from public.placement_tests where id = p_exercise_id;
  return t;
end $$;

-- Starting an exercise: a course-only exercise opens for students of ANY linked course.
create or replace function public.start_test_attempt(p_test_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  t public.placement_tests; a public.test_attempts;
  v_attempts int; v_available int; v_total int; v_left int;
  v_course_ok boolean;
begin
  if not public.is_active_account() then raise exception 'ACCOUNT_NOT_ACTIVE'; end if;
  select * into t from public.placement_tests where id = p_test_id;
  if not found or t.status <> 'PUBLISHED' then raise exception 'TEST_NOT_AVAILABLE'; end if;

  v_course_ok := t.course_id is null
    or t.owner_id = auth.uid()
    or public.is_staff_account()
    or public.has_exercise_course_access(t.id);

  if t.kind = 'EXERCISE' then
    if t.course_id is null then
      if not public.can_take_exercise(t.owner_id, null) then raise exception 'EXERCISE_NOT_STUDENT'; end if;
    elsif not v_course_ok then
      raise exception 'EXERCISE_NOT_STUDENT';
    end if;
  end if;

  if not v_course_ok then raise exception 'COURSE_NOT_ENROLLED'; end if;

  select count(*) into v_available from public.test_questions where test_id = t.id and is_active;
  if v_available = 0 then raise exception 'TEST_HAS_NO_QUESTIONS'; end if;
  v_total := least(greatest(t.question_count, 1), v_available);

  update public.test_attempts
     set status = 'EXPIRED', submitted_at = now()
   where user_id = auth.uid() and test_id = p_test_id and status = 'IN_PROGRESS'
     and extract(epoch from (now() - started_at)) > t.time_limit_seconds;

  select * into a from public.test_attempts
    where test_id = p_test_id and user_id = auth.uid() and status = 'IN_PROGRESS'
    order by started_at desc limit 1;

  if found then
    v_left := greatest(0, t.time_limit_seconds - floor(extract(epoch from (now() - a.started_at)))::int);
    return jsonb_build_object('attempt_id', a.id, 'resumed', true, 'question_count', v_total,
      'time_limit_seconds', t.time_limit_seconds, 'seconds_left', v_left,
      'answered', a.answered_count, 'title', t.title);
  end if;

  if coalesce(t.is_locked, false)
     and t.owner_id is distinct from auth.uid()
     and not public.is_staff_account() then
    raise exception 'TEST_LOCKED';
  end if;

  if t.attempt_limit > 0 then
    select count(*) into v_attempts from public.test_attempts
      where test_id = p_test_id and user_id = auth.uid() and status = 'SUBMITTED';
    if v_attempts >= t.attempt_limit then raise exception 'ATTEMPT_LIMIT_REACHED'; end if;
  end if;

  insert into public.test_attempts(test_id, test_version, user_id, current_difficulty)
  values (t.id, t.version, auth.uid(), 3) returning * into a;

  return jsonb_build_object('attempt_id', a.id, 'resumed', false, 'question_count', v_total,
    'time_limit_seconds', t.time_limit_seconds, 'seconds_left', t.time_limit_seconds,
    'answered', 0, 'title', t.title);
end $$;

revoke all on function public.course_exercise_list(uuid) from public, anon;
revoke all on function public.linkable_exercises(uuid, text, text) from public, anon;
revoke all on function public.link_exercises_to_course(uuid, uuid[], uuid) from public, anon;
revoke all on function public.unlink_exercise_from_course(uuid, uuid) from public, anon;
revoke all on function public.reorder_course_exercises(uuid, uuid[]) from public, anon;
grant execute on function public.course_exercise_list(uuid) to authenticated;
grant execute on function public.linkable_exercises(uuid, text, text) to authenticated;
grant execute on function public.link_exercises_to_course(uuid, uuid[], uuid) to authenticated;
grant execute on function public.unlink_exercise_from_course(uuid, uuid) to authenticated;
grant execute on function public.reorder_course_exercises(uuid, uuid[]) to authenticated;

notify pgrst, 'reload schema';
