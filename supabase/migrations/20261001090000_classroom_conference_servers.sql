-- ============================================================================
-- سيرفرات الاجتماعات (Jitsi) — قائمة سيرفرات يقدر المالك/الأدمن يبدّل بينها
--
-- قبل كده كان فيه دومين واحد بس في classroom_settings.jitsi_domain. دلوقتي:
--   * جدول classroom_conference_servers: كل سيرفر (Contabo / Hetzner / مجتمعي / غيره)
--     بالدومين بتاعه و JWT App ID (اختياري).
--   * سر الـ JWT في جدول منفصل classroom_conference_server_secrets — مفيش أي عميل
--     (تطبيق أو متصفح) يقدر يقراه؛ بيقراه بس الـ Edge Function classroom-jitsi-token
--     بمفتاح الـ service role.
--   * سيرفر واحد بس "نشط" في أي وقت. التفعيل بيحدّث classroom_settings.jitsi_domain
--     وبينقل الحصص المجدولة (SCHEDULED) للسيرفر الجديد. الحصص الشغالة (LIVE) بتكمل
--     على السيرفر اللي بدأت عليه عشان محدش يتقسم في نص الحصة.
--   * صلاحية جديدة meeting_servers.manage (المالك عنده كل الصلاحيات تلقائيًا).
--
-- إصلاح جانبي: عمود jitsi_domain عليه DEFAULT 'meet.ffmuc.net'، والـ DEFAULT بيتطبق
-- قبل الـ BEFORE trigger، فالتريجر عمره ما كان بيقرا الإعداد للحصص الجديدة. التريجر
-- دلوقتي بياخد الدومين من الإعداد دايمًا — السيرفر هو اللي يقرر، مش العميل.
-- ============================================================================

create table if not exists public.classroom_conference_servers (
  id            uuid primary key default gen_random_uuid(),
  label         text not null,
  provider      text not null default 'OTHER'
                check (provider in ('CONTABO', 'HETZNER', 'COMMUNITY', 'OTHER')),
  domain        text not null default '',
  jwt_app_id    text not null default '',
  notes         text not null default '',
  is_active     boolean not null default false,
  sort_order    integer not null default 0,
  last_test_ok  boolean,
  last_tested_at timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  updated_by    uuid references public.profiles(id) on delete set null
);

create unique index if not exists classroom_conference_servers_one_active
  on public.classroom_conference_servers ((true)) where is_active;
create unique index if not exists classroom_conference_servers_domain_uq
  on public.classroom_conference_servers (lower(domain)) where domain <> '';

create table if not exists public.classroom_conference_server_secrets (
  server_id      uuid primary key references public.classroom_conference_servers(id) on delete cascade,
  jwt_app_secret text not null,
  updated_at     timestamptz not null default now()
);

alter table public.classroom_conference_servers enable row level security;
alter table public.classroom_conference_server_secrets enable row level security;

-- الأسرار: مفيش ولا policy، ومفيش صلاحيات للعملاء خالص.
revoke all on public.classroom_conference_server_secrets from anon, authenticated;

-- القراءة المباشرة للموظفين بس (القائمة الفعلية بتيجي من RPC تحت). الكتابة عبر RPC فقط.
drop policy if exists classroom_conference_servers_read on public.classroom_conference_servers;
create policy classroom_conference_servers_read on public.classroom_conference_servers
  for select to authenticated
  using (public.has_permission('meeting_servers.manage') or public.has_permission('classroom.manage'));
revoke all on public.classroom_conference_servers from anon;
revoke insert, update, delete on public.classroom_conference_servers from authenticated;

-- ---------------------------------------------------------------- helpers

create or replace function public.classroom_normalize_domain(p_raw text)
returns text
language sql immutable
as $$
  select split_part(
           regexp_replace(regexp_replace(lower(btrim(coalesce(p_raw, ''))), '^https?://', ''), '/+$', ''),
           '/', 1)
$$;

create or replace function public.classroom_can_manage_servers()
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.is_owner() or public.has_permission('meeting_servers.manage')
$$;

-- ---------------------------------------------------------------- seed

-- السيرفر الحالي (المجتمعي) يتسجّل كنشط عشان مفيش حاجة تتغير لحد ما تفعّل سيرفرك.
insert into public.classroom_conference_servers (label, provider, domain, is_active, sort_order, notes)
select 'سيرفر مجتمعي مجاني (احتياطي)', 'COMMUNITY',
       coalesce((select jitsi_domain from public.classroom_settings where id = true), 'meet.ffmuc.net'),
       true, 30, 'سيرفر عام بدون ضمان — للطوارئ بس'
where not exists (select 1 from public.classroom_conference_servers);

insert into public.classroom_conference_servers (label, provider, domain, is_active, sort_order, notes)
select 'سيرفر Contabo', 'CONTABO', '', false, 10, 'اكتب الدومين بعد ما تجهّز السيرفر'
where not exists (select 1 from public.classroom_conference_servers where provider = 'CONTABO');

