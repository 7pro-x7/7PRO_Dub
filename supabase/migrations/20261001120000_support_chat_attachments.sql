-- ============================================================================
-- Support chat: photos and files (camera, gallery, "attach file" in the new composer).
--
--  * support_chat_messages.kind gains IMAGE and FILE. The message body is a readable stand-in
--    ("📷 صورة" / the file name) so previews, notifications and older app versions still show
--    something sensible; the file itself is described in meta {path, name, mime, size}.
--  * Files live in a PRIVATE bucket, support-attachments, under <uploader>/<chat>/<file>.
--    The uploader, the learner who owns the chat, and support staff can read them; the app
--    opens them through short-lived signed links.
--  * support_send_attachment() runs the same checks as support_send() (participant, chat not
--    closed, rate limit, staff assignment) and posts the message.
-- ============================================================================

alter table public.support_chat_messages drop constraint if exists support_chat_messages_kind_check;
alter table public.support_chat_messages add constraint support_chat_messages_kind_check
  check (kind = any (array['TEXT', 'NOTE', 'EVENT', 'IMAGE', 'FILE']));

-- Photos and files count as real messages for unread counters and "last message".
create or replace function public._support_post(p_chat uuid, p_sender uuid, p_kind text, p_staff boolean, p_body text, p_meta jsonb default '{}'::jsonb)
returns public.support_chat_messages
language plpgsql security definer set search_path to 'public'
as $function$
declare m public.support_chat_messages;
begin
  insert into public.support_chat_messages (chat_id, sender_id, kind, from_staff, body, meta)
  values (p_chat, p_sender, p_kind, p_staff, p_body, coalesce(p_meta, '{}'::jsonb))
  returning * into m;

  if p_kind in ('TEXT', 'IMAGE', 'FILE') then
    update public.support_chats set
      last_message_at = m.created_at,
      last_message_preview = public._support_preview(p_body),
      last_sender = case when p_staff then 'STAFF' else 'USER' end,
      user_unread = case when p_staff then user_unread + 1 else 0 end,
      staff_unread = case when p_staff then 0 else staff_unread + 1 end,
      user_last_read_at = case when p_staff then user_last_read_at else m.created_at end,
      staff_last_read_at = case when p_staff then m.created_at else staff_last_read_at end,
      first_response_at = case when p_staff then coalesce(first_response_at, m.created_at) else first_response_at end,
      updated_at = now()
    where id = p_chat;
  else
    update public.support_chats set updated_at = now() where id = p_chat;
  end if;
  return m;
end $function$;

insert into storage.buckets (id, name, public, file_size_limit)
values ('support-attachments', 'support-attachments', false, 15728640)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;

drop policy if exists support_attachments_insert on storage.objects;
create policy support_attachments_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'support-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
    and exists (
      select 1 from public.support_chats c
      where c.id::text = (storage.foldername(name))[2]
        and (c.user_id = auth.uid() or public.has_permission('support.manage'))
    )
  );

drop policy if exists support_attachments_read on storage.objects;
create policy support_attachments_read on storage.objects
  for select to authenticated
  using (
    bucket_id = 'support-attachments'
    and (
      (storage.foldername(name))[1] = auth.uid()::text
      or public.has_permission('support.manage')
      or exists (
        select 1 from public.support_chats c
        where c.id::text = (storage.foldername(name))[2] and c.user_id = auth.uid()
      )
    )
  );

create or replace function public.support_send_attachment(
  p_chat uuid,
  p_kind text,
  p_path text,
  p_name text,
  p_mime text default null,
  p_size bigint default null
)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_uid uuid := auth.uid();
  v_kind text := upper(coalesce(p_kind, ''));
  v_name text := left(btrim(coalesce(p_name, '')), 200);
  v_body text;
  c public.support_chats;
  v_is_user boolean;
  v_is_staff boolean;
  v_recent int;
  m public.support_chat_messages;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if v_kind not in ('IMAGE', 'FILE') then raise exception 'INVALID_KIND'; end if;
  if coalesce(p_path, '') not like v_uid::text || '/' || p_chat::text || '/%' then raise exception 'INVALID_PATH'; end if;
  if v_name = '' then v_name := 'file'; end if;

  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  v_is_user := c.user_id = v_uid;
  v_is_staff := not v_is_user and public.has_permission('support.manage');
  if not v_is_user and not v_is_staff then raise exception 'FORBIDDEN'; end if;
  if c.status = 'CLOSED' then raise exception 'CHAT_CLOSED'; end if;

  select count(*) into v_recent from public.support_chat_messages
   where sender_id = v_uid and created_at > now() - interval '1 minute';
  if v_recent >= 20 then raise exception 'TOO_MANY_MESSAGES'; end if;

  v_body := case when v_kind = 'IMAGE' then '📷 صورة' else '📎 ' || v_name end;

  if v_is_staff then
    if c.assigned_to is null then
      update public.support_chats set assigned_to = v_uid, status = 'OPEN' where id = c.id;
      perform public._support_post(c.id, v_uid, 'EVENT', true, 'CLAIMED',
        jsonb_build_object('name', public._support_first_name(v_uid)));
    elsif c.assigned_to <> v_uid then
      raise exception 'ASSIGNED_TO_OTHER';
    end if;
  end if;

  m := public._support_post(c.id, v_uid, v_kind, v_is_staff, v_body,
    jsonb_build_object('path', p_path, 'name', v_name, 'mime', coalesce(p_mime, ''), 'size', coalesce(p_size, 0)));

  if v_is_staff and c.user_unread = 0 then
    perform public.push_notification(c.user_id, 'SUPPORT',
      'رد جديد من الدعم • New reply from support',
      public._support_first_name(v_uid) || ': ' || v_body,
      jsonb_build_object('route', 'support/chat/' || c.id, 'support_chat_id', c.id));
  elsif not v_is_staff and c.assigned_to is not null and c.staff_unread = 0 then
    perform public.push_notification(c.assigned_to, 'SUPPORT',
      'رسالة جديدة في الدعم • New support message',
      public._support_first_name(v_uid) || ': ' || v_body,
      jsonb_build_object('route', 'admin/support/chat/' || c.id, 'support_chat_id', c.id));
  end if;
  return to_jsonb(m);
end $function$;

revoke all on function public.support_send_attachment(uuid, text, text, text, text, bigint) from public, anon;
grant execute on function public.support_send_attachment(uuid, text, text, text, text, bigint) to authenticated;
