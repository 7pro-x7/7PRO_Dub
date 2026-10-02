-- 7PRO Virtual Classroom — guest access by invite link (see repo migration
-- 20260915000000_classroom_guest_links.sql for the full commentary).

ALTER TABLE classroom_sessions
  ADD COLUMN IF NOT EXISTS guest_token UUID NOT NULL DEFAULT gen_random_uuid();

DO $$
BEGIN
  ALTER TABLE classroom_sessions ADD CONSTRAINT classroom_sessions_guest_token_key UNIQUE (guest_token);
EXCEPTION WHEN duplicate_table OR duplicate_object THEN NULL;
END $$;

ALTER TABLE classroom_sessions
  ADD COLUMN IF NOT EXISTS guests_enabled BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS is_guest BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_profiles_is_guest ON profiles(is_guest) WHERE is_guest;

CREATE OR REPLACE FUNCTION classroom_guest_link_token(p_session_id UUID)
RETURNS TABLE (guest_token UUID, guests_enabled BOOLEAN)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT cs.guest_token, cs.guests_enabled
  FROM classroom_sessions cs
  WHERE cs.id = p_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_guest_link_token(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_guest_link_token(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_guest_link_token(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION classroom_rotate_guest_token(p_session_id UUID)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_token UUID;
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  UPDATE classroom_sessions
  SET guest_token = gen_random_uuid()
  WHERE id = p_session_id
  RETURNING guest_token INTO v_token;

  RETURN v_token;
END;
$$;

REVOKE ALL ON FUNCTION classroom_rotate_guest_token(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_rotate_guest_token(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_rotate_guest_token(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION classroom_set_guests_enabled(p_session_id UUID, p_enabled BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  UPDATE classroom_sessions SET guests_enabled = p_enabled WHERE id = p_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_guests_enabled(UUID, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_set_guests_enabled(UUID, BOOLEAN) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_set_guests_enabled(UUID, BOOLEAN) TO authenticated;

CREATE OR REPLACE FUNCTION classroom_admit_guest(
  p_token UUID,
  p_user_id UUID,
  p_display_name TEXT
)
RETURNS TABLE (
  session_id UUID,
  title TEXT,
  room_name TEXT,
  room_password TEXT,
  jitsi_domain TEXT,
  display_name TEXT,
  status TEXT,
  mic_locked BOOLEAN,
  camera_locked BOOLEAN
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
#variable_conflict use_column
DECLARE
  v_session RECORD;
  v_live_count INTEGER;
  v_name TEXT;
  v_mic BOOLEAN;
  v_cam BOOLEAN;
BEGIN
  SELECT * INTO v_session FROM classroom_sessions WHERE guest_token = p_token FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'INVALID_LINK';
  END IF;

  IF NOT v_session.guests_enabled THEN
    RAISE EXCEPTION 'GUESTS_DISABLED';
  END IF;
  IF v_session.status = 'CANCELED' THEN
    RAISE EXCEPTION 'SESSION_CANCELED';
  END IF;
  IF v_session.status = 'ENDED' THEN
    RAISE EXCEPTION 'SESSION_ENDED';
  END IF;
  IF now() < v_session.scheduled_start - INTERVAL '10 minutes' THEN
    RAISE EXCEPTION 'TOO_EARLY';
  END IF;
  IF now() > v_session.scheduled_end + INTERVAL '30 minutes' AND v_session.status <> 'LIVE' THEN
    RAISE EXCEPTION 'SESSION_WINDOW_PASSED';
  END IF;

  SELECT count(*) INTO v_live_count
  FROM classroom_participants cp2
  WHERE cp2.session_id = v_session.id AND cp2.status = 'JOINED';

  IF v_live_count >= v_session.max_participants THEN
    RAISE EXCEPTION 'SESSION_FULL';
  END IF;

  v_name := NULLIF(btrim(COALESCE(p_display_name, '')), '');
  v_name := COALESCE(v_name, 'ضيف');
  v_name := left(v_name, 60);

  UPDATE profiles
  SET is_guest = true,
      full_name = v_name
  WHERE id = p_user_id;

  INSERT INTO classroom_participants (session_id, user_id, role_in_session, status, joined_at, last_seen_at)
  VALUES (v_session.id, p_user_id, 'STUDENT', 'JOINED', now(), now())
  ON CONFLICT (session_id, user_id) DO UPDATE
    SET status = 'JOINED', joined_at = now(), left_at = NULL, last_seen_at = now();

  INSERT INTO classroom_attendance_events (session_id, user_id, event)
  VALUES (v_session.id, p_user_id, 'JOIN');

  SELECT cp.mic_locked, cp.camera_locked INTO v_mic, v_cam
  FROM classroom_participants cp
  WHERE cp.session_id = v_session.id AND cp.user_id = p_user_id;

  RETURN QUERY
  SELECT
    v_session.id,
    v_session.title,
    v_session.room_name,
    v_session.room_password,
    v_session.jitsi_domain,
    v_name,
    v_session.status,
    COALESCE(v_mic, false),
    COALESCE(v_cam, false);
END;
$$;

REVOKE ALL ON FUNCTION classroom_admit_guest(UUID, UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_admit_guest(UUID, UUID, TEXT) FROM anon;
REVOKE ALL ON FUNCTION classroom_admit_guest(UUID, UUID, TEXT) FROM authenticated;
GRANT EXECUTE ON FUNCTION classroom_admit_guest(UUID, UUID, TEXT) TO service_role;

DROP POLICY IF EXISTS "classroom_share_upload" ON storage.objects;
CREATE POLICY "classroom_share_upload"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'course-media'
    AND (storage.foldername(name))[1] = auth.uid()::text
    AND (storage.foldername(name))[2] = 'classroom-whiteboards'
    AND (
      NOT COALESCE((SELECT p.is_guest FROM profiles p WHERE p.id = auth.uid()), false)
      OR EXISTS (
        SELECT 1
        FROM classroom_participants cp
        JOIN classroom_sessions cs ON cs.id = cp.session_id
        WHERE cp.user_id = auth.uid()
          AND cp.status <> 'KICKED'
          AND cs.status = 'LIVE'
      )
    )
  );

CREATE OR REPLACE FUNCTION classroom_purge_guest_accounts(p_retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_deleted INTEGER;
BEGIN
  WITH stale AS (
    SELECT p.id
    FROM profiles p
    WHERE p.is_guest
      AND p.created_at < now() - make_interval(days => GREATEST(p_retention_days, 1))
      AND NOT EXISTS (
        SELECT 1
        FROM classroom_participants cp
        JOIN classroom_sessions cs ON cs.id = cp.session_id
        WHERE cp.user_id = p.id
          AND cs.status IN ('SCHEDULED', 'LIVE')
      )
  )
  DELETE FROM auth.users u USING stale WHERE u.id = stale.id;

  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted;
END;
$$;

REVOKE ALL ON FUNCTION classroom_purge_guest_accounts(INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_purge_guest_accounts(INTEGER) FROM anon;
REVOKE ALL ON FUNCTION classroom_purge_guest_accounts(INTEGER) FROM authenticated;
GRANT EXECUTE ON FUNCTION classroom_purge_guest_accounts(INTEGER) TO service_role;

DO $$
BEGIN
  PERFORM cron.unschedule('classroom-purge-guests');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

SELECT cron.schedule(
  'classroom-purge-guests',
  '17 3 * * *',
  $cron$SELECT public.classroom_purge_guest_accounts(30)$cron$
);;
