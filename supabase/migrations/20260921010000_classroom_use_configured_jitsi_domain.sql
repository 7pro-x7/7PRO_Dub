-- New classroom sessions were being stored with jitsi_domain = 'jitsi.riot.im' (the column
-- default) while classroom_settings says 'meet.ffmuc.net'. The current app and web page both
-- rewrite jitsi.riot.im -> meet.ffmuc.net before joining, so they still end up in the same
-- room, but any older installed build that does not have that rewrite tries to connect to
-- jitsi.riot.im (not a conferencing server) and can never hear the teacher.
-- This makes the database itself say the right host, for every client version.

ALTER TABLE public.classroom_sessions ALTER COLUMN jitsi_domain SET DEFAULT 'meet.ffmuc.net';

CREATE OR REPLACE FUNCTION public.classroom_default_jitsi_domain()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF NEW.jitsi_domain IS NULL OR NEW.jitsi_domain = ''
     OR NEW.jitsi_domain IN ('meet.jit.si', 'jitsi.riot.im') THEN
    SELECT jitsi_domain INTO NEW.jitsi_domain FROM classroom_settings WHERE id = true;
  END IF;
  RETURN NEW;
END;
$$;

-- Only sessions that can still be joined; finished ones are history and are left alone.
UPDATE public.classroom_sessions
SET jitsi_domain = (SELECT jitsi_domain FROM classroom_settings WHERE id = true)
WHERE status IN ('SCHEDULED', 'LIVE')
  AND jitsi_domain IN ('meet.jit.si', 'jitsi.riot.im');
