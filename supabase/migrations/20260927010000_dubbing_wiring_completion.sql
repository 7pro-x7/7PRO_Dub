-- Completes the dubbing feature wiring: exposes the dubbing-server URL to the app the same
-- way `ai_tutor_status()` exposes the AI Tutor worker URL, and provisions the two storage
-- buckets translation-server/DUBBING-WIRING.md calls for (both were missing).

alter table public.dubbing_settings add column if not exists dub_server_url text not null default '';

grant update (is_enabled, price_per_minute, currency, min_billable_minutes,
              free_trial_minutes_total, max_source_minutes, dub_server_url, updated_at)
  on public.dubbing_settings to authenticated;

create or replace function public.dubbing_status()
returns jsonb language sql stable security definer set search_path to 'public' as $$
  select jsonb_build_object(
    'is_enabled', is_enabled,
    'price_per_minute', price_per_minute,
    'currency', currency,
    'min_billable_minutes', min_billable_minutes,
    'free_trial_minutes_total', free_trial_minutes_total,
    'max_source_minutes', max_source_minutes,
    'dub_server_url', dub_server_url
  ) from public.dubbing_settings where id = 1;
$$;
revoke all on function public.dubbing_status() from public, anon;
grant execute on function public.dubbing_status() to authenticated;

-- Storage buckets used by DubbingRepository (uploadLocalVideo -> dubbing_uploads,
-- signedOutputUrl -> dubbing) and by the dubbing-server (service_role writes the output).
insert into storage.buckets (id, name, public)
values ('dubbing_uploads', 'dubbing_uploads', false)
on conflict (id) do nothing;

insert into storage.buckets (id, name, public)
values ('dubbing', 'dubbing', false)
on conflict (id) do nothing;

drop policy if exists dubbing_uploads_own_rw on storage.objects;
create policy dubbing_uploads_own_rw on storage.objects
  for all to authenticated
  using (bucket_id = 'dubbing_uploads' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'dubbing_uploads' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists dubbing_output_own_read on storage.objects;
create policy dubbing_output_own_read on storage.objects
  for select to authenticated
  using (bucket_id = 'dubbing' and (storage.foldername(name))[1] = auth.uid()::text);
