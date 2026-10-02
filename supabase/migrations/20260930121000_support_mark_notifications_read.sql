-- قراءة المحادثة بتعلّم إشعاراتها كمقروءة، فالموبايل مايرنّش على رسالة اتقرت خلاص.
create or replace function public.support_mark_read(p_chat uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare c public.support_chats;
begin
  select * into c from public.support_chats where id = p_chat;
  if not found then return; end if;
  if c.user_id = auth.uid() then
    update public.support_chats set user_unread = 0, user_last_read_at = now() where id = p_chat;
  elsif public.has_permission('support.manage') and (c.assigned_to is null or c.assigned_to = auth.uid()) then
    update public.support_chats set staff_unread = 0, staff_last_read_at = now() where id = p_chat;
  else
    return;
  end if;
  update public.notifications set read_at = now()
   where user_id = auth.uid() and read_at is null and data->>'support_chat_id' = p_chat::text;
end $$;
