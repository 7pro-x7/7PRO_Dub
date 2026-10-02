-- ============================================================================
-- صلاحيات ناقصة: كانت في السيرفر لكن مش ظاهرة للمالك في قائمة صلاحيات الأدمن،
-- وحاجات تانية كانت للمالك بس ومفيش صلاحية تتمنحلها.
--   * students.manage  : (موجودة في السيرفر) نقل الطلاب بين المعلمين وإدارة طلاب المعلم.
--   * ai_tutor.manage  : (جديدة) خطط المعلّم الذكي وملخص مبيعاته.
--   * settings.manage  : بقت تكفي لإعدادات الترجمة الفورية في الفصل.
-- اللي فضل للمالك بس عن قصد: تعيين الأدمن وصلاحياتهم، تغيير إيميل/باسورد أي حساب، وإجبار التحديث.
-- ============================================================================

create or replace function public.ai_tutor_sales_summary()
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
begin
  if not (public.is_owner() or public.has_permission('ai_tutor.manage')) then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'active_subscribers', (select count(distinct user_id) from public.ai_tutor_entitlements
                            where status = 'ACTIVE' and starts_at <= now() and ends_at > now()
                              and replies_used < replies_total),
    'paid_orders', (select count(*) from public.orders
                     where item_type = 'AI_TUTOR' and status in ('PAID', 'PARTIALLY_REFUNDED')),
    'revenue', (select coalesce(jsonb_object_agg(currency, s), '{}'::jsonb)
                  from (select currency, sum(total_amount - refunded_amount) s from public.orders
                         where item_type = 'AI_TUTOR' and status in ('PAID', 'PARTIALLY_REFUNDED')
                         group by currency) x),
    'replies_sold', (select coalesce(sum(replies_total), 0) from public.ai_tutor_entitlements where status = 'ACTIVE'),
    'replies_used', (select coalesce(sum(replies_used), 0) from public.ai_tutor_entitlements where status = 'ACTIVE'),
    'replies_expired', (select coalesce(sum(replies_total - replies_used), 0) from public.ai_tutor_entitlements
                         where status = 'ACTIVE' and ends_at <= now())
  );
end $$;

drop policy if exists ai_tutor_plans_owner_write on public.ai_tutor_plans;
create policy ai_tutor_plans_owner_write on public.ai_tutor_plans for all
  using (public.is_owner() or public.has_permission('ai_tutor.manage'))
  with check (public.is_owner() or public.has_permission('ai_tutor.manage'));

drop policy if exists ai_tutor_plans_read on public.ai_tutor_plans;
create policy ai_tutor_plans_read on public.ai_tutor_plans for select
  using (is_active or public.is_owner() or public.has_permission('ai_tutor.manage'));

drop policy if exists ai_tutor_entitlements_read on public.ai_tutor_entitlements;
create policy ai_tutor_entitlements_read on public.ai_tutor_entitlements for select
  using (user_id = auth.uid() or public.is_owner() or public.has_permission('ai_tutor.manage'));

create or replace function public.classroom_set_live_translation(p_enabled boolean, p_server_url text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_url text := btrim(coalesce(p_server_url, ''));
begin
  if not (public.classroom_is_owner() or public.has_permission('settings.manage')) then
    raise exception 'FORBIDDEN';
  end if;
  if v_url <> '' and v_url !~* '^wss?://' then raise exception 'INVALID_URL'; end if;
  if p_enabled and v_url = '' then raise exception 'SERVER_URL_REQUIRED'; end if;

  insert into public.app_settings (key, value, updated_by, updated_at)
  values ('classroom.live_translation_enabled', to_jsonb(coalesce(p_enabled, false)), auth.uid(), now()),
         ('classroom.live_translation_server_url', to_jsonb(v_url), auth.uid(), now())
  on conflict (key) do update
    set value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;

  perform public.write_audit('settings.update', 'setting', 'classroom.live_translation',
    jsonb_build_object('enabled', coalesce(p_enabled, false), 'server_url', v_url));
  return jsonb_build_object('ok', true);
end $$;
