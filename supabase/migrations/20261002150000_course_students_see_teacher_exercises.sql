-- ============================================================================
-- الطالب المشترك في أي دورة مدفوعة عند معلم = طالب عند المعلم ده في التمارين.
--   يقدر يفتح كل تمارين المعلم (زي طلاب الجروبات) طول ما اشتراكه في الدورة نشط.
--   لما الاشتراك ينتهي أو يتلغى، الصلاحية بتقف تلقائيًا.
--   ده بيفتح التمارين بس — دورات المعلم التانية بتفضل مدفوعة زي ما هي.
-- is_student_of_teacher مستخدمة بس في can_take_exercise (بداية التمرين في التطبيق).
-- ============================================================================

create or replace function public.has_course_with_teacher(p_teacher uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and exists (
    select 1
    from public.enrollments e
    join public.courses c on c.id = e.course_id
    where e.user_id = auth.uid()
      and c.teacher_id = p_teacher
      -- Paid courses only: a free course doesn't unlock the teacher's exercises.
      and not coalesce(c.is_free, false)
      and (coalesce(c.base_price, 0) > 0 or coalesce(c.monthly_price, 0) > 0)
      and e.status = 'ACTIVE'
      and (e.expires_at is null or e.expires_at > now())
  );
$$;

create or replace function public.is_student_of_teacher(p_teacher uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and (
    exists (
      select 1 from public.teacher_subscriptions s
      where s.teacher_id = p_teacher
        and s.student_user_id = auth.uid()
        and s.approval_status = 'APPROVED'
        and s.status in ('ACTIVE', 'DUE', 'OVERDUE')
    )
    or public.has_course_with_teacher(p_teacher)
  );
$$;

-- Same rule for the web exercises page.
create or replace function public.can_open_teacher_exercises(p_teacher uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and (
    auth.uid() = p_teacher
    or public.is_staff()
    or public.has_permission('tests.manage')
    or exists (
      select 1 from public.teacher_subscriptions s
      where s.teacher_id = p_teacher
        and s.student_user_id = auth.uid()
        and s.approval_status = 'APPROVED'
        and s.status <> 'PAUSED'
    )
    or public.has_course_with_teacher(p_teacher)
  );
$$;

revoke all on function public.has_course_with_teacher(uuid) from public, anon;
grant execute on function public.has_course_with_teacher(uuid) to authenticated;
