-- 7PRO — تمارين المعلمين: العرض للكل، والمشاركة لطلاب المعلم فقط.
--   * أي مستخدم (حتى غير المشترك) يشوف المعلمين وقايمة تمارينهم المنشورة (العنوان/الوصف/عدد الأسئلة/المدة).
--   * البدء في التمرين (start_test_attempt) يفضل مقصور على: صاحب التمرين، الستاف، طلاب المعلم المعتمدين،
--     مسجّلي الكورس (لو التمرين مربوط بكورس)، أو تمارين المالك.
-- قبل كده can_view_exercise كانت بتتحكم في الرؤية والبدء مع بعض، فغير المشترك ماكانش يشوف حاجة.

-- 1) قاعدة "يقدر يشارك" = نفس منطق can_view_exercise القديم (بنفصلها في دالة باسم واضح).
create or replace function public.can_take_exercise(p_owner uuid, p_course uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select
    (auth.uid() is not null and p_owner = auth.uid())
    or coalesce(public.has_permission('tests.manage'), false)
    or case
         when p_course is not null then public.has_active_enrollment(auth.uid(), p_course)
         when public.is_platform_owner(p_owner) then true
         else public.is_student_of_teacher(p_owner)
       end;
$$;
grant execute on function public.can_take_exercise(uuid, uuid) to anon, authenticated;

-- 2) can_view_exercise بقت مجرد "منشور" — الرؤية للكل. (بنبقيها موجودة عشان أي كود بيناديها.)
create or replace function public.can_view_exercise(p_owner uuid, p_course uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$ select true; $$;
grant execute on function public.can_view_exercise(uuid, uuid) to anon, authenticated;

-- 3) سياسة القراءة: التمارين المنشورة مرئية للكل، وباقي الأنواع زي ما هي.
--    ملاحظة: تمرين مربوط بكورس (course_id) بيفضل مخفي عن غير المسجّلين زي ما كان.
drop policy if exists tests_read on public.placement_tests;
create policy tests_read on public.placement_tests
  for select
  using (
    owner_id = auth.uid()
    or public.has_permission('tests.manage')
    or (
      status = 'PUBLISHED'
      and (course_id is null or public.has_active_enrollment(auth.uid(), course_id))
    )
  );

-- 4) البدء: لازم can_take_exercise (الحماية الحقيقية — الواجهة بس بتعكسها).
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
     and not public.has_permission('tests.manage')
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
     and not public.has_permission('tests.manage') then
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

-- 5) أمان الأسئلة: قايمة التمارين بقت مفتوحة، فلازم نتأكد إن أسئلة التمرين نفسها (والإجابات الصح)
--    مش مقروءة مباشرة من غير طالب المعلم. التطبيق بيجيب الأسئلة عبر next_test_question فقط.
--    شغّل الاستعلام ده على قاعدة الإنتاج وراجع النتيجة قبل ما تعتمد على أي حاجة:
--      select policyname, cmd, qual from pg_policies where tablename = 'test_questions';
--    لو فيه سياسة SELECT بتسمح لأي مستخدم، اقصرها على: owner_id بتاع الاختبار أو has_permission('tests.manage').
