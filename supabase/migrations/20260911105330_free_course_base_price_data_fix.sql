-- Data safety net for resolve_price_honors_course_is_free.
-- Keeps base_price consistent with is_free for any screen/report that still reads it directly,
-- not just for checkout (which already goes through resolve_price).
update public.courses
   set base_price = 0
 where is_free = true
   and base_price is distinct from 0;;
