-- 7PRO — شاشة النتيجة (تسويق):
--  1) placement_result_extras بيرجّع كمان correct_count / answered_count (هوك «جاوبت X من Y صح»).
--  2) attempt_review(p_attempt_id): مراجعة إجابات المحاولة للطالب صاحبها (أو المالك/الأدمن/معلم التمرين).
--       - بعد ما المحاولة تخلص بس (مش أثناء الحل)،
--       - التمرين: السؤال + إجابته + الإجابة الصح + الشرح. اختبار تحديد المستوى: السؤال + إجابته + صح/غلط بس
--         (من غير الإجابة الصح ولا الشرح، عشان ما تتسرّبش وتتحفظ).
--     جدول test_questions نفسه بيفضل مقفول على الطلاب.
--  3) retest_reminders(): إشعار للطالب بعد ٣٠ يوم من آخر اختبار مستوى (اختبار بيحدد مستوى فعلًا)، وكرون يومي.

create or replace function public.placement_result_extras(p_attempt_id uuid)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare a public.test_attempts; t public.placement_tests; v_n integer; v_ok integer; v_all integer;
begin
  select * into a from public.test_attempts
   where id = p_attempt_id and (user_id = auth.uid() or public.has_permission('tests.manage'));
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;
  select count(*) into v_n from public.test_attempts where test_id = a.test_id and status = 'SUBMITTED';
  select count(*) filter (where is_correct), count(*) into v_ok, v_all
    from public.test_answers where attempt_id = a.id;
  return jsonb_build_object(
    'sample_size', v_n,
    'correct_count', v_ok,
    'answered_count', v_all,
    'levels', to_jsonb(coalesce(t.levels, array['A1','A2','B1','B2','C1','C2'])),
    'level_thresholds', coalesce(t.level_thresholds, '{}'::jsonb)
  );
end
$function$;

create or replace function public.attempt_review(p_attempt_id uuid)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare a public.test_attempts; t public.placement_tests; v_manager boolean; v_reveal boolean;
begin
  select * into a from public.test_attempts where id = p_attempt_id;
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;

  v_manager := public.has_permission('tests.manage') or t.owner_id = auth.uid();
  if not (a.user_id = auth.uid() or v_manager) then raise exception 'ATTEMPT_NOT_FOUND'; end if;

  -- أثناء الحل الطالب مايشوفش حاجة (ما نكشفش الإجابات وهو لسه بيجاوب).
  if a.status = 'IN_PROGRESS' and not v_manager then return '[]'::jsonb; end if;

  v_reveal := t.kind = 'EXERCISE' or v_manager;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'answer', jsonb_build_object(
        'id', x.id, 'attempt_id', x.attempt_id, 'question_id', x.question_id,
        'selected_indexes', to_jsonb(coalesce(x.selected_indexes, '{}'::int[])),
        'text_answer', x.text_answer, 'is_correct', x.is_correct,
        'earned', x.earned, 'answered_at', x.answered_at),
      'question', jsonb_build_object(
        'id', q.id, 'test_id', q.test_id, 'kind', q.kind, 'skill', q.skill, 'prompt', q.prompt,
        'options', coalesce(q.options, '[]'::jsonb),
        'correct_indexes', case when v_reveal then to_jsonb(coalesce(q.correct_indexes, '{}'::int[])) else '[]'::jsonb end,
        'correct_text', case when v_reveal then to_jsonb(q.correct_text) else null end,
        'explanation', case when v_reveal then q.explanation else null end,
        'sort_order', q.sort_order)
    ) order by x.answered_at)
    from public.test_answers x join public.test_questions q on q.id = x.question_id
    where x.attempt_id = a.id
  ), '[]'::jsonb);
end
$function$;

revoke all on function public.attempt_review(uuid) from public, anon;
grant execute on function public.attempt_review(uuid) to authenticated;

create or replace function public.retest_reminders()
returns integer
language plpgsql security definer set search_path to 'public'
as $function$
declare r record; n integer := 0;
begin
  for r in
    select x.user_id from (
      select distinct on (a.user_id) a.user_id, a.submitted_at
        from public.test_attempts a
        join public.placement_tests t on t.id = a.test_id
       where t.kind = 'PLACEMENT' and a.status = 'SUBMITTED' and a.level is not null
       order by a.user_id, a.submitted_at desc
    ) x
    join public.profiles p on p.id = x.user_id and p.status = 'ACTIVE' and not coalesce(p.is_guest, false)
    where x.submitted_at <= now() - interval '30 days'
      and x.submitted_at >  now() - interval '31 days'
  loop
    perform public.push_notification(
      r.user_id, 'TEST_RETEST',
      'عدّى شهر على اختبار مستواك • Time to retest',
      'اختبر تاني وشوف اتقدّمت قد إيه. — Take the test again and see how far you have come.',
      '{}'::jsonb);
    n := n + 1;
  end loop;
  return n;
end
$function$;

revoke all on function public.retest_reminders() from public, anon, authenticated;
grant execute on function public.retest_reminders() to service_role;

select cron.schedule('retest-reminders', '0 9 * * *', 'select public.retest_reminders()');
