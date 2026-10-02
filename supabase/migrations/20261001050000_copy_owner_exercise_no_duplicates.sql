-- 7PRO — تمرين المالك اللي اتنسخ قبل كده ما يتنسخش تاني.
-- copy_owner_level كان بيتخطاه أصلاً؛ دلوقتي نسخ تمرين واحد كمان بيرفض (ALREADY_COPIED).
-- لو المعلم مسح نسخته، يقدر ينسخه تاني (الفحص على نسخة موجودة فعلاً).
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

  -- ضغطتين ورا بعض ما يعملوش نسختين
  perform pg_advisory_xact_lock(hashtextextended(me::text || ':' || p_exercise_id::text, 0));
  if exists (select 1 from public.placement_tests c
              where c.copied_from = src.id and c.owner_id = me and c.kind = 'EXERCISE') then
    raise exception 'ALREADY_COPIED';
  end if;

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
