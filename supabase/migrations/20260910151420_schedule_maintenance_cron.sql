select cron.schedule(
  'maintenance-sweep',
  '*/30 * * * *',
  $$
  select net.http_post(
    url := 'https://usbfrqbjtpvnkmmcusdi.supabase.co/functions/v1/maintenance',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-maintenance-secret', (select value #>> '{}' from public.app_settings where key = 'maintenance.secret')
    ),
    body := '{}'::jsonb
  );
  $$
);;
