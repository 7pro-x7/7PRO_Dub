-- ============================================================================
-- Group photo: the owning teacher, and the owner/admin on their behalf, can
-- set or clear a picture for a teacher_groups row.
--
-- name/level stay teacher-only (existing teacher_groups_self_access policy).
-- price/is_open/capacity/schedule stay owner/admin-only (set_group_booking).
-- photo_url is the one field the owning teacher AND staff may both write, so
-- it goes through its own SECURITY DEFINER door rather than loosening the
-- table's RLS in either direction.
-- ============================================================================

alter table public.teacher_groups
  add column if not exists photo_url text;

comment on column public.teacher_groups.photo_url is
  'Public URL of the group picture. Settable by the owning teacher or by owner/admin staff, via set_group_photo().';

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

  insert into public.audit_logs (action, target_type, target_id, actor_role, metadata)
  values (
    'SET_GROUP_PHOTO',
    'teacher_group',
    p_group_id,
    coalesce(v_role, 'TEACHER'),
    jsonb_build_object('teacher_id', v_teacher, 'photo_url', p_photo_url)
  );
end;
$$;

revoke all on function public.set_group_photo(uuid, text) from public;
revoke all on function public.set_group_photo(uuid, text) from anon;
grant execute on function public.set_group_photo(uuid, text) to authenticated;
;
