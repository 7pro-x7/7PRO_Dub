-- A DB-stored fallback secret for the maintenance function, so a scheduler running from inside
-- this same Supabase project (pg_cron) can authenticate without needing a separate deploy-time
-- secret. Only service_role can ever read app_settings values through the app's own RLS, and the
-- maintenance function itself reads it with the admin client.
insert into public.app_settings (key, value)
values ('maintenance.secret', to_jsonb(encode(gen_random_bytes(24), 'hex')))
on conflict (key) do nothing;

create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;

grant usage on schema cron to postgres;
;
