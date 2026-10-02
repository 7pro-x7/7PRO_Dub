-- AI Tutor character library with optional speaker reference audio for self-hosted OpenVoice.
-- The sample is private; only the app can mint short-lived signed URLs for active characters.

create table if not exists public.ai_tutor_characters (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null,
  image_url text not null,
  voice text not null default 'male',
  mouth_x real not null default 0.5,
  mouth_y real not null default 0.62,
  mouth_h real not null default 0.05,
  eye_y real not null default 0.35,
  voice_mode text not null default 'system' check (voice_mode in ('system', 'clone')),
  voice_sample_path text,
  sort_order integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint ai_tutor_character_clone_sample check (
    (voice_mode = 'system' and voice_sample_path is null)
    or (voice_mode = 'clone' and voice_sample_path is not null)
  )
);

-- Older environments may already have created the character table outside this migration history.
alter table public.ai_tutor_characters
  add column if not exists owner_id uuid references auth.users(id) on delete cascade,
  add column if not exists voice_mode text not null default 'system',
  add column if not exists voice_sample_path text,
  add column if not exists created_at timestamptz not null default now();

-- Legacy installations need the same default for new character rows as a fresh table.
alter table public.ai_tutor_characters alter column owner_id set default auth.uid();

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.ai_tutor_characters'::regclass
      and conname = 'ai_tutor_character_clone_sample'
  ) then
    alter table public.ai_tutor_characters
      add constraint ai_tutor_character_clone_sample check (
        (voice_mode = 'system' and voice_sample_path is null)
        or (voice_mode = 'clone' and voice_sample_path is not null)
      );
  end if;
end $$;

alter table public.ai_tutor_characters enable row level security;
grant select, insert, update, delete on public.ai_tutor_characters to authenticated;

drop policy if exists ai_tutor_characters_read on public.ai_tutor_characters;
create policy ai_tutor_characters_read on public.ai_tutor_characters
  for select to authenticated
  using (
    active
    or owner_id = auth.uid()
    or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN'))
  );

drop policy if exists ai_tutor_characters_manage on public.ai_tutor_characters;
create policy ai_tutor_characters_manage on public.ai_tutor_characters
  for all to authenticated
  using (
    owner_id = auth.uid()
    or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN'))
  )
  with check (
    (owner_id = auth.uid() or owner_id is null)
    and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN'))
  );

-- Character artwork remains publicly viewable, matching the URLs already stored by the app.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('tutor-characters', 'tutor-characters', true, 5242880, array['image/png'])
on conflict (id) do nothing;

-- Speaker recordings are not public. Paths begin with the uploading Owner/Admin's user id.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('tutor-voice-samples', 'tutor-voice-samples', false, 1048576, array['audio/wav','audio/x-wav'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists tutor_voice_sample_owner_upload on storage.objects;
create policy tutor_voice_sample_owner_upload on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'tutor-voice-samples'
    and (storage.foldername(name))[1] = auth.uid()::text
    and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN'))
  );

drop policy if exists tutor_voice_sample_short_link_read on storage.objects;
create policy tutor_voice_sample_short_link_read on storage.objects
  for select to authenticated
  using (
    bucket_id = 'tutor-voice-samples'
    and (
      exists (
        select 1 from public.ai_tutor_characters c
        where c.voice_sample_path = storage.objects.name and c.active and c.voice_mode = 'clone'
      )
      or exists (
        select 1 from public.ai_tutor_characters c
        where c.voice_sample_path = storage.objects.name and c.owner_id = auth.uid()
      )
    )
  );

drop policy if exists tutor_voice_sample_owner_delete on storage.objects;
create policy tutor_voice_sample_owner_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'tutor-voice-samples'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- Artwork cleanup is limited to the character owner/staff; public users only read the bucket.
drop policy if exists tutor_character_artwork_public_read on storage.objects;
create policy tutor_character_artwork_public_read on storage.objects
  for select to public using (bucket_id = 'tutor-characters');

drop policy if exists tutor_character_artwork_staff_write on storage.objects;
create policy tutor_character_artwork_staff_write on storage.objects
  for all to authenticated
  using (
    bucket_id = 'tutor-characters'
    and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN'))
  )
  with check (
    bucket_id = 'tutor-characters'
    and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN'))
  );
