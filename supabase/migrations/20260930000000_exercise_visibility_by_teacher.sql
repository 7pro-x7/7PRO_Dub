-- 7PRO — رؤية التمارين:
--   * تمارين المعلم (kind = 'EXERCISE') يشوفها بس: المعلم نفسه، الستاف (tests.manage)،
--     وطلابه المعتمدين (اشتراك APPROVED وحالته ACTIVE/DUE/OVERDUE) — أو الطلاب المسجّلين
--     في الكورس لو التمرين مربوط بكورس (زي ما كان).
--   * تمارين المالك (صاحب الحساب بدور OWNER) المنشورة يشوفها أي حد.
--   * اختبارات تحديد المستوى (PLACEMENT) بتفضل زي ما هي.
-- قبل كده كانت سياسة tests_read بتسمح لأي مستخدم يشوف أي تمرين منشور غير مربوط بكورس،
-- و start_test_attempt ما كانتش بتتحقق من صاحب التمرين، فكان أي حد يقدر يبدأ تمرين أي معلم.

-- ── دوال مساعدة (SECURITY DEFINER عشان ما تتأثرش بـ RLS الجداول اللي بتقرأها) ──────────

create or replace function public.is_platform_owner(p_uid uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select exists (select 1 from public.profiles p where p.id = p_uid and p.role::text = 'OWNER');
$$;

-- تسجيل ساري في كورس. مجهّزة من دلوقتي لتاريخ الانتهاء (expires_at) بتاع الاشتراك الشهري.
-- العمود بيتضاف في الميجريشن اللي بعده، فالدالة بتقرأه بشكل ديناميكي عبر to_jsonb.
create or replace function public.has_active_enrollment(p_user uuid, p_course uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select exists (
    select 1
    from public.enrollments e
    where e.user_id = p_user
      and e.course_id = p_course
      and e.status = 'ACTIVE'
      and coalesce((to_jsonb(e) ->> 'expires_at')::timestamptz, 'infinity'::timestamptz) > now()
  );
$$;

-- هل المستخدم الحالي طالب معتمد عند المعلم ده؟
create or replace function public.is_student_of_teacher(p_teacher uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select auth.uid() is not null and exists (
    select 1
    from public.teacher_subscriptions s
    where s.teacher_id = p_teacher
      and s.student_user_id = auth.uid()
      and s.approval_status = 'APPROVED'
      and s.status in ('ACTIVE', 'DUE', 'OVERDUE')
  );
$$;

-- القاعدة الواحدة لرؤية تمرين منشور (بتتستخدم في السياسة وفي start_test_attempt).
create or replace function public.can_view_exercise(p_owner uuid, p_course uuid)
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

grant execute on function public.is_platform_owner(uuid) to anon, authenticated;
grant execute on function public.has_active_enrollment(uuid, uuid) to anon, authenticated;
grant execute on function public.is_student_of_teacher(uuid) to anon, authenticated;
grant execute on function public.can_view_exercise(uuid, uuid) to anon, authenticated;

-- ── سياسة القراءة ────────────────────────────────────────────────────────────────────

drop policy if exists tests_read on public.placement_tests;
create policy tests_read on public.placement_tests
  for select
  using (
    owner_id = auth.uid()
    or public.has_permission('tests.manage')
    or (
      status = 'PUBLISHED'
      and case
            when kind = 'EXERCISE' then public.can_view_exercise(owner_id, course_id)
            else (course_id is null or public.has_active_enrollment(auth.uid(), course_id))
          end
    )
  );

-- ── منع بدء تمرين مش متاح لك عن طريق الـ RPC مباشرة ─────────────────────────────────

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

  -- تمرين معلم: لازم تكون طالبه، أو التمرين تمرين المالك، أو مسجّل في الكورس المربوط بيه.
  if t.kind = 'EXERCISE' and not public.can_view_exercise(t.owner_id, t.course_id) then
    raise exception 'EXERCISE_NOT_AVAILABLE';
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

  -- Checked after the resume branch above, so an attempt already in progress survives a lock.
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
