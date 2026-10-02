-- =============================================================================================
-- 1) Retire the cartoon-character conversation exercise completely.
--    Live data check before this ran: 0 questions of kind CONVERSATION, 0 with a dialogue.
--    The `dialogue` column is KEPT (always NULL) on purpose: app builds already installed on
--    students' and teachers' phones still send `"dialogue": null` when saving a question, and
--    dropping the column would make every save from those builds fail until they update.
-- =============================================================================================

create or replace function public.validate_test_question_payload()
returns trigger language plpgsql set search_path to 'public', 'pg_temp' as $function$
declare
  v_options text[];
begin
  if new.kind = 'CONVERSATION' then
    raise exception 'QUESTION_KIND_REMOVED';
  end if;
  new.dialogue := null;
  if new.kind in ('SINGLE','MULTI','TRUE_FALSE') then
    v_options := array(select jsonb_array_elements_text(coalesce(new.options, '[]'::jsonb)));
    if coalesce(array_length(v_options, 1), 0) < 2 then
      raise exception 'QUESTION_OPTIONS_REQUIRED';
    end if;
    if coalesce(array_length(new.correct_indexes, 1), 0) = 0
       or exists (select 1 from unnest(new.correct_indexes) x where x < 0 or x >= array_length(v_options,1)) then
      raise exception 'QUESTION_CORRECT_OPTION_INVALID';
    end if;
  elsif new.kind = 'TEXT' then
    if coalesce(array_length(new.correct_text, 1), 0) = 0 then
      raise exception 'QUESTION_CORRECT_TEXT_REQUIRED';
    end if;
  end if;
  return new;
end;
$function$;

alter table public.test_questions drop constraint if exists chk_no_cartoon_conversation;
alter table public.test_questions
  add constraint chk_no_cartoon_conversation check (kind <> 'CONVERSATION' and dialogue is null);

create or replace function public.next_test_question(p_attempt_id uuid)
returns jsonb language plpgsql security definer set search_path to 'public' as $function$
declare
  a public.test_attempts; t public.placement_tests; q public.test_questions;
  v_available int; v_total int; v_left int; v_pending uuid;
begin
  select * into a from public.test_attempts where id = p_attempt_id and user_id = auth.uid();
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;

  select count(*) into v_available from public.test_questions where test_id = a.test_id and is_active;
  v_total := least(greatest(t.question_count, 1), greatest(v_available, 1));

  if a.status <> 'IN_PROGRESS' then return jsonb_build_object('done', true, 'total', v_total); end if;
  if a.answered_count >= v_total then return jsonb_build_object('done', true, 'total', v_total); end if;

  v_left := t.time_limit_seconds - floor(extract(epoch from (now() - a.started_at)))::int;
  if v_left <= 0 then
    return jsonb_build_object('done', true, 'reason', 'TIME_UP', 'total', v_total);
  end if;

  if coalesce(array_length(a.asked_question_ids, 1), 0) > a.answered_count then
    v_pending := a.asked_question_ids[array_length(a.asked_question_ids, 1)];
    select * into q from public.test_questions where id = v_pending and is_active;
  end if;

  if q.id is null then
    if t.is_adaptive then
      select * into q from public.test_questions
        where test_id = a.test_id and is_active and not (id = any(a.asked_question_ids))
        order by abs(difficulty - a.current_difficulty), random() limit 1;
    else
      select * into q from public.test_questions
        where test_id = a.test_id and is_active and not (id = any(a.asked_question_ids))
        order by case when t.randomize then random() else sort_order end limit 1;
    end if;

    if q.id is null then
      return jsonb_build_object('done', true, 'reason', 'NO_MORE_QUESTIONS', 'total', v_total);
    end if;

    update public.test_attempts set asked_question_ids = array_append(asked_question_ids, q.id)
      where id = a.id;
  end if;

  return jsonb_build_object(
    'done', false,
    'question', jsonb_build_object(
      'id', q.id, 'kind', q.kind, 'skill', q.skill, 'difficulty', q.difficulty,
      'prompt', q.prompt, 'options', coalesce(q.options, '[]'::jsonb),
      'image_url', q.media_image_url, 'audio_url', q.media_audio_url, 'video_url', q.media_video_url,
      'points', q.points
    ),
    'index', a.answered_count + 1,
    'total', v_total,
    'seconds_left', v_left
  );
end
$function$;

create or replace function public.submit_test_answer(p_attempt_id uuid, p_question_id uuid, p_selected integer[], p_text text, p_order integer[], p_match jsonb)
returns jsonb language plpgsql security definer set search_path to 'public' as $function$
declare
  a public.test_attempts; q public.test_questions;
  v_correct boolean := false; v_earned numeric := 0; v_norm text; v_delta int;
  v_gradable boolean; v_existing public.test_answers;
