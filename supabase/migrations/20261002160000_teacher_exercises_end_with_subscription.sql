-- ============================================================================
-- تمارين المعلم تتقفل أول ما اشتراك الطالب ينتهي.
--   * اشتراك الجروب (teacher_subscriptions): الصلاحية لحد يوم التجديد بالظبط
--     (next_renewal_date بتوقيت القاهرة). من يوم التجديد ولحد ما يتجدد ويتوافق
--     عليه، الطالب ما يقدرش يفتح تمارين المعلم — سواء الحالة DUE أو OVERDUE.
--     الاشتراك الموقوف (PAUSED) أو غير المعتمد ما يفتحش حاجة.
--   * اشتراك الدورة المدفوعة: كان بالفعل بيقف عند expires_at.
--   بعد الانتهاء الطالب بيتعامل زي أي زائر: يشوف قايمة التمارين مقفولة، وما يقدرش يبدأها.
-- ============================================================================

create or replace function public.has_active_teacher_subscription(p_teacher uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and exists (
    select 1 from public.teacher_subscriptions s
    where s.teacher_id = p_teacher
      and s.student_user_id = auth.uid()
      and s.approval_status = 'APPROVED'
      and s.status <> 'PAUSED'
      and s.next_renewal_date > public._cairo_today()
  );
$$;

create or replace function public.is_student_of_teacher(p_teacher uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and (
    public.has_active_teacher_subscription(p_teacher)
    or public.has_course_with_teacher(p_teacher)
  );
$$;

create or replace function public.can_open_teacher_exercises(p_teacher uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and (
    auth.uid() = p_teacher
    or public.is_staff()
    or public.has_permission('tests.manage')
    or public.has_active_teacher_subscription(p_teacher)
    or public.has_course_with_teacher(p_teacher)
  );
$$;

revoke all on function public.has_active_teacher_subscription(uuid) from public, anon;
grant execute on function public.has_active_teacher_subscription(uuid) to authenticated;
