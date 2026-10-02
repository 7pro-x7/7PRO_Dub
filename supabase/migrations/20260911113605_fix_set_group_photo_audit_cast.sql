-- The audit_logs.actor_role column is the app_role enum, not text — a bare
-- COALESCE(v_role, 'TEACHER') insert fails with "column is of type app_role
-- but expression is of type text" (the same latent issue delete_course()
-- papers over with an exception-swallowing block). Cast explicitly here, and
-- keep the exception-safe wrapper too: a logging hiccup must never roll back
-- the photo update itself.
create or replace function public.set_group_photo(
  p_group_id uuid,
  p_photo_url text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role    text;
  v_teacher uuid;
begin
  select role::text into v_role from public.profiles where id = auth.uid();
  select teacher_id into v_teacher from public.teacher_groups where id = p_group_id;

  if v_teacher is null then
    raise exception 'GROUP_NOT_FOUND';
  end if;

  if auth.uid() <> v_teacher and coalesce(v_role, '') not in ('OWNER', 'ADMIN') then
    raise exception 'FORBIDDEN';
  end if;

  update public.teacher_groups
  set photo_url = nullif(trim(p_photo_url), ''),
      updated_at = now()
  where id = p_group_id;

  begin
    insert into public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    values (
      auth.uid(),
      'SET_GROUP_PHOTO',
      'teacher_group',
      p_group_id,
      coalesce(v_role, 'TEACHER')::app_role,
      jsonb_build_object('teacher_id', v_teacher, 'photo_url', p_photo_url)
    );
  exception when others then
    null;
  end;
end;
$$;

revoke all on function public.set_group_photo(uuid, text) from public;
revoke all on function public.set_group_photo(uuid, text) from anon;
grant execute on function public.set_group_photo(uuid, text) to authenticated;
;
