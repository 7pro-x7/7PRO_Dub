-- The short description shown under a teacher's name on the booking/showcase
-- cards ("English Teacher for Kids" etc). Until now this could only be set
-- once, at teacher-creation time — there was no way to change it afterwards.
-- Same shape and permission check as set_teacher_booking_available.
create or replace function public.admin_set_teacher_headline(p_teacher uuid, p_headline text)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if not (public.is_staff() or public.has_permission('teachers.manage')) then
    raise exception 'FORBIDDEN';
  end if;

  update public.teacher_profiles
     set headline = nullif(trim(p_headline), ''), updated_at = now()
   where id = p_teacher;
  if not found then raise exception 'TEACHER_NOT_FOUND'; end if;

  perform public.write_audit(
    'teachers.headline', 'teacher', p_teacher::text,
    jsonb_build_object('headline', p_headline)
  );
end;
$function$;

revoke all on function public.admin_set_teacher_headline(uuid, text) from public;
revoke all on function public.admin_set_teacher_headline(uuid, text) from anon;
grant execute on function public.admin_set_teacher_headline(uuid, text) to authenticated;;
