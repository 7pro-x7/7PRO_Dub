-- Per-account notification preferences.
-- Missing rows intentionally mean enabled, so existing accounts keep their current behavior.
create table if not exists public.notification_kind_preferences (
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null,
  enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key (user_id, kind),
  constraint notification_kind_preferences_kind_nonempty check (length(trim(kind)) > 0)
);

create index if not exists notification_preferences_user_idx
  on public.notification_kind_preferences (user_id);

alter table public.notification_kind_preferences enable row level security;

 drop policy if exists notification_kind_preferences_own_select on public.notification_kind_preferences;
create policy notification_kind_preferences_own_select
  on public.notification_kind_preferences for select
  using (user_id = auth.uid());

 drop policy if exists notification_kind_preferences_own_insert on public.notification_kind_preferences;
create policy notification_kind_preferences_own_insert
  on public.notification_kind_preferences for insert
  with check (user_id = auth.uid());

 drop policy if exists notification_kind_preferences_own_update on public.notification_kind_preferences;
create policy notification_kind_preferences_own_update
  on public.notification_kind_preferences for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Keep writes limited to the signed-in user's own preference row.
revoke all on public.notification_kind_preferences from anon;
grant select, insert, update on public.notification_kind_preferences to authenticated;

-- Shared helper for push/database code. Unknown kinds remain enabled by default.
create or replace function public.notification_preference_enabled(p_user_id uuid, p_kind text)
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $function$
  select coalesce(
    (
      select np.enabled
      from public.notification_kind_preferences np
      where np.user_id = p_user_id
        and upper(np.kind) = upper(p_kind)
      limit 1
    ),
    true
  );
$function$;

revoke all on function public.notification_preference_enabled(uuid, text) from public;
grant execute on function public.notification_preference_enabled(uuid, text) to authenticated, service_role;

-- Normalize route-bearing notification payloads for older notification writers. New writers can
-- still provide an explicit `route`; this only fills a safe route when the payload carries a known
-- entity id. The Android client also applies the same fallback for rows created before this change.
create or replace function public.notification_route_from_data(p_kind text, p_data jsonb)
returns text
language sql
immutable
as $function$
  select coalesce(
    nullif(p_data ->> 'route', ''),
    case
      when nullif(p_data ->> 'classroom_session_id', '') is not null
        then 'classroom/lobby/' || (p_data ->> 'classroom_session_id')
      when nullif(p_data ->> 'course_id', '') is not null
        then 'course/' || (p_data ->> 'course_id')
      when nullif(p_data ->> 'attempt_id', '') is not null
        then 'test-result/' || (p_data ->> 'attempt_id')
      when nullif(p_data ->> 'subscription_id', '') is not null
        then 'booking/pay/' || (p_data ->> 'subscription_id')
      when nullif(p_data ->> 'request_id', '') is not null
        then 'admin/approvals'
      else 'notifications'
    end
  );
$function$;

grant execute on function public.notification_route_from_data(text, jsonb) to authenticated, service_role;

-- Ensure the standard notification table exposes its existing app payload columns to the client
-- without changing existing rows or writers. This is deliberately conditional for deployments
-- where the table was created by the production baseline rather than the local migration set.
do $$
begin
  if to_regclass('public.notifications') is not null then
    execute 'alter table public.notifications add column if not exists data jsonb';
  end if;
end
$$;
