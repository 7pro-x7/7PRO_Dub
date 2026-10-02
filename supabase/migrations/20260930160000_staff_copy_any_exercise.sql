-- ============================================================================
-- المالك (أو أدمن معاه tests.manage) ينسخ أي تمرين من أي معلم لتمارينه هو، ويستخدمه:
-- يعدّل عليه، ينشره، يربطه بكورس، أو يعرضه على المعلمين (تمارين المالك المنشورة بتظهر للمعلمين ينسخوها).
-- النسخة بتتعمل DRAFT من غير قسم ولا كورس ولا قفل، مع كل أسئلتها، و copied_from بيشاور على الأصل.
-- ============================================================================
create or replace function public.staff_copy_exercise(p_exercise_id uuid)
returns uuid
language plpgsql security definer set search_path = public, pg_temp
as $$
declare
  me uuid := auth.uid();
  src public.placement_tests;
  new_id uuid := gen_random_uuid();
  next_order integer;
begin
  if me is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.has_permission('tests.manage') then raise exception 'FORBIDDEN'; end if;

  select * into src from public.placement_tests where id = p_exercise_id and kind = 'EXERCISE';
  if not found then raise exception 'EXERCISE_NOT_FOUND'; end if;
  if src.owner_id = me then raise exception 'ALREADY_YOURS'; end if;

  select count(*) into next_order from public.placement_tests where kind = 'EXERCISE' and owner_id = me;

  insert into public.placement_tests
  select (r).*
  from (
    select jsonb_populate_record(
             null::public.placement_tests,
             to_jsonb(src) || jsonb_build_object(
               'id', new_id, 'owner_id', me, 'status', 'DRAFT',
               'section_id', null, 'course_id', null, 'is_locked', false,
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

  perform public.write_audit('exercise.copy', 'exercise', new_id::text,
    jsonb_build_object('from', src.id, 'from_owner', src.owner_id));
  return new_id;
end $$;
revoke all on function public.staff_copy_exercise(uuid) from public, anon;
grant execute on function public.staff_copy_exercise(uuid) to authenticated;
