-- ============================================================================
-- Meeting fixes (2026-09-22)
--
-- 1. classroom_heartbeat no longer brings a participant back to JOINED on a class
--    that is over. A device still sitting in an ended call kept flipping its seat
--    back to JOINED every 20 s (8 such rows were found on ENDED sessions).
--    Revival from LEFT/INVITED now only happens while the session is LIVE.
-- 2. A student could clear their own teacher-set mic/camera lock with a direct
--    UPDATE on their own participant row (the self-update policy allowed it and
--    the guard trigger did not check those columns). Only managers may change them.
-- 3. One-off cleanup: seats still JOINED on ENDED/CANCELED sessions -> LEFT.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.classroom_heartbeat(p_session_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  UPDATE classroom_participants cp
  SET last_seen_at = now(),
      status = CASE
                 WHEN cp.status IN ('LEFT', 'INVITED') AND s.status = 'LIVE' THEN 'JOINED'
                 ELSE cp.status
               END,
      joined_at = CASE
                    WHEN cp.status IN ('LEFT', 'INVITED') AND s.status = 'LIVE'
                      THEN COALESCE(cp.joined_at, now())
                    ELSE cp.joined_at
                  END
  FROM classroom_sessions s
  WHERE s.id = cp.session_id
    AND cp.session_id = p_session_id
    AND cp.user_id = auth.uid()
    AND cp.status <> 'KICKED'
    AND s.status IN ('LIVE', 'SCHEDULED');
END;
$function$;

CREATE OR REPLACE FUNCTION public.classroom_participants_guard_self_update()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  IF classroom_can_manage(NEW.session_id) THEN
    RETURN NEW;
  END IF;
  IF NEW.user_id != auth.uid()
     OR NEW.session_id != OLD.session_id
     OR NEW.role_in_session != OLD.role_in_session
     OR NEW.invited_by IS DISTINCT FROM OLD.invited_by
     OR NEW.mic_locked IS DISTINCT FROM OLD.mic_locked
     OR NEW.camera_locked IS DISTINCT FROM OLD.camera_locked
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF OLD.status = 'KICKED' AND NEW.status <> 'KICKED' THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF NEW.status NOT IN ('INVITED', 'JOINED', 'LEFT', 'KICKED', 'DECLINED') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  NEW.hand_raised_at := CASE WHEN NEW.hand_raised THEN now() ELSE NEW.hand_raised_at END;
  RETURN NEW;
END;
$function$;

UPDATE classroom_participants cp
SET status = 'LEFT', left_at = COALESCE(cp.left_at, s.ended_at, now())
FROM classroom_sessions s
WHERE s.id = cp.session_id
  AND s.status IN ('ENDED', 'CANCELED')
  AND cp.status = 'JOINED';
