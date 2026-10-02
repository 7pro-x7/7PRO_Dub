-- Bug fix: the instant staff push (notify_staff_push, wired in
-- 20260915131544_staff_alert_push_hook.sql) never checked the recipient's own
-- per-kind toggle from the Notification Settings screen. Only the 15-minute
-- background poll (PushCenter.sync, client-side) honored it, so an owner/admin/
-- teacher who switched off e.g. PAYOUT_REQUEST would still get pushed instantly
-- for it — the toggle silently did nothing for the channel that matters most.
--
-- notification_preference_enabled() already existed for exactly this (added in
-- 20260918050000_notification_preferences_and_routes.sql) but nothing called it.
-- This wires it in.
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

  -- Respect the recipient's own per-kind toggle (Notification Settings screen).
  if not public.notification_preference_enabled(new.user_id, new.kind) then
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
