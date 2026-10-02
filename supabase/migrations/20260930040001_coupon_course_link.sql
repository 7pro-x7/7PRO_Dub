-- A coupon linked to specific courses (coupons.course_ids, enforced by evaluate_coupon) must never
-- also apply to the AI Tutor: ai_tutor_evaluate_coupon does not look at course_ids.
alter table public.coupons drop constraint if exists coupons_course_link_no_tutor;
alter table public.coupons add constraint coupons_course_link_no_tutor
  check (coalesce(array_length(course_ids, 1), 0) = 0 or ai_tutor_scope = 'NONE');
