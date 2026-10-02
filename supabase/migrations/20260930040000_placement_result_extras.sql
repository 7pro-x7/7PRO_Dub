-- 7PRO — شاشة نتيجة اختبار المستوى: بيانات إضافية للعرض (الحدود الحقيقية للمستويات + عدد اللي اختبروا).
-- دالة قراءة فقط؛ مبتلمسش finish_test_attempt ولا test_attempt_result.
create or replace function public.placement_result_extras(p_attempt_id uuid)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare a public.test_attempts; t public.placement_tests; v_n integer;
begin
  select * into a from public.test_attempts
   where id = p_attempt_id and (user_id = auth.uid() or public.has_permission('tests.manage'));
  if not found then raise exception 'ATTEMPT_NOT_FOUND'; end if;
  select * into t from public.placement_tests where id = a.test_id;
  select count(*) into v_n from public.test_attempts where test_id = a.test_id and status = 'SUBMITTED';
  return jsonb_build_object(
    'sample_size', v_n,
    'levels', to_jsonb(coalesce(t.levels, array['A1','A2','B1','B2','C1','C2'])),
    'level_thresholds', coalesce(t.level_thresholds, '{}'::jsonb)
  );
end
$function$;

revoke all on function public.placement_result_extras(uuid) from public, anon;
grant execute on function public.placement_result_extras(uuid) to authenticated;
