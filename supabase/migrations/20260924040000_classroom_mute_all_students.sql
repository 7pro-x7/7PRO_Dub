-- One-shot "mute everyone now" / "let everyone speak" for the teacher.
-- Unlike the retired session-wide switch (see 20260917183151), this only changes the students
-- who are in the class right now: nobody joining later inherits a lock, and each student can
-- still be unlocked individually with classroom_set_participant_media.
create or replace function public.classroom_set_students_mic_locked(p_session_id uuid, p_locked boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not classroom_can_manage(p_session_id) then
    raise exception 'FORBIDDEN';
  end if;

  update classroom_participants
  set mic_locked = p_locked
  where session_id = p_session_id
    and role_in_session = 'STUDENT'
    and status = 'JOINED'
    and mic_locked is distinct from p_locked;
end;
$$;

revoke all on function public.classroom_set_students_mic_locked(uuid, boolean) from public, anon;
grant execute on function public.classroom_set_students_mic_locked(uuid, boolean) to authenticated;
