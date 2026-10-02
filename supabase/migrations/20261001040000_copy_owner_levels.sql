-- 7PRO — نسخ تدريبات المالك بالليفل:
--   * القايمة بقت بترجّع الليفل بتاع كل تدريب (section_id / section_title / section_sort_order)
--     عشان المعلم يتصفح الليفلات ويفتح أي ليفل.
--   * copy_owner_exercise: بنفس سلوكها لكن بتقبل ليفل اختياري، ولو التمرين مربوط بكورس عند المالك
--     النسخة ماتتربطش بكورس المالك (كورس المالك مش بتاع المعلم).
--   * copy_owner_level: ينسخ كل تمارين الليفل المنشورة دفعة واحدة كمسودات في ليفل بنفس الاسم عند المعلم
--     (يستخدم ليفله اللي بنفس الاسم لو موجود، وإلا بيعمل واحد)، ويتخطى اللي اتنسخ قبل كده.
-- كل ده داخل السيرفر: مفيش تمرين غير تمارين المالك ينفع يتنسخ.

drop function if exists public.owner_exercises_available_to_copy();
create or replace function public.owner_exercises_available_to_copy()
returns table(
  id uuid, title text, title_ar text, title_en text,
  description text, description_ar text, description_en text,
  question_count integer, time_limit_seconds integer, questions_total bigint,
  sort_order integer, already_copied boolean,
  section_id uuid, section_title text, section_sort_order integer
)
language plpgsql stable security definer set search_path = public, pg_temp as $$
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.is_approved_teacher(auth.uid()) then raise exception 'FORBIDDEN'; end if;

  return query
  select e.id, e.title, e.title_ar, e.title_en,
         e.description, e.description_ar, e.description_en,
         e.question_count, e.time_limit_seconds,
         (select count(*) from public.test_questions q where q.test_id = e.id),
         e.sort_order,
         exists (select 1 from public.placement_tests c
                  where c.copied_from = e.id and c.owner_id = auth.uid() and c.kind = 'EXERCISE'),
         e.section_id, s.title, s.sort_order
    from public.placement_tests e
    join public.profiles op on op.id = e.owner_id and op.role = 'OWNER'
    left join public.exercise_sections s on s.id = e.section_id
   where e.kind = 'EXERCISE' and e.status = 'PUBLISHED'
   order by s.sort_order asc nulls last, e.sort_order asc, e.created_at asc;
end;
$$;
revoke all on function public.owner_exercises_available_to_copy() from public, anon;
grant execute on function public.owner_exercises_available_to_copy() to authenticated;

drop function if exists public.copy_owner_exercise(uuid);
create or replace function public.copy_owner_exercise(p_exercise_id uuid, p_section_id uuid default null)
returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  me uuid := auth.uid();
  src public.placement_tests;
  new_id uuid := gen_random_uuid();
  next_order integer;
begin
  if me is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.is_approved_teacher(me) then raise exception 'FORBIDDEN'; end if;

  select * into src from public.placement_tests where id = p_exercise_id and kind = 'EXERCISE';
  if not found then raise exception 'EXERCISE_NOT_FOUND'; end if;

  if not exists (select 1 from public.profiles p where p.id = src.owner_id and p.role = 'OWNER') then
    raise exception 'ONLY_OWNER_EXERCISES_CAN_BE_COPIED';
  end if;
  if src.status <> 'PUBLISHED' then raise exception 'EXERCISE_NOT_PUBLISHED'; end if;

  select count(*) into next_order from public.placement_tests where kind = 'EXERCISE' and owner_id = me;

  insert into public.placement_tests
  select (r).*
  from (
    select jsonb_populate_record(
             null::public.placement_tests,
             to_jsonb(src) || jsonb_build_object(
               'id', new_id, 'owner_id', me, 'status', 'DRAFT',
               'section_id', p_section_id, 'course_id', null, 'is_locked', false,
               'sort_order', next_order, 'copied_from', src.id,
               'created_at', now(), 'updated_at', now())
           ) as r
  ) s;

  insert into public.test_questions
  select (x.r).*
  from public.test_questions q
  cross join lateral (
    select jsonb_populate_record(
             null::public.test_questions,
             to_jsonb(q) || jsonb_build_object('id', gen_random_uuid(), 'test_id', new_id, 'created_at', now())
           ) as r
  ) x
  where q.test_id = src.id
  order by q.sort_order;

  return new_id;
end;
$$;
revoke all on function public.copy_owner_exercise(uuid, uuid) from public, anon;
grant execute on function public.copy_owner_exercise(uuid, uuid) to authenticated;

create or replace function public.copy_owner_level(p_section_id uuid)
returns integer
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  me uuid := auth.uid();
  sec public.exercise_sections;
  my_sec uuid;
  r record;
  n integer := 0;
begin
  if me is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.is_approved_teacher(me) then raise exception 'FORBIDDEN'; end if;

  select * into sec from public.exercise_sections where id = p_section_id;
  if not found then raise exception 'LEVEL_NOT_FOUND'; end if;
  if not exists (select 1 from public.profiles p where p.id = sec.owner_id and p.role = 'OWNER') then
    raise exception 'ONLY_OWNER_EXERCISES_CAN_BE_COPIED';
  end if;

  -- مفيش حاجة جديدة تتنسخ: ماننشئش ليفل فاضي
  if not exists (
    select 1 from public.placement_tests e
     where e.section_id = sec.id and e.kind = 'EXERCISE' and e.status = 'PUBLISHED'
       and not exists (select 1 from public.placement_tests c
                        where c.copied_from = e.id and c.owner_id = me and c.kind = 'EXERCISE')
  ) then
    return 0;
  end if;

  select s.id into my_sec from public.exercise_sections s
   where s.owner_id = me and s.title = sec.title order by s.sort_order limit 1;
  if my_sec is null then
    insert into public.exercise_sections (owner_id, title, sort_order)
    values (me, sec.title, (select count(*) from public.exercise_sections s2 where s2.owner_id = me))
    returning id into my_sec;
  end if;

  for r in
    select e.id from public.placement_tests e
     where e.section_id = sec.id and e.kind = 'EXERCISE' and e.status = 'PUBLISHED'
       and not exists (select 1 from public.placement_tests c
                        where c.copied_from = e.id and c.owner_id = me and c.kind = 'EXERCISE')
     order by e.sort_order, e.created_at
  loop
    perform public.copy_owner_exercise(r.id, my_sec);
    n := n + 1;
  end loop;
  return n;
end;
$$;
revoke all on function public.copy_owner_level(uuid) from public, anon;
grant execute on function public.copy_owner_level(uuid) to authenticated;
