-- 7PRO — مسح محادثة الدعم.
--   * المستخدم يمسح محادثته هو (أي حالة: منتظرة / مفتوحة / منتهية).
--   * المالك أو أي أدمن معاه support.manage يمسح أي محادثة.
-- المسح نهائي: الرسائل والملاحظات الداخلية بتتمسح معاها (on delete cascade).
create or replace function public.support_delete_chat(p_chat uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  c public.support_chats;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  if c.user_id <> v_uid and not coalesce(public.has_permission('support.manage'), false) then
    raise exception 'FORBIDDEN';
  end if;
  delete from public.support_chats where id = p_chat;
end $$;

revoke all on function public.support_delete_chat(uuid) from public, anon;
grant execute on function public.support_delete_chat(uuid) to authenticated;
