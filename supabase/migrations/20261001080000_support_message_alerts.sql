-- تنبيه صوتي لكل رسالة في دعم العملاء، في الاتجاهين:
--  1) كل رسالة عميل تنبّه المسؤول عن المحادثة (أو الفريق المتاح لو مفيش مسؤول).
--  2) كل رد من الدعم ينبّه العميل (قبل كده كان أول رد بس لحد ما يقرا).
--  3) الدفع الفوري بقى يشمل إشعارات SUPPORT للعميل مش للموظفين بس.
-- ملاحظة: دالة alert-push (Edge Function) مش جوه المشروع؛ لازم تقبل مستلم غير موظف.

create or replace function public.support_send(p_chat uuid, p_body text, p_note boolean default false)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_body text := btrim(coalesce(p_body, ''));
  c public.support_chats;
  v_is_user boolean;
  v_is_staff boolean;
  v_recent int;
  m public.support_chat_messages;
  v_targets uuid[];
  v_name text;
  s uuid;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if v_body = '' then raise exception 'MESSAGE_EMPTY'; end if;
  if char_length(v_body) > 2000 then raise exception 'MESSAGE_TOO_LONG'; end if;

  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  v_is_user := c.user_id = v_uid;
  v_is_staff := not v_is_user and public.has_permission('support.manage');
  if not v_is_user and not v_is_staff then raise exception 'FORBIDDEN'; end if;
  if c.status = 'CLOSED' then raise exception 'CHAT_CLOSED'; end if;
  if coalesce(p_note, false) and not v_is_staff then raise exception 'FORBIDDEN'; end if;

  select count(*) into v_recent from public.support_chat_messages
   where sender_id = v_uid and created_at > now() - interval '1 minute';
  if v_recent >= 20 then raise exception 'TOO_MANY_MESSAGES'; end if;

  if coalesce(p_note, false) then
    m := public._support_post(c.id, v_uid, 'NOTE', true, v_body);
    return to_jsonb(m);
  end if;

  if v_is_staff then
    if c.assigned_to is null then
      update public.support_chats set assigned_to = v_uid, status = 'OPEN' where id = c.id;
      perform public._support_post(c.id, v_uid, 'EVENT', true, 'CLAIMED',
        jsonb_build_object('name', public._support_first_name(v_uid)));
    elsif c.assigned_to <> v_uid then
      raise exception 'ASSIGNED_TO_OTHER';
    end if;
    m := public._support_post(c.id, v_uid, 'TEXT', true, v_body);
    -- إشعار (وصوت على الموبايل) لكل رد من الدعم
    perform public.push_notification(c.user_id, 'SUPPORT',
      'رد جديد من الدعم • New reply from support',
      public._support_first_name(v_uid) || ': ' || public._support_preview(v_body),
      jsonb_build_object('route', 'support/chat/' || c.id, 'support_chat_id', c.id));
  else
    m := public._support_post(c.id, v_uid, 'TEXT', false, v_body);
    -- إشعار لكل رسالة من العميل: للمسؤول عن المحادثة، ولو مفيش حد استلمها للفريق المتاح
    if c.assigned_to is not null then
      v_targets := array[c.assigned_to];
    else
      select array_agg(id) into v_targets from public._support_staff_ids() id
       where id <> v_uid and coalesce((select available from public.support_availability a where a.user_id = id), true);
      if v_targets is null then
        select array_agg(id) into v_targets from public._support_staff_ids() id where id <> v_uid;
      end if;
    end if;
    select coalesce(nullif(btrim(full_name), ''), email) into v_name from public.profiles where id = v_uid;
    foreach s in array coalesce(v_targets, '{}') loop
      perform public.push_notification(s, 'SUPPORT',
        'رسالة جديدة في الدعم • New support message',
        coalesce(v_name, '') || ': ' || public._support_preview(v_body),
        jsonb_build_object('route', 'admin/support/chat/' || c.id, 'support_chat_id', c.id));
    end loop;
  end if;
  return to_jsonb(m);
end $$;

-- الدفع الفوري (FCM) كان للمالك/الأدمن/المعلم بس. رد الدعم للعميل لازم يوصله هو كمان.
create or replace function public.notify_staff_push()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'vault'
as $function$
declare
  v_role   text;
  v_secret text;
begin
  select role::text into v_role from public.profiles where id = new.user_id;
  if v_role is null then
    return new;
  end if;
  if v_role not in ('OWNER', 'ADMIN', 'TEACHER') and new.kind <> 'SUPPORT' then
    return new;
  end if;

  if not public.notification_preference_enabled(new.user_id, new.kind) then
    return new;
  end if;

  select decrypted_secret into v_secret
  from vault.decrypted_secrets where name = 'alert_push_hook_secret' limit 1;
  if v_secret is null then
    return new;
  end if;

  perform net.http_post(
    url := 'https://usbfrqbjtpvnkmmcusdi.supabase.co/functions/v1/alert-push',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-7pro-hook', v_secret
    ),
    body := jsonb_build_object('notification_id', new.id),
    timeout_milliseconds := 5000
  );

  return new;
exception when others then
  return new;
end;
$function$;
