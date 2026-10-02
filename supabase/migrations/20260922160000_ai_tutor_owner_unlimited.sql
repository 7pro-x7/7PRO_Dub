-- =============================================================================================
-- AI Tutor: the platform Owner is exempt from both the per-user daily cap and the global daily
-- cap. Everyone else (students, teachers, admins) keeps the limits configured in
-- ai_tutor_config exactly as before. Owner turns are still logged in ai_tutor_turns (so usage
-- stays visible in analytics) but never counted against anyone's remaining allowance.
-- =============================================================================================

create or replace function public.ai_tutor_status()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean; v_role text;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into c from public.ai_tutor_config where id;
  select coalesce(is_guest, false), role into v_guest, v_role from public.profiles where id = v_uid;

  if v_role = 'OWNER' then
    return jsonb_build_object(
      'enabled', c.enabled and c.worker_url <> '',
      'per_user_daily', c.per_user_daily,
      'used', 0,
      'remaining', 999999,
      'global_available', true,
      'worker_url', c.worker_url,
      'avatar_url', c.avatar_url,
      'avatar_version', c.avatar_version,
      'resets_at', ((v_day + 1)::timestamp at time zone 'UTC')
    );
  end if;

  select count(*) into v_used from public.ai_tutor_turns where day = v_day and user_id = v_uid and kind = 'turn';
  select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = 'turn';
  return jsonb_build_object(
    'enabled', c.enabled and c.worker_url <> '' and not coalesce(v_guest, false),
    'per_user_daily', c.per_user_daily,
    'used', v_used,
    'remaining', greatest(c.per_user_daily - v_used, 0),
    'global_available', v_global < c.global_daily,
    'worker_url', c.worker_url,
    'avatar_url', c.avatar_url,
    'avatar_version', c.avatar_version,
    'resets_at', ((v_day + 1)::timestamp at time zone 'UTC')
  );
end $$;

create or replace function public.ai_tutor_take_turn(p_kind text default 'turn')
returns jsonb language plpgsql volatile security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean; v_role text; v_ticket uuid; v_limit int; v_glimit int;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_kind not in ('turn','open') then raise exception 'INVALID_KIND'; end if;
  select * into c from public.ai_tutor_config where id;
  select coalesce(is_guest, false), role into v_guest, v_role from public.profiles where id = v_uid;
  if not c.enabled then
    return jsonb_build_object('allowed', false, 'reason', 'DISABLED');
  end if;

  -- Owner: log the turn (so usage still shows up in analytics) but never enforce a cap,
  -- and never let it count against the global pool other users share.
  if v_role = 'OWNER' then
    insert into public.ai_tutor_turns(user_id, kind, day) values (v_uid, p_kind, v_day) returning id into v_ticket;
    return jsonb_build_object('allowed', true, 'ticket', v_ticket, 'remaining', 999999);
  end if;

  if coalesce(v_guest, false) then
    return jsonb_build_object('allowed', false, 'reason', 'DISABLED');
  end if;

  -- One student's parallel requests queue up here instead of racing past the limit.
  -- (Limits resolved up front: a CASE inside an IF condition trips PL/pgSQL's THEN scanner.)
  v_limit  := case when p_kind = 'turn' then c.per_user_daily else c.per_user_opens end;
  v_glimit := case when p_kind = 'turn' then c.global_daily   else c.global_opens   end;

  perform pg_advisory_xact_lock(hashtext('ai_tutor:' || v_uid::text));

  select count(*) into v_used from public.ai_tutor_turns where day = v_day and user_id = v_uid and kind = p_kind;
  if v_used >= v_limit then
    return jsonb_build_object('allowed', false, 'reason', 'USER_LIMIT', 'remaining', 0);
  end if;
  select count(*) into v_global from public.ai_tutor_turns where day = v_day and kind = p_kind;
  if v_global >= v_glimit then
    return jsonb_build_object('allowed', false, 'reason', 'GLOBAL_LIMIT');
  end if;

  insert into public.ai_tutor_turns(user_id, kind, day) values (v_uid, p_kind, v_day) returning id into v_ticket;

  if random() < 0.02 then
    delete from public.ai_tutor_turns where day < v_day - 45;
  end if;

  return jsonb_build_object(
    'allowed', true,
    'ticket', v_ticket,
    'remaining', case when p_kind = 'turn' then greatest(c.per_user_daily - v_used - 1, 0) else null end
  );
end $$;
