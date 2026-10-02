-- Loud, immediate alerts for owner / admin / teacher.
--
-- Until now a notification row only reached the device when the app happened to
-- poll (PushCenter / the 15-minute background worker), so a staff alert could sit
-- unseen for a quarter of an hour or until the app was next opened. This wires the
-- notifications table straight to FCM, the same transport the classroom ring
-- already uses, so a payment request or an approval lands on the phone within
-- seconds even with the app closed.

-- The Edge Function is called with no user JWT (there is no user in an INSERT
-- trigger), so it authenticates on this shared secret instead — read here and
-- re-read on the function's side through the service-role client.
create or replace function public.get_alert_push_hook_secret()
returns text
language plpgsql
security definer
set search_path to 'public', 'vault'
as $function$
declare v_secret text;
begin
  select decrypted_secret into v_secret
  from vault.decrypted_secrets
  where name = 'alert_push_hook_secret'
  limit 1;
  return v_secret;
end;
$function$;

revoke all on function public.get_alert_push_hook_secret() from public, anon, authenticated;
grant execute on function public.get_alert_push_hook_secret() to service_role;

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
  -- Only the people who run the academy. Students keep the quieter existing path;
  -- nothing about their notifications changes.
  select role::text into v_role from public.profiles where id = new.user_id;
  if v_role is null or v_role not in ('OWNER', 'ADMIN', 'TEACHER') then
    return new;
  end if;

  select decrypted_secret into v_secret
  from vault.decrypted_secrets where name = 'alert_push_hook_secret' limit 1;
  if v_secret is null then
    return new;
  end if;

  -- pg_net queues the request and returns immediately, so the insert that raised
  -- this alert is never slowed down by (or dependent on) the push going out.
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
  -- A push is a delivery convenience. It must never roll back the thing it was
  -- announcing: a failed FCM call cannot be allowed to undo a recorded payment.
  return new;
end;
$function$;

drop trigger if exists notifications_staff_push on public.notifications;
create trigger notifications_staff_push
after insert on public.notifications
for each row execute function public.notify_staff_push();;