begin
  select * into a from public.test_attempts where id = p_attempt_id and user_id = auth.uid() for update;
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into q from public.test_questions where id = p_question_id and test_id = a.test_id;
  if not found then raise exception 'QUESTION_NOT_FOUND'; end if;

  select * into v_existing from public.test_answers where attempt_id = a.id and question_id = q.id;
  if found then
    return jsonb_build_object('correct', v_existing.is_correct, 'earned', v_existing.earned,
      'explanation', q.explanation, 'graded', true, 'repeat', true);
  end if;

  if a.status <> 'IN_PROGRESS' then raise exception 'ATTEMPT_CLOSED'; end if;

  v_gradable := case
    when q.kind in ('SINGLE','MULTI','TRUE_FALSE') then coalesce(array_length(q.correct_indexes, 1), 0) > 0
    when q.kind = 'TEXT' then coalesce(array_length(q.correct_text, 1), 0) > 0
    when q.kind = 'ORDERING' then coalesce(array_length(q.order_answer, 1), 0) > 0
    when q.kind = 'MATCHING' then coalesce(q.match_pairs, '{}'::jsonb) <> '{}'::jsonb
    else false
  end;
  v_gradable := coalesce(v_gradable, false);

  if v_gradable then
    if q.kind in ('SINGLE','MULTI','TRUE_FALSE') then
      v_correct := coalesce(
        (select array(select distinct unnest(coalesce(p_selected, '{}')) order by 1)
             = array(select distinct unnest(q.correct_indexes) order by 1)), false);
    elsif q.kind = 'TEXT' then
      v_norm := lower(trim(coalesce(p_text, '')));
      v_correct := v_norm <> '' and exists (
        select 1 from unnest(coalesce(q.correct_text, '{}')) ct where lower(trim(ct)) = v_norm);
    elsif q.kind = 'ORDERING' then
      v_correct := coalesce(p_order, '{}') = coalesce(q.order_answer, '{}');
    elsif q.kind = 'MATCHING' then
      v_correct := coalesce(p_match, '{}'::jsonb) = coalesce(q.match_pairs, '{}'::jsonb);
    end if;
  end if;

  if v_correct then v_earned := q.points * q.weight; end if;

  insert into public.test_answers(attempt_id, question_id, selected_indexes, text_answer, order_answer, match_answer, is_correct, earned)
  values (a.id, q.id, coalesce(p_selected, '{}'), p_text, p_order, p_match, v_correct, v_earned);

  v_delta := case when not v_gradable then 0 when v_correct then 1 else -1 end;

  update public.test_attempts set
    answered_count = answered_count + 1,
    correct_count = correct_count + case when v_correct then 1 else 0 end,
    raw_score = raw_score + v_earned,
    max_score = max_score + case when v_gradable then q.points * q.weight else 0 end,
    current_difficulty = greatest(1, least(6, current_difficulty + v_delta))
  where id = a.id;

  return jsonb_build_object('correct', v_correct, 'earned', v_earned,
    'explanation', q.explanation, 'graded', v_gradable);
end
$function$;

create or replace function public.finish_test_attempt(p_attempt_id uuid)
returns jsonb language plpgsql security definer set search_path to 'public' as $function$
declare
  a public.test_attempts; t public.placement_tests;
  v_percent numeric; v_level text; v_skills jsonb; v_strengths text[]; v_weak text[];
  v_rank numeric; k text; thr numeric; v_best_level text;
