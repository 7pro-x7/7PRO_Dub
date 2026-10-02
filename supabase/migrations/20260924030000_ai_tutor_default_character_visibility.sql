-- Owner control over the two built-in tutors (Mr. Adam = 'male', Ms. Sara = 'female').
-- Everyone can read which ones are visible; only OWNER / ADMIN accounts can change it.
-- A missing row means visible, so nothing changes for existing installs.

create table if not exists public.ai_tutor_default_characters (
  voice      text primary key check (voice in ('male', 'female')),
  visible    boolean not null default true,
  updated_at timestamptz not null default now()
);

insert into public.ai_tutor_default_characters (voice, visible)
values ('male', true), ('female', true)
on conflict (voice) do nothing;

alter table public.ai_tutor_default_characters enable row level security;
grant select on public.ai_tutor_default_characters to anon, authenticated;
grant insert, update on public.ai_tutor_default_characters to authenticated;

drop policy if exists ai_tutor_default_characters_read on public.ai_tutor_default_characters;
create policy ai_tutor_default_characters_read on public.ai_tutor_default_characters
  for select to anon, authenticated
  using (true);

drop policy if exists ai_tutor_default_characters_manage on public.ai_tutor_default_characters;
create policy ai_tutor_default_characters_manage on public.ai_tutor_default_characters
  for all to authenticated
  using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN')))
  with check (exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('OWNER', 'ADMIN')));
