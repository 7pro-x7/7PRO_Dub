-- 7PRO — المالك والأدمن يدخلوا ويمارسوا أي تمرين (تمارينهم وتمارين كل المعلمين).
-- قبل كده كان السماح معتمد على صلاحية tests.manage بس، فأدمن من غير الصلاحية دي كان بيتحجب،
-- وكمان قفل المعلم (is_locked) ومنع الكورس كانوا بيمنعوا الستاف. دلوقتي OWNER/ADMIN النشطين
-- مسموح لهم دايمًا. باقي الناس زي ما هم (طالب المعلم، صاحب التمرين، مسجّل الكورس، تمارين المالك).

create or replace function public.is_staff_account()
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select auth.uid() is not null and (
    exists (
      select 1 from public.profiles p
      where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN') and p.status = 'ACTIVE'
    )
    or coalesce(public.has_permission('tests.manage'), false)
  );
$$;
grant execute on function public.is_staff_account() to anon, authenticated;

create or replace function public.can_take_exercise(p_owner uuid, p_course uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select
    (auth.uid() is not null and p_owner = auth.uid())
    or public.is_staff_account()
    or case
         when p_course is not null then public.has_active_enrollment(auth.uid(), p_course)
         when public.is_platform_owner(p_owner) then true
         else public.is_student_of_teacher(p_owner)
       end;
$$;
grant execute on function public.can_take_exercise(uuid, uuid) to anon, authenticated;

create or replace function public.start_test_attempt(p_test_id uuid)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  t public.placement_tests; a public.test_attempts;
  v_attempts int; v_available int; v_total int; v_left int;
begin
  if not public.is_active_account() then raise exception 'ACCOUNT_NOT_ACTIVE'; end if;
  select * into t from public.placement_tests where id = p_test_id;
  if not found or t.status <> 'PUBLISHED' then raise exception 'TEST_NOT_AVAILABLE'; end if;

  -- تمرين معلم: المشاركة لطلاب المعلم فقط (أو تمرين المالك، أو مسجّل في الكورس المربوط).
  if t.kind = 'EXERCISE' and not public.can_take_exercise(t.owner_id, t.course_id) then
    raise exception 'EXERCISE_NOT_STUDENT';
  end if;

  if t.course_id is not null
     and t.owner_id is distinct from auth.uid()
     and not public.is_staff_account()
     and not public.has_active_enrollment(auth.uid(), t.course_id) then
    raise exception 'COURSE_NOT_ENROLLED';
  end if;

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
end
$function$;
