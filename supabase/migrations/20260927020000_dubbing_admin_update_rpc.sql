-- Owner/admin write path for dubbing_settings, mirroring classroom_set_live_translation's
-- shape: one RPC, one grant, no direct table writes from the client.
create or replace function public.dubbing_update_settings(
  p_is_enabled boolean, p_price_per_minute numeric, p_currency text,
  p_min_billable_minutes numeric, p_free_trial_minutes_total numeric,
  p_max_source_minutes int, p_dub_server_url text)
returns void language plpgsql security definer set search_path to 'public' as $$
begin
  if not (public.is_owner() or public.has_permission('finance.manage')) then
    raise exception 'FORBIDDEN';
  end if;
  update public.dubbing_settings set
    is_enabled = p_is_enabled,
    price_per_minute = p_price_per_minute,
    currency = upper(p_currency),
    min_billable_minutes = p_min_billable_minutes,
    free_trial_minutes_total = p_free_trial_minutes_total,
    max_source_minutes = p_max_source_minutes,
    dub_server_url = trim(p_dub_server_url),
    updated_at = now()
  where id = 1;
end $$;
revoke all on function public.dubbing_update_settings(boolean, numeric, text, numeric, numeric, int, text) from public, anon;
grant execute on function public.dubbing_update_settings(boolean, numeric, text, numeric, numeric, int, text) to authenticated;
