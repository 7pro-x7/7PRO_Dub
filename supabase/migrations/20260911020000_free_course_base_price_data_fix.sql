-- Data safety net for 20260910150000_free_course_resolve_price_honors_is_free.sql.
--
-- That migration made resolve_price() treat is_free=true as authoritative regardless of
-- base_price, which is the correct fix for checkout. But it only changes behaviour going
-- forward from whatever base_price a row already holds; it does nothing for a course that
-- was flagged free by hand (direct SQL / an older client build) with a base_price that was
-- never zeroed. This backfills those rows so the free/paid signal is consistent everywhere
-- that still reads base_price directly (course lists, admin screens, analytics), not just at
-- checkout.
update public.courses
   set base_price = 0
 where is_free = true
   and base_price is distinct from 0;
