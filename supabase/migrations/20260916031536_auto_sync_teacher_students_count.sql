
-- teacher_profiles.students_count was only ever set once, inside
-- confirm_order_payment (i.e. only for students who paid through checkout).
-- It never updated for manually-added students, approved subscription
-- requests, transfers, status changes (active -> expired etc.), or
-- deletions. This makes it recompute automatically and stay correct
-- whenever teacher_students changes, regardless of how the row got there.

-- Two existing triggers guard students_count from being edited directly by
-- a client (guard_teacher_profile silently reverts it, guard_teacher_profile_fields
-- raises FORBIDDEN) unless the caller is OWNER/ADMIN. Our internal recompute
-- needs to get through that guard even when triggered by an ordinary
-- teacher's own action, so it sets a transaction-local flag the guards
-- explicitly trust.

CREATE OR REPLACE FUNCTION public.guard_teacher_profile()
RETURNS trigger
LANGUAGE plpgsql
AS $$
begin
  if current_setting('app.internal_sync', true) = 'true' then
    return new;
  end if;
  if public.has_permission('teachers.manage') then
    return new;
  end if;
  new.status := old.status;
  new.accepting_new_students := old.accepting_new_students;
  new.live_enabled := old.live_enabled;
  new.can_manage_tests := old.can_manage_tests;
  new.commission_rate := old.commission_rate;
  new.rating_avg := old.rating_avg;
  new.rating_count := old.rating_count;
  new.students_count := old.students_count;
  new.badge := old.badge;
  return new;
end
$$;

CREATE OR REPLACE FUNCTION public.guard_teacher_profile_fields()
RETURNS trigger
LANGUAGE plpgsql
AS $$
begin
  if current_setting('app.internal_sync', true) = 'true' then
    return new;
  end if;
  if auth.uid() is null then return new; end if;
  if public.is_owner() or public.has_permission('teachers.manage') then return new; end if;
  if new.commission_rate is distinct from old.commission_rate
     or new.status is distinct from old.status
     or new.can_manage_tests is distinct from old.can_manage_tests
     or new.live_enabled is distinct from old.live_enabled
     or new.badge is distinct from old.badge
     or new.is_premium is distinct from old.is_premium
     or new.sort_order is distinct from old.sort_order
     or new.rating_avg is distinct from old.rating_avg
     or new.rating_count is distinct from old.rating_count
     or new.students_count is distinct from old.students_count then
    raise exception 'FORBIDDEN';
  end if;
  return new;
end
$$;

CREATE OR REPLACE FUNCTION public._recompute_teacher_students_count(p_teacher_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF p_teacher_id IS NULL THEN
    RETURN;
  END IF;
  PERFORM set_config('app.internal_sync', 'true', true);
  UPDATE public.teacher_profiles
  SET students_count = (
    SELECT count(*) FROM public.teacher_students
    WHERE teacher_id = p_teacher_id AND status = 'ACTIVE'
  )
  WHERE id = p_teacher_id
    AND students_count IS DISTINCT FROM (
      SELECT count(*) FROM public.teacher_students
      WHERE teacher_id = p_teacher_id AND status = 'ACTIVE'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public._sync_teacher_students_count()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    PERFORM public._recompute_teacher_students_count(OLD.teacher_id);
    RETURN OLD;
  END IF;

  PERFORM public._recompute_teacher_students_count(NEW.teacher_id);
  IF TG_OP = 'UPDATE' AND OLD.teacher_id IS DISTINCT FROM NEW.teacher_id THEN
    PERFORM public._recompute_teacher_students_count(OLD.teacher_id);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_teacher_students_count ON public.teacher_students;
CREATE TRIGGER trg_sync_teacher_students_count
AFTER INSERT OR DELETE OR UPDATE OF status, teacher_id ON public.teacher_students
FOR EACH ROW EXECUTE FUNCTION public._sync_teacher_students_count();

-- Backfill: fix every teacher's count right now.
DO $$
DECLARE r RECORD;
BEGIN
  PERFORM set_config('app.internal_sync', 'true', true);
  FOR r IN SELECT id FROM public.teacher_profiles LOOP
    PERFORM public._recompute_teacher_students_count(r.id);
  END LOOP;
END $$;
;