begin
  select * into a from public.test_attempts where id = p_attempt_id and user_id = auth.uid() for update;
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;

  if a.status <> 'IN_PROGRESS' then
    select round(100.0 * (count(*) filter (where percent < a.percent))::numeric / greatest(count(*),1), 0)
      into v_rank from public.test_attempts where test_id = a.test_id and status = 'SUBMITTED';
    return jsonb_build_object('attempt_id', a.id, 'percent', a.percent, 'level', a.level,
      'skills', coalesce(a.skill_breakdown, '{}'::jsonb), 'strengths', coalesce(a.strengths, '{}'),
      'weaknesses', coalesce(a.weaknesses, '{}'), 'better_than_percent', v_rank,
      'passed', a.percent >= t.passing_score, 'status', a.status,
      'answered', a.answered_count, 'already', true);
  end if;

  if a.answered_count = 0 then
    update public.test_attempts set status = 'ABANDONED', submitted_at = now() where id = a.id;
    return jsonb_build_object('attempt_id', a.id, 'percent', 0, 'level', null,
      'skills', '{}'::jsonb, 'strengths', '{}', 'weaknesses', '{}',
      'better_than_percent', null, 'passed', false, 'status', 'ABANDONED', 'answered', 0);
  end if;

  v_percent := case when a.max_score > 0 then round((a.raw_score / a.max_score) * 100, 2) else 0 end;

  select coalesce(jsonb_object_agg(skill, pct), '{}'::jsonb) into v_skills from (
    select q.skill,
           round(case when sum(q.points*q.weight) > 0
                      then sum(ans.earned) / sum(q.points*q.weight) * 100 else 0 end, 0) as pct
    from public.test_answers ans join public.test_questions q on q.id = ans.question_id
    where ans.attempt_id = a.id
      and coalesce(case
            when q.kind in ('SINGLE','MULTI','TRUE_FALSE') then coalesce(array_length(q.correct_indexes, 1), 0) > 0
            when q.kind = 'TEXT' then coalesce(array_length(q.correct_text, 1), 0) > 0
            when q.kind = 'ORDERING' then coalesce(array_length(q.order_answer, 1), 0) > 0
            when q.kind = 'MATCHING' then coalesce(q.match_pairs, '{}'::jsonb) <> '{}'::jsonb
            else false
          end, false)
    group by q.skill
  ) s;

  select coalesce(array_agg(skill order by pct desc), '{}') into v_strengths from (
    select key as skill, (value#>>'{}')::numeric as pct from jsonb_each(v_skills)
    order by (value#>>'{}')::numeric desc limit 2
  ) x;
  select coalesce(array_agg(skill order by pct asc), '{}') into v_weak from (
    select key as skill, (value#>>'{}')::numeric as pct from jsonb_each(v_skills)
    order by (value#>>'{}')::numeric asc limit 2
  ) y;

  v_best_level := coalesce(t.levels[1], 'A1');
  for k, thr in select key, (value#>>'{}')::numeric from jsonb_each(t.level_thresholds) order by (value#>>'{}')::numeric asc loop
    if v_percent >= thr then v_best_level := k; end if;
  end loop;
  v_level := v_best_level;

  update public.test_attempts set status = 'SUBMITTED', submitted_at = now(),
    percent = v_percent, level = v_level, skill_breakdown = v_skills,
    strengths = v_strengths, weaknesses = v_weak
  where id = a.id;

  update public.profiles set current_level = v_level where id = auth.uid();

  select round(100.0 * (count(*) filter (where percent < v_percent))::numeric / greatest(count(*),1), 0)
    into v_rank from public.test_attempts where test_id = a.test_id and status = 'SUBMITTED';

  perform public.push_notification(auth.uid(), 'TEST_RESULT', 'Your level: ' || v_level,
    'You scored ' || v_percent || '% on ' || t.title || '.', jsonb_build_object('attempt_id', a.id, 'level', v_level));

  return jsonb_build_object('attempt_id', a.id, 'percent', v_percent, 'level', v_level,
    'skills', v_skills, 'strengths', v_strengths, 'weaknesses', v_weak,
    'better_than_percent', v_rank, 'passed', v_percent >= t.passing_score,
    'status', 'SUBMITTED', 'answered', a.answered_count);
end
$function$;

drop function if exists public.conversation_score(jsonb, text);

-- The unused "buddy" cartoon-character tables (2 seed rows each, nothing in the app reads them).
drop table if exists public.buddy_results;
drop table if exists public.exercise_buddy_configs;
drop table if exists public.buddy_scenarios;
drop table if exists public.buddy_characters;
-- Storage policy that let staff upload buddy voice clips; it depends on buddy_can_manage().
drop policy if exists "buddy_audio_upload" on storage.objects;
drop function if exists public.buddy_log_result(uuid, uuid, integer, integer, boolean);
drop function if exists public.buddy_set_enabled(boolean);
drop function if exists public.buddy_can_manage();
drop function if exists public.buddy_touch_updated_at();

-- =============================================================================================
-- 2) AI Tutor (face-to-face call). The brain/ears/voice run on a Cloudflare Worker using open
--    models on Workers AI; this is only the gatekeeper: who may talk, how much, and where the
--    Worker and the 3D avatar live (so both can change without shipping a new app build).
--    Days are UTC days, matching Cloudflare's free-allocation reset at 00:00 UTC.
-- =============================================================================================

create table if not exists public.ai_tutor_config (
  id               boolean primary key default true check (id),
  enabled          boolean not null default true,
  per_user_daily   int     not null default 20  check (per_user_daily between 0 and 1000),
  per_user_opens   int     not null default 12  check (per_user_opens between 0 and 1000),
  global_daily     int     not null default 420 check (global_daily between 0 and 1000000),
  global_opens     int     not null default 300 check (global_opens between 0 and 1000000),
  worker_url       text    not null default '',
  avatar_url       text    not null default '',
  avatar_version   int     not null default 0,
  updated_at       timestamptz not null default now()
);
insert into public.ai_tutor_config(id) values (true) on conflict (id) do nothing;
alter table public.ai_tutor_config enable row level security;

create table if not exists public.ai_tutor_turns (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  kind       text not null check (kind in ('turn','open')),
  day        date not null default (timezone('utc', now()))::date,
  created_at timestamptz not null default now()
);
create index if not exists ai_tutor_turns_day_user_idx on public.ai_tutor_turns(day, user_id, kind);
create index if not exists ai_tutor_turns_day_kind_idx on public.ai_tutor_turns(day, kind);
alter table public.ai_tutor_turns enable row level security;
-- No policies on purpose: both tables are reached only through the functions below.

create or replace function public.ai_tutor_status()
returns jsonb language plpgsql stable security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into c from public.ai_tutor_config where id;
  select coalesce(is_guest, false) into v_guest from public.profiles where id = v_uid;
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

-- Called by the Worker with the student's own JWT before it spends anything on Workers AI.
-- Returns a ticket the Worker hands back to ai_tutor_refund_turn if the AI call then fails,
-- so a network hiccup never costs the student one of their daily turns. The ticket id is
-- never returned to the app, only to the Worker.
create or replace function public.ai_tutor_take_turn(p_kind text default 'turn')
returns jsonb language plpgsql volatile security definer set search_path to 'public' as $$
declare
  c public.ai_tutor_config;
  v_uid uuid := auth.uid();
  v_day date := timezone('utc', now())::date;
  v_used int; v_global int; v_guest boolean; v_ticket uuid; v_limit int; v_glimit int;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_kind not in ('turn','open') then raise exception 'INVALID_KIND'; end if;
  select * into c from public.ai_tutor_config where id;
  select coalesce(is_guest, false) into v_guest from public.profiles where id = v_uid;
  if not c.enabled or coalesce(v_guest, false) then
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

create or replace function public.ai_tutor_refund_turn(p_ticket uuid)
returns void language sql volatile security definer set search_path to 'public' as $$
  delete from public.ai_tutor_turns
   where id = p_ticket and user_id = auth.uid() and created_at > now() - interval '3 minutes';
$$;

-- Owner/Admin only: point the app at the deployed Worker and the uploaded avatar, tune limits.
create or replace function public.ai_tutor_configure(
  p_worker_url text default null, p_avatar_url text default null, p_bump_avatar boolean default false,
  p_per_user_daily int default null, p_global_daily int default null, p_enabled boolean default null)
returns jsonb language plpgsql volatile security definer set search_path to 'public' as $$
begin
  if not exists (select 1 from public.profiles where id = auth.uid() and role in ('OWNER','ADMIN')) then
    raise exception 'FORBIDDEN';
  end if;
  update public.ai_tutor_config set
    worker_url     = coalesce(nullif(btrim(p_worker_url), ''), worker_url),
    avatar_url     = coalesce(nullif(btrim(p_avatar_url), ''), avatar_url),
    avatar_version = avatar_version + case when p_bump_avatar or p_avatar_url is not null then 1 else 0 end,
    per_user_daily = coalesce(p_per_user_daily, per_user_daily),
    global_daily   = coalesce(p_global_daily, global_daily),
    enabled        = coalesce(p_enabled, enabled),
    updated_at     = now()
  where id;
  return (select to_jsonb(c) from public.ai_tutor_config c where id);
end $$;

revoke all on function public.ai_tutor_status() from public, anon;
revoke all on function public.ai_tutor_take_turn(text) from public, anon;
revoke all on function public.ai_tutor_refund_turn(uuid) from public, anon;
revoke all on function public.ai_tutor_configure(text, text, boolean, int, int, boolean) from public, anon;
grant execute on function public.ai_tutor_status() to authenticated;
grant execute on function public.ai_tutor_take_turn(text) to authenticated;
grant execute on function public.ai_tutor_refund_turn(uuid) to authenticated;
grant execute on function public.ai_tutor_configure(text, text, boolean, int, int, boolean) to authenticated;

-- Public bucket for the 3D tutor model (.glb). Reads are public (it's a 3D model, not user
-- data); only Owner/Admin can upload or replace it.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('tutor-assets', 'tutor-assets', true, 31457280, array['model/gltf-binary','application/octet-stream'])
on conflict (id) do nothing;

drop policy if exists "tutor_assets_staff_write" on storage.objects;
create policy "tutor_assets_staff_write" on storage.objects for all to authenticated
  using (bucket_id = 'tutor-assets' and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER','ADMIN')))
  with check (bucket_id = 'tutor-assets' and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER','ADMIN')));
