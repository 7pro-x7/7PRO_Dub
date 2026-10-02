-- A student a teacher adds to a group used to sit silently as "PENDING" until the teacher also
-- pressed "Activate". Only that second press created the approval request and alerted the owner,
-- so a freshly added student could wait unseen.
--
-- Now adding the student IS the request: the moment a TEACHER inserts a member who lands PENDING,
-- an ACTIVATE_SUBSCRIPTION approval request is opened (it shows on the owner/admin Approvals page)
-- and the owner/admins get an ACTIVATION_REQUEST alert (in-app row + push; each person's own
-- Notification Settings toggle still applies). Pressing "Activate" afterwards is a harmless no-op:
-- activate_subscription() already skips a subscription that has an open request.
--
-- Left alone on purpose:
--   * members added by owner/admin (they are the approvers),
--   * students' own self-service requests (they already create an ADD_SUBSCRIPTION request),
--   * members of a group that is not yet approved (activating the group is the request for them;
--     a per-student request would be left stale when the group is approved).

create or replace function public._request_approval_for_teacher_added_member()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_role text;
  v_group_status text;
  v_request_id uuid;
  v_teacher_name text;
begin
  if new.approval_status is distinct from 'PENDING' then return new; end if;
  -- Only the teacher adding their own member: not staff, not a student's self-service request.
  if auth.uid() is null or new.teacher_id is distinct from auth.uid() then return new; end if;

  select role::text into v_role from public.profiles where id = auth.uid();
  if v_role is distinct from 'TEACHER' then return new; end if;

  select approval_status into v_group_status
    from public.teacher_groups
   where teacher_id = new.teacher_id and lower(name) = lower(new.group_name)
   limit 1;
  -- A group that exists but is not approved yet is requested as a whole.
  if v_group_status is not null and v_group_status <> 'APPROVED' then return new; end if;

  if exists (
    select 1 from public.approval_requests
     where target_type = 'subscription' and target_id = new.id and status = 'PENDING'
  ) then
    return new;
  end if;

  -- Never let the alert machinery undo the teacher adding a student.
  begin
    insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
    values (
      new.teacher_id, 'ACTIVATE_SUBSCRIPTION', 'subscription', new.id,
      jsonb_build_object(
        'student_name', new.student_name, 'group_name', new.group_name,
        'parent_name', new.parent_name, 'monthly_amount', new.monthly_amount,
        'currency', new.currency
      )
    )
    returning id into v_request_id;

    select coalesce(full_name, '') into v_teacher_name from public.profiles where id = new.teacher_id;
    perform public.notify_staff(
      'ACTIVATION_REQUEST',
      'طالب جديد بانتظار الموافقة',
      trim(v_teacher_name || ' أضاف ' || coalesce(new.student_name, '') || ' — ' || coalesce(new.group_name, '') || '.'),
      jsonb_build_object('request_id', v_request_id, 'teacher_id', new.teacher_id,
                         'route', 'admin/approvals?teacherId=' || new.teacher_id::text)
    );
  exception when others then
    null;
  end;

  return new;
end;
$function$;

revoke all on function public._request_approval_for_teacher_added_member() from public, anon, authenticated;

drop trigger if exists trg_request_approval_for_teacher_added_member on public.teacher_subscriptions;
create trigger trg_request_approval_for_teacher_added_member
  after insert on public.teacher_subscriptions
  for each row
  execute function public._request_approval_for_teacher_added_member();

-- Students already waiting without any open request (like the ones added before this change)
-- are put in the queue too, so nothing stays invisible. One summary alert, not one per student.
do $backfill$
declare
  v_added int;
begin
  with ins as (
    insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
    select s.teacher_id, 'ACTIVATE_SUBSCRIPTION', 'subscription', s.id,
           jsonb_build_object(
             'student_name', s.student_name, 'group_name', s.group_name,
             'parent_name', s.parent_name, 'monthly_amount', s.monthly_amount,
             'currency', s.currency)
      from public.teacher_subscriptions s
     where s.approval_status = 'PENDING'
       and coalesce(s.status, '') <> 'PAUSED'
       and not exists (
         select 1 from public.teacher_groups g
          where g.teacher_id = s.teacher_id and lower(g.name) = lower(s.group_name)
            and g.approval_status <> 'APPROVED')
       and not exists (
         select 1 from public.approval_requests a
          where a.target_type = 'subscription' and a.target_id = s.id and a.status = 'PENDING')
    returning 1
  )
  select count(*) into v_added from ins;

  if v_added > 0 then
    perform public.notify_staff(
      'ACTIVATION_REQUEST',
      'طلاب بانتظار الموافقة',
      v_added || ' طالب أضافهم المعلمون وبانتظار موافقتك.',
      jsonb_build_object('route', 'admin/approvals')
    );
  end if;
exception when others then
  null;
end
$backfill$;

notify pgrst, 'reload schema';
