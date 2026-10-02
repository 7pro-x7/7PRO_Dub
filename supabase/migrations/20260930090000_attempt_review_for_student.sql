-- 7PRO — مراجعة إجابات الطالب في شاشة النتيجة (إجاباته + الإجابة الصحيحة).
-- الطالب مايقدرش يقرأ test_questions مباشرة (سياسة tq_manage_read للمديرين فقط)، فكانت المراجعة فاضية.
-- الدالة دي بترجّع أسئلة محاولة الطالب نفسه بعد ما تنتهي فقط (مش وهي IN_PROGRESS)، ولصاحب التمرين والستاف.
-- APPLIED DIRECTLY to the live 7pro-x7 project on 2026-09-29 via the Supabase MCP tools.

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