insert into public.classroom_conference_servers (label, provider, domain, is_active, sort_order, notes)
select 'سيرفر Hetzner', 'HETZNER', '', false, 20, 'اكتب الدومين بعد ما تجهّز السيرفر'
where not exists (select 1 from public.classroom_conference_servers where provider = 'HETZNER');

-- ---------------------------------------------------------------- new sessions follow the active server

create or replace function public.classroom_default_jitsi_domain()
returns trigger
language plpgsql security definer set search_path to 'public'
as $$
declare v_domain text;
begin
  select jitsi_domain into v_domain from public.classroom_settings where id = true;
  if coalesce(v_domain, '') <> '' then
    new.jitsi_domain := v_domain;
  elsif new.jitsi_domain is null or new.jitsi_domain = '' or new.jitsi_domain in ('meet.jit.si', 'jitsi.riot.im') then
    new.jitsi_domain := 'meet.ffmuc.net';
  end if;
  return new;
end;
$$;

-- ---------------------------------------------------------------- RPCs

create or replace function public.classroom_conference_servers_list()
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
begin
  if not public.classroom_can_manage_servers() then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', s.id,
             'label', s.label,
             'provider', s.provider,
             'domain', s.domain,
             'jwt_app_id', s.jwt_app_id,
             'has_jwt_secret', exists (select 1 from public.classroom_conference_server_secrets x where x.server_id = s.id),
             'notes', s.notes,
             'is_active', s.is_active,
             'last_test_ok', s.last_test_ok,
             'last_tested_at', s.last_tested_at,
             'updated_at', s.updated_at,
             'live_sessions', (select count(*) from public.classroom_sessions cs
                                where cs.status = 'LIVE' and s.domain <> '' and lower(cs.jitsi_domain) = lower(s.domain)),
             'scheduled_sessions', (select count(*) from public.classroom_sessions cs
                                     where cs.status = 'SCHEDULED' and s.domain <> '' and lower(cs.jitsi_domain) = lower(s.domain))
           ) order by s.is_active desc, s.sort_order, s.created_at)
    from public.classroom_conference_servers s
  ), '[]'::jsonb);
end;
$$;

