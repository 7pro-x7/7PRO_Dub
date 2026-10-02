-- REAL BUG, confirmed by live testing: trg_classroom_participants_guard fires on every UPDATE to
-- classroom_participants and blocks any change to status/joined_at/left_at from a non-manager —
-- including the legitimate one classroom_join()/classroom_leave() themselves perform on a
-- student's own row. Since a teacher normally invites students *before* the session starts,
-- that first insert already creates the row (status INVITED), so the student's own
-- classroom_join() call almost always hits the UPDATE path (ON CONFLICT DO UPDATE) — and was
-- being rejected with FORBIDDEN. This is likely why "students don't actually connect" was
-- reported: an already-invited student could never actually join.
--
-- Fix: classroom_join()/classroom_leave() set a transaction-local flag right before their own
-- UPDATE, and the guard trigger allows the change when it's set. A client can never set this
-- flag itself — it only reaches Postgres through PostgREST's table/RPC surface, never arbitrary
-- SQL — so the original protection (a student directly PATCHing their own row via the
-- self-update RLS policy to forge JOINED status, fake timestamps, or change invited_by) is
-- completely unaffected for every path except these two vetted RPCs.

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

  IF current_setting('app.classroom_join_bypass', true) = NEW.session_id::text THEN
    RETURN NEW;
  END IF;

  IF NEW.user_id != auth.uid()
     OR NEW.session_id != OLD.session_id
     OR NEW.role_in_session != OLD.role_in_session
     OR NEW.status != OLD.status
     OR NEW.invited_by IS DISTINCT FROM OLD.invited_by
     OR NEW.joined_at IS DISTINCT FROM OLD.joined_at
     OR NEW.left_at IS DISTINCT FROM OLD.left_at
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  NEW.hand_raised_at := CASE WHEN NEW.hand_raised THEN now() ELSE NEW.hand_raised_at END;
  RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.classroom_join(p_session_id uuid)
 RETURNS TABLE(room_name text, room_password text, jitsi_domain text, role_in_session text, is_moderator boolean, display_name text, mic_locked boolean, camera_locked boolean)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_session RECORD;
  v_participant RECORD;
  v_is_manager BOOLEAN := classroom_can_manage(p_session_id);
  v_live_count INTEGER;
  v_name TEXT;
  v_mic_locked BOOLEAN;
  v_camera_locked BOOLEAN;
BEGIN
  SELECT * INTO v_session FROM classroom_sessions WHERE id = p_session_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SESSION_NOT_FOUND';
  END IF;

  IF v_session.status = 'CANCELED' THEN
    RAISE EXCEPTION 'SESSION_CANCELED';
  END IF;
  IF v_session.status = 'ENDED' THEN
    RAISE EXCEPTION 'SESSION_ENDED';
  END IF;

  IF NOT v_is_manager THEN
    SELECT * INTO v_participant
    FROM classroom_participants
    WHERE session_id = p_session_id AND user_id = auth.uid();

    IF NOT FOUND OR v_participant.status = 'KICKED' THEN
      RAISE EXCEPTION 'NOT_AUTHORIZED';
    END IF;

    IF now() < v_session.scheduled_start - INTERVAL '10 minutes' THEN
      RAISE EXCEPTION 'TOO_EARLY';
    END IF;
    IF now() > v_session.scheduled_end + INTERVAL '30 minutes' AND v_session.status != 'LIVE' THEN
      RAISE EXCEPTION 'SESSION_WINDOW_PASSED';
    END IF;

    SELECT count(*) INTO v_live_count
    FROM classroom_participants
    WHERE session_id = p_session_id AND status = 'JOINED';

    IF v_live_count >= v_session.max_participants THEN
      RAISE EXCEPTION 'SESSION_FULL';
    END IF;
  END IF;

  PERFORM set_config('app.classroom_join_bypass', p_session_id::text, true);

  INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, invited_by, joined_at)
  VALUES (
    p_session_id, auth.uid(),
    CASE WHEN v_session.teacher_id = auth.uid() THEN 'TEACHER'
         WHEN v_is_manager THEN 'MODERATOR'
         ELSE 'STUDENT' END,
    'JOINED', auth.uid(), now()
  )
  ON CONFLICT (session_id, user_id) DO UPDATE
    SET status = 'JOINED', joined_at = now(), left_at = NULL;

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  VALUES (p_session_id, auth.uid(), 'JOIN');

  IF v_session.status = 'SCHEDULED' THEN
    UPDATE classroom_sessions SET status = 'LIVE', started_at = now() WHERE id = p_session_id;
  END IF;

  SELECT COALESCE(p.full_name, p.email, 'Learner') INTO v_name FROM profiles p WHERE p.id = auth.uid();

  SELECT cp.mic_locked, cp.camera_locked INTO v_mic_locked, v_camera_locked
  FROM classroom_participants cp
  WHERE cp.session_id = p_session_id AND cp.user_id = auth.uid();

  RETURN QUERY
  SELECT
    v_session.room_name,
    v_session.room_password,
    v_session.jitsi_domain,
    CASE WHEN v_session.teacher_id = auth.uid() THEN 'TEACHER'
         WHEN v_is_manager THEN 'MODERATOR'
         ELSE 'STUDENT' END,
    (v_session.teacher_id = auth.uid() OR v_is_manager),
    v_name,
    COALESCE(v_mic_locked, false),
    COALESCE(v_camera_locked, false);
END;
$function$;

CREATE OR REPLACE FUNCTION public.classroom_leave(p_session_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  PERFORM set_config('app.classroom_join_bypass', p_session_id::text, true);

  UPDATE classroom_participants
  SET status = CASE WHEN status = 'JOINED' THEN 'LEFT' ELSE status END,
      left_at = now()
  WHERE session_id = p_session_id AND user_id = auth.uid();

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  SELECT p_session_id, auth.uid(), 'LEAVE'
  WHERE classroom_is_participant(p_session_id)
     OR classroom_can_manage(p_session_id);
END;
$function$;;
