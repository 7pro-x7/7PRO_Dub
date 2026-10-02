-- ============================================================================
-- تمارين الدورة جوه صفحة الدورة:
--   * الدورة المدفوعة: المشترك (اشتراك نشط) يفتح التمارين المربوطة بيها.
--   * الدورة المجانية المنشورة: التمارين المربوطة مفتوحة لأي مستخدم مسجّل دخول،
--     زي دروسها بالظبط (نفس تعريف "مجانية" في التطبيق: is_free أو السعر = 0).
-- تمارين المعلم العامة (خارج الدورات) لسه للدورات المدفوعة فقط (has_course_with_teacher).
-- ============================================================================
create or replace function public.has_exercise_course_access(p_test uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and exists (
    select 1 from public.course_exercise_links l
    join public.courses c on c.id = l.course_id
    where l.exercise_id = p_test
      and (
        public.has_active_enrollment(auth.uid(), l.course_id)
        or (c.status = 'PUBLISHED'::content_status
            and (coalesce(c.is_free, false) or coalesce(c.base_price, 0) <= 0))
      )
  );
$$;
