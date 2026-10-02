-- Applied on production as "subscription_status_derived_from_renewal_date".
-- Subscription status is decided by the SYSTEM from next_renewal_date, never by the teacher:
--   next_renewal_date <  today          -> OVERDUE
--   next_renewal_date <= today + 7 days -> DUE
--   otherwise                           -> ACTIVE
-- Only PAUSED is a manual state (it is not derived from a date) and is left alone.
-- Only APPROVED subscriptions are derived (pending/rejected ones don't count anywhere).

CREATE OR REPLACE FUNCTION public._subscription_status_for(p_next_renewal date)
RETURNS text
LANGUAGE sql
STABLE
SET search_path TO 'public'
AS $$
  SELECT CASE
    WHEN p_next_renewal < current_date THEN 'OVERDUE'
    WHEN p_next_renewal <= current_date + 7 THEN 'DUE'
    ELSE 'ACTIVE'
  END;
$$;

-- Runs on every insert/update, so a teacher (or any client) cannot set ACTIVE/DUE/OVERDUE by hand
-- and changing a date moves the status immediately. Named trg_zz_* so it fires after the other
-- BEFORE triggers (renewal-date defaulting / start-date realignment) have settled the date.
CREATE OR REPLACE FUNCTION public._derive_subscription_status()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO 'public'
AS $$
BEGIN
  IF NEW.approval_status = 'APPROVED' AND NEW.status <> 'PAUSED' THEN
    NEW.status := public._subscription_status_for(NEW.next_renewal_date);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_zz_derive_subscription_status ON public.teacher_subscriptions;
CREATE TRIGGER trg_zz_derive_subscription_status
  BEFORE INSERT OR UPDATE ON public.teacher_subscriptions
  FOR EACH ROW EXECUTE FUNCTION public._derive_subscription_status();

-- The periodic/on-read refresh now works in BOTH directions (it used to only ever move
-- ACTIVE/DUE -> OVERDUE, so a subscription whose date moved forward stayed OVERDUE forever).
CREATE OR REPLACE FUNCTION public.refresh_subscription_statuses()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_count INTEGER := 0;
BEGIN
  UPDATE public.teacher_subscriptions
  SET status = public._subscription_status_for(next_renewal_date)
  WHERE approval_status = 'APPROVED'
    AND status <> 'PAUSED'
    AND status IS DISTINCT FROM public._subscription_status_for(next_renewal_date);
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$function$;

-- One-off: fix subscriptions already stuck on the wrong status (e.g. OVERDUE with a future date).
SELECT public.refresh_subscription_statuses();
