-- AI Tutor: a few durable facts about each student (hobbies, goals, names they mention) so the
-- tutor can pick the conversation up where it left off instead of starting from zero every call.
-- Reached only through the functions below (no table policies on purpose). Signed-in students
-- only: guest / anonymous sessions never get memory. Capped at 800 characters per student.

create table if not exists public.ai_tutor_memory (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  facts      text not null default '',
  updated_at timestamptz not null default now()
);
alter table public.ai_tutor_memory enable row level security;

create or replace function public.ai_tutor_get_memory()
returns text language plpgsql stable security definer set search_path to 'public' as $$
declare
  v_uid uuid := auth.uid();
  v_facts text;
begin
  if v_uid is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    return null;
  end if;
  select facts into v_facts from public.ai_tutor_memory where user_id = v_uid;
  return nullif(v_facts, '');
end $$;

create or replace function public.ai_tutor_remember(p_fact text)
returns void language plpgsql security definer set search_path to 'public' as $$
declare
  v_uid uuid := auth.uid();
  v_fact text := left(btrim(coalesce(p_fact, '')), 160);
  v_old text;
  v_new text;
begin
  if v_uid is null or v_fact = '' or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    return;
  end if;
  select facts into v_old from public.ai_tutor_memory where user_id = v_uid;
  v_old := coalesce(v_old, '');
  if v_old <> '' and position(lower(v_fact) in lower(v_old)) > 0 then
    return;
  end if;
  v_new := case when v_old = '' then v_fact else v_old || '; ' || v_fact end;
  if length(v_new) > 800 then
    v_new := right(v_new, 800);
    if position('; ' in v_new) > 0 then
      v_new := substr(v_new, position('; ' in v_new) + 2);
    end if;
  end if;
  insert into public.ai_tutor_memory(user_id, facts, updated_at)
  values (v_uid, v_new, now())
  on conflict (user_id) do update set facts = excluded.facts, updated_at = now();
end $$;

create or replace function public.ai_tutor_forget_memory()
returns void language plpgsql security definer set search_path to 'public' as $$
begin
  delete from public.ai_tutor_memory where user_id = auth.uid();
end $$;

revoke all on function public.ai_tutor_get_memory() from public, anon;
revoke all on function public.ai_tutor_remember(text) from public, anon;
revoke all on function public.ai_tutor_forget_memory() from public, anon;
grant execute on function public.ai_tutor_get_memory() to authenticated;
grant execute on function public.ai_tutor_remember(text) to authenticated;
grant execute on function public.ai_tutor_forget_memory() to authenticated;
