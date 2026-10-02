-- ============================================================================
-- Fix guard_teacher_profile_fields trigger for service-role context
--
-- When confirm_order_payment runs via the checkout edge function with the
-- service_role key, auth.uid() returns NULL. The trigger was blocking the
-- students_count update because is_owner() and has_permission() both fail
-- when auth.uid() is NULL — causing every zero-total coupon checkout to
-- fail with "FORBIDDEN".
--
-- The fix: allow the trigger to pass through when auth.uid() IS NULL
-- (service_role / internal SECURITY DEFINER callers).
-- ============================================================================

CREATE OR REPLACE FUNCTION public.guard_teacher_profile_fields()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO public
AS $function$
begin
  -- Server-side functions (service_role / SECURITY DEFINER callers) set
  -- auth.uid() to NULL.  Allow these so confirm_order_payment can update
  -- students_count without hitting a dead end.
  if auth.uid() IS NULL then
    return new;
  end if;

  if public.is_owner() or public.has_permission('teachers.manage') then
    return new;
  end if;

  if new.commission_rate is distinct from old.commission_rate
     or new.status is distinct from old.status
     or new.can_manage_tests is distinct from old.can_manage_tests
     or new.live_enabled is distinct from old.live_enabled
     or new.badge is distinct from old.badge
     or new.rating_avg is distinct from old.rating_avg
     or new.rating_count is distinct from old.rating_count
     or new.students_count is distinct from old.students_count then
    raise exception 'FORBIDDEN';
  end if;

  return new;
end $function$;
-- Clean up all stuck PENDING zero-total orders from the trigger bug
UPDATE public.orders
SET status = 'FAILED', failure_reason = 'CLEANUP_ZERO_TOTAL_TRIGGER_BUG'
WHERE status = 'PENDING' AND total_amount = 0;
