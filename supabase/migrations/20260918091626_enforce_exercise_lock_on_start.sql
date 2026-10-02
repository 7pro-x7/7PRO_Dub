-- is_locked was only a column: the app could hide a locked exercise, but nothing stopped a
-- request from starting an attempt on it anyway. That made the lock a visual suggestion rather
-- than a rule. This enforces it where attempts actually begin.
--
-- The teacher who owns the exercise, and staff, are exempt: they need to be able to open their
-- own locked work to check it. A learner already mid-attempt is also let through — locking an
-- exercise should not strand someone who started before the lock went on.

CREATE OR REPLACE FUNCTION public.start_test_attempt(p_test_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  t public.placement_tests; a public.test_attempts;
  v_attempts int; v_available int; v_total int; v_left int;
begin
  if not public.is_active_account() then raise exception 'ACCOUNT_NOT_ACTIVE'; end if;
  select * into t from public.placement_tests where id = p_test_id;
  if not found or t.status <> 'PUBLISHED' then raise exception 'TEST_NOT_AVAILABLE'; end if;

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
;