-- p_jwt_app_secret: NULL = سيب السر القديم زي ما هو، '' = امسحه، غير كده = سر جديد.
create or replace function public.classroom_conference_server_save(
  p_id uuid,
  p_label text,
  p_provider text,
  p_domain text,
  p_jwt_app_id text,
  p_jwt_app_secret text default null,
  p_notes text default ''
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_id uuid := p_id;
  v_domain text := public.classroom_normalize_domain(p_domain);
  v_app_id text := btrim(coalesce(p_jwt_app_id, ''));
  v_label text := btrim(coalesce(p_label, ''));
  v_provider text := upper(btrim(coalesce(p_provider, 'OTHER')));
  v_was_active boolean := false;
  v_old_domain text;
  v_has_secret boolean;
begin
  if not public.classroom_can_manage_servers() then raise exception 'FORBIDDEN'; end if;
  if v_label = '' then raise exception 'LABEL_REQUIRED'; end if;
  if v_provider not in ('CONTABO', 'HETZNER', 'COMMUNITY', 'OTHER') then raise exception 'INVALID_PROVIDER'; end if;
  if v_domain <> '' and v_domain !~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$' then
    raise exception 'INVALID_DOMAIN';
  end if;
  if v_domain in ('meet.jit.si', 'jitsi.riot.im') then raise exception 'DOMAIN_NOT_SUPPORTED'; end if;
  if exists (select 1 from public.classroom_conference_servers
              where v_domain <> '' and lower(domain) = v_domain and id is distinct from v_id) then
    raise exception 'DOMAIN_ALREADY_USED';
  end if;

  if v_id is null then
    insert into public.classroom_conference_servers (label, provider, domain, jwt_app_id, notes, sort_order, updated_by)
    values (v_label, v_provider, v_domain, v_app_id, btrim(coalesce(p_notes, '')),
            coalesce((select max(sort_order) + 10 from public.classroom_conference_servers), 10), auth.uid())
    returning id into v_id;
  else
    select is_active, domain into v_was_active, v_old_domain
      from public.classroom_conference_servers where id = v_id for update;
    if not found then raise exception 'NOT_FOUND'; end if;
    if v_was_active and v_domain = '' then raise exception 'ACTIVE_NEEDS_DOMAIN'; end if;
    update public.classroom_conference_servers
       set label = v_label, provider = v_provider, domain = v_domain, jwt_app_id = v_app_id,
           notes = btrim(coalesce(p_notes, '')), updated_at = now(), updated_by = auth.uid(),
           last_test_ok = case when lower(v_old_domain) = v_domain then last_test_ok end,
           last_tested_at = case when lower(v_old_domain) = v_domain then last_tested_at end
     where id = v_id;
  end if;

  if p_jwt_app_secret is not null then
    if btrim(p_jwt_app_secret) = '' then
      delete from public.classroom_conference_server_secrets where server_id = v_id;
    else
      insert into public.classroom_conference_server_secrets (server_id, jwt_app_secret, updated_at)
      values (v_id, btrim(p_jwt_app_secret), now())
      on conflict (server_id) do update set jwt_app_secret = excluded.jwt_app_secret, updated_at = now();
    end if;
  end if;

  v_has_secret := exists (select 1 from public.classroom_conference_server_secrets where server_id = v_id);
  if v_app_id <> '' and not v_has_secret then raise exception 'JWT_SECRET_REQUIRED'; end if;
  if v_app_id = '' and v_has_secret then raise exception 'JWT_APP_ID_REQUIRED'; end if;

  -- لو عدّلت دومين السيرفر النشط: الإعداد والحصص المجدولة تمشي وراه.
  if v_was_active and lower(coalesce(v_old_domain, '')) <> v_domain then
    update public.classroom_settings set jitsi_domain = v_domain, updated_at = now() where id = true;
    update public.classroom_sessions set jitsi_domain = v_domain
     where status = 'SCHEDULED' and lower(jitsi_domain) = lower(v_old_domain);
  end if;

  perform public.write_audit('meeting_server.save', 'meeting_server', v_id::text,
    jsonb_build_object('label', v_label, 'provider', v_provider, 'domain', v_domain,
                       'jwt', v_app_id <> '', 'secret_changed', p_jwt_app_secret is not null));
  return jsonb_build_object('ok', true, 'id', v_id);
end;
$$;

create or replace function public.classroom_conference_server_activate(p_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_domain text;
  v_moved integer := 0;
  v_live integer := 0;
begin
  if not public.classroom_can_manage_servers() then raise exception 'FORBIDDEN'; end if;
  select domain into v_domain from public.classroom_conference_servers where id = p_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if coalesce(v_domain, '') = '' then raise exception 'DOMAIN_REQUIRED'; end if;

  update public.classroom_conference_servers set is_active = false, updated_at = now()
   where is_active and id <> p_id;
  update public.classroom_conference_servers set is_active = true, updated_at = now(), updated_by = auth.uid()
   where id = p_id;

  insert into public.classroom_settings (id, jitsi_domain, updated_at) values (true, v_domain, now())
  on conflict (id) do update set jitsi_domain = excluded.jitsi_domain, updated_at = now();

  -- الحصص اللي لسه مبدأتش تتنقل. الشغالة دلوقتي تكمل مكانها.
  update public.classroom_sessions set jitsi_domain = v_domain
   where status = 'SCHEDULED' and lower(jitsi_domain) <> lower(v_domain);
  get diagnostics v_moved = row_count;
  select count(*) into v_live from public.classroom_sessions
   where status = 'LIVE' and lower(jitsi_domain) <> lower(v_domain);

  perform public.write_audit('meeting_server.activate', 'meeting_server', p_id::text,
    jsonb_build_object('domain', v_domain, 'moved_scheduled', v_moved, 'live_left', v_live));
  return jsonb_build_object('ok', true, 'domain', v_domain, 'moved_scheduled', v_moved, 'live_left', v_live);
end;
$$;

create or replace function public.classroom_conference_server_delete(p_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_active boolean; v_label text;
begin
  if not public.classroom_can_manage_servers() then raise exception 'FORBIDDEN'; end if;
  select is_active, label into v_active, v_label from public.classroom_conference_servers where id = p_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_active then raise exception 'CANNOT_DELETE_ACTIVE'; end if;
  delete from public.classroom_conference_servers where id = p_id;
  perform public.write_audit('meeting_server.delete', 'meeting_server', p_id::text, jsonb_build_object('label', v_label));
  return jsonb_build_object('ok', true);
end;
$$;

-- نتيجة "اختبار الاتصال" اللي بيعمله التطبيق من جهاز المالك/الأدمن.
create or replace function public.classroom_conference_server_mark_test(p_id uuid, p_ok boolean)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.classroom_can_manage_servers() then raise exception 'FORBIDDEN'; end if;
  update public.classroom_conference_servers
     set last_test_ok = p_ok, last_tested_at = now()
   where id = p_id;
  return jsonb_build_object('ok', true);
end;
$$;

revoke all on function public.classroom_conference_servers_list() from public, anon;
revoke all on function public.classroom_conference_server_save(uuid, text, text, text, text, text, text) from public, anon;
revoke all on function public.classroom_conference_server_activate(uuid) from public, anon;
revoke all on function public.classroom_conference_server_delete(uuid) from public, anon;
revoke all on function public.classroom_conference_server_mark_test(uuid, boolean) from public, anon;
grant execute on function public.classroom_conference_servers_list() to authenticated;
grant execute on function public.classroom_conference_server_save(uuid, text, text, text, text, text, text) to authenticated;
grant execute on function public.classroom_conference_server_activate(uuid) to authenticated;
grant execute on function public.classroom_conference_server_delete(uuid) to authenticated;
grant execute on function public.classroom_conference_server_mark_test(uuid, boolean) to authenticated;
