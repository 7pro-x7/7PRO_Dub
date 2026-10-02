-- 7PRO — رجوع شاشة نتيجة اختبار المستوى للنسخة القديمة (بطلب المالك).
-- بيلغي كل اللي اتضاف في:
--   20260930040000_placement_result_extras / 20260930050000_test_evaluates_level_flag / 20260930060000_result_marketing
-- ويرجّع finish_test_attempt و recommend_nearest_group زي ما كانوا بالظبط.
-- (تعديلات الاشتراك الشهري والتمارين وصفحة الويب مبتتلمسش.)

select cron.unschedule('retest-reminders')
 where exists (select 1 from cron.job where jobname = 'retest-reminders');

create or replace function public.finish_test_attempt(p_attempt_id uuid)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
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

create or replace function public.recommend_nearest_group(p_user uuid)
returns table(teacher_id uuid, teacher_name text, group_name text, level text)
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  v_level text;
  v_recommended_teacher uuid;
begin
  select a.level into v_level
  from public.test_attempts a
  where a.user_id = p_user and a.status = 'SUBMITTED'
  order by a.submitted_at desc
  limit 1;

  if v_level is not null then
    select c.teacher_id into v_recommended_teacher
    from public.courses c
    where c.status = 'PUBLISHED' and c.level = v_level
    order by c.enrollments_count desc nulls last
    limit 1;
  end if;

  if v_recommended_teacher is not null then
    return query
      select tg.teacher_id, p.full_name, tg.name, tg.level
      from public.teacher_groups tg
      join public.profiles p on p.id = tg.teacher_id
      where tg.teacher_id = v_recommended_teacher and tg.approval_status = 'APPROVED'
      order by tg.updated_at desc
      limit 1;
    if found then return; end if;
  end if;

  return query
    select tg.teacher_id, p.full_name, tg.name, tg.level
    from public.teacher_groups tg
    join public.profiles p on p.id = tg.teacher_id
    left join (
      select ts.teacher_id as sub_teacher_id, lower(ts.group_name) as gname, count(*) as members
      from public.teacher_subscriptions ts
      where ts.status <> 'PAUSED'
      group by ts.teacher_id, lower(ts.group_name)
    ) m on m.sub_teacher_id = tg.teacher_id and m.gname = lower(tg.name)
    where tg.approval_status = 'APPROVED'
    order by coalesce(m.members, 0) asc, tg.updated_at desc
    limit 1;
end;
$function$;

drop function if exists public.placement_result_extras(uuid);
drop function if exists public.attempt_review(uuid);
drop function if exists public.retest_reminders();
alter table public.placement_tests drop column if exists evaluates_level;
