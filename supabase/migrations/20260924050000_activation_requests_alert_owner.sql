-- When a teacher presses "Activate" (a single student or a whole group) the owner/admins now get
-- a loud staff alert (kind ACTIVATION_REQUEST). Each owner can switch it off in
-- Notification Settings; the existing per-kind preference already governs both the in-app row
-- and the sound push. A single-student activation also becomes a reviewable approval request
-- (ACTIVATE_SUBSCRIPTION, which process_approval_request already knows how to approve/reject).

create or replace function public.activate_subscription(p_subscription_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_role text;
  v_sub record;
  v_request_id uuid;
  v_teacher_name text;
begin
  select role into v_role from public.profiles where id = auth.uid();
  if v_role is null then
    raise exception 'PROFILE_NOT_FOUND';
  end if;

  select * into v_sub from public.teacher_subscriptions where id = p_subscription_id;
  if not found then
    raise exception 'SUBSCRIPTION_NOT_FOUND';
  end if;

  if v_role in ('OWNER', 'ADMIN') then
    update public.teacher_subscriptions
    set approval_status = 'APPROVED', reviewed_by = auth.uid(), reviewed_at = now()
    where id = p_subscription_id and approval_status != 'APPROVED';

    -- Activated directly: the teacher's pending request (if any) is settled too.
    update public.approval_requests
    set status = 'APPROVED', reviewed_by = auth.uid(), reviewed_at = now()
    where target_id = p_subscription_id and action_type = 'ACTIVATE_SUBSCRIPTION' and status = 'PENDING';
  elsif v_sub.teacher_id = auth.uid() then
    update public.teacher_subscriptions
    set approval_status = 'PENDING', reviewed_by = null, reviewed_at = null, review_note = null
    where id = p_subscription_id and approval_status = 'REJECTED';

    if v_sub.approval_status in ('PENDING', 'REJECTED')
       and not exists (
         select 1 from public.approval_requests
         where target_id = p_subscription_id and action_type = 'ACTIVATE_SUBSCRIPTION' and status = 'PENDING'
       ) then
      insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
      values (
        auth.uid(), 'ACTIVATE_SUBSCRIPTION', 'subscription', p_subscription_id,
        jsonb_build_object(
          'student_name', v_sub.student_name, 'group_name', v_sub.group_name,
          'parent_name', v_sub.parent_name, 'monthly_amount', v_sub.monthly_amount,
          'currency', v_sub.currency
        )
      )
      returning id into v_request_id;

      select coalesce(full_name, '') into v_teacher_name from public.profiles where id = auth.uid();
      perform public.notify_staff(
        'ACTIVATION_REQUEST',
        'طلب تفعيل طالب',
        trim(v_teacher_name || ' طلب تفعيل ' || coalesce(v_sub.student_name, '') || ' — ' || coalesce(v_sub.group_name, '') || '.'),
        jsonb_build_object('request_id', v_request_id, 'teacher_id', auth.uid(),
                           'route', 'admin/approvals?teacherId=' || auth.uid()::text)
      );
    end if;
  else
    raise exception 'FORBIDDEN';
  end if;
end;
$function$;

create or replace function public.activate_group(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_caller_role text;
  v_group record;
  v_request_id uuid;
  v_member_count bigint;
  v_teacher_name text;
begin
  select role into v_caller_role from public.profiles where id = auth.uid();
  if v_caller_role is null then
    raise exception 'PROFILE_NOT_FOUND';
  end if;

  if v_caller_role not in ('TEACHER', 'ADMIN', 'OWNER') then
    raise exception 'FORBIDDEN';
  end if;

  select * into v_group from public.teacher_groups where id = p_group_id;
  if not found then
    raise exception 'GROUP_NOT_FOUND';
  end if;

  if v_group.teacher_id <> auth.uid() and v_caller_role not in ('ADMIN', 'OWNER') then
    raise exception 'FORBIDDEN';
  end if;

  if v_group.approval_status = 'APPROVED' then
    return;
  end if;
  if v_group.approval_status = 'PENDING' then
    raise exception 'GROUP_ALREADY_PENDING';
  end if;

  select count(*) into v_member_count
  from public.teacher_subscriptions
  where teacher_id = v_group.teacher_id
    and lower(group_name) = lower(v_group.name);

  if v_caller_role in ('ADMIN', 'OWNER') then
    update public.teacher_groups
    set approval_status = 'APPROVED', updated_at = now()
    where id = p_group_id;

    update public.teacher_subscriptions
    set approval_status = 'APPROVED', updated_at = now()
    where teacher_id = v_group.teacher_id
      and lower(group_name) = lower(v_group.name)
      and status != 'PAUSED'
      and approval_status != 'APPROVED';
  else
    update public.teacher_groups
    set approval_status = 'PENDING', updated_at = now()
    where id = p_group_id;

    insert into public.approval_requests (teacher_id, action_type, target_type, target_id, request_data)
    values (
      auth.uid(), 'ACTIVATE_GROUP', 'group', p_group_id,
      jsonb_build_object('name', v_group.name, 'level', v_group.level, 'member_count', v_member_count)
    )
    returning id into v_request_id;

    select coalesce(full_name, '') into v_teacher_name from public.profiles where id = auth.uid();
    perform public.notify_staff(
      'ACTIVATION_REQUEST',
      'طلب تفعيل مجموعة',
      trim(v_teacher_name || ' طلب تفعيل مجموعة ' || v_group.name || ' (' || v_member_count || ' طالب).'),
      jsonb_build_object('request_id', v_request_id, 'teacher_id', auth.uid(),
                         'route', 'admin/approvals?teacherId=' || auth.uid()::text)
    );
  end if;

  begin
    insert into public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
    values (
      auth.uid(),
      case when v_caller_role in ('ADMIN', 'OWNER') then 'GROUP_ACTIVATED' else 'GROUP_ACTIVATION_REQUESTED' end,
      'group', p_group_id,
      coalesce(v_caller_role, 'TEACHER'),
      jsonb_build_object('request_id', v_request_id, 'teacher_id', v_group.teacher_id, 'name', v_group.name)
    );
  exception when others then
    null;
  end;
end;
$function$;
