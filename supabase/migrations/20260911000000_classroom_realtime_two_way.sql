-- ============================================================================
-- 7PRO Virtual Classroom — two-way Realtime fix
--
-- Problems this fixes (all of them broke sync in BOTH directions):
--
-- 1. classroom_participants_select only ever matched `user_id = auth.uid()`
--    for a student, so a student's Realtime subscription on that table could
--    never receive another participant's row. RLS is applied to Postgres
--    Changes exactly like it is to a SELECT: a row the policy hides is a row
--    Realtime silently drops. Result: students never saw who joined, never
--    saw raised hands, and the teacher's roster was the only one that worked.
--
-- 2. classroom_whiteboard_boards was not in the `supabase_realtime`
--    publication at all, so a shared image/PDF/video only ever reached other
--    people through the side-effect CLEAR stroke row. Anything that shares a
--    background without writing that row (or a client that missed it) showed
--    nothing.
--
-- 3. classroom_set_whiteboard_background() required classroom_can_manage(),
--    so a student could never share material back to the teacher.
--
-- 4. Chat/stroke inserts required status = 'LIVE' exactly. A client that
--    posts in the gap before classroom_join() has flipped the session to LIVE
--    (or right after an auto-expire sweep) got a bare RLS 403 that looks
--    exactly like "realtime is broken".
--
-- 5. There was no way to tell a participant who is really connected from one
--    whose row still says JOINED after their phone died — hence last_seen_at
--    plus classroom_heartbeat() below.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Presence: a real "is this student actually connected" signal
-- ----------------------------------------------------------------------------
ALTER TABLE classroom_participants
  ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION classroom_heartbeat(p_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  UPDATE classroom_participants
  SET last_seen_at = now(),
      -- A reconnect after a crash/network drop puts the row back to JOINED
      -- without needing a second classroom_join() round trip.
      status = CASE WHEN status IN ('LEFT', 'INVITED') THEN 'JOINED' ELSE status END,
      joined_at = COALESCE(joined_at, now())
  WHERE session_id = p_session_id
    AND user_id = auth.uid()
    AND status <> 'KICKED';
END;
$$;

REVOKE ALL ON FUNCTION classroom_heartbeat(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_heartbeat(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_heartbeat(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- 2. Participants: everyone in the room can see everyone in the room
--    (still nobody outside it — classroom_can_view is the same gate every
--    other child table already uses).
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS "classroom_participants_select" ON classroom_participants;
CREATE POLICY "classroom_participants_select"
  ON classroom_participants FOR SELECT
  USING (
    user_id = auth.uid()
    OR classroom_can_view(session_id)
  );

-- The self-update guard has to let last_seen_at through, otherwise the
-- heartbeat above would be rejected for students by the trigger.
CREATE OR REPLACE FUNCTION classroom_participants_guard_self_update()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF classroom_can_manage(NEW.session_id) THEN
    RETURN NEW;
  END IF;

  IF NEW.user_id != auth.uid()
     OR NEW.session_id != OLD.session_id
     OR NEW.role_in_session != OLD.role_in_session
     OR NEW.invited_by IS DISTINCT FROM OLD.invited_by
  THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  -- status/joined_at/left_at may only move through the values the
  -- join/leave/heartbeat RPCs themselves set — a student still cannot promote
  -- themselves, change anyone else, or un-kick themselves.
  IF OLD.status = 'KICKED' AND NEW.status <> 'KICKED' THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF NEW.status NOT IN ('INVITED', 'JOINED', 'LEFT', 'KICKED', 'DECLINED') THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  NEW.hand_raised_at := CASE WHEN NEW.hand_raised THEN now() ELSE NEW.hand_raised_at END;
  RETURN NEW;
END;
$$;

-- ----------------------------------------------------------------------------
-- 3. Chat + strokes: accept SCHEDULED as well as LIVE
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS "classroom_chat_insert" ON classroom_chat_messages;
CREATE POLICY "classroom_chat_insert"
  ON classroom_chat_messages FOR INSERT
  WITH CHECK (
    sender_id = auth.uid()
    AND classroom_can_view(session_id)
    AND EXISTS (
      SELECT 1 FROM classroom_sessions
      WHERE id = session_id AND status IN ('SCHEDULED', 'LIVE')
    )
  );

DROP POLICY IF EXISTS "classroom_wb_strokes_insert" ON classroom_whiteboard_strokes;
CREATE POLICY "classroom_wb_strokes_insert"
  ON classroom_whiteboard_strokes FOR INSERT
  WITH CHECK (
    user_id = auth.uid()
    AND EXISTS (
      SELECT 1 FROM classroom_sessions
      WHERE id = session_id AND status IN ('SCHEDULED', 'LIVE')
    )
    AND (
      (action = 'STROKE' AND classroom_can_view(session_id))
      OR (action IN ('CLEAR', 'UNDO') AND classroom_can_manage(session_id))
    )
  );

-- ----------------------------------------------------------------------------
-- 4. Sharing works in both directions
--
--    Any authorized participant may now put material on the shared board.
--    The board row itself is still only writable through this SECURITY
--    DEFINER function, so a student cannot touch it directly, and the
--    function records who shared it.
-- ----------------------------------------------------------------------------
ALTER TABLE classroom_whiteboard_boards
  ADD COLUMN IF NOT EXISTS shared_by UUID REFERENCES profiles(id),
  ADD COLUMN IF NOT EXISTS shared_at TIMESTAMPTZ;

DO $$
BEGIN
  BEGIN
    ALTER TABLE classroom_whiteboard_boards
      DROP CONSTRAINT classroom_whiteboard_boards_background_type_check;
  EXCEPTION WHEN undefined_object THEN NULL;
  END;
END $$;

ALTER TABLE classroom_whiteboard_boards
  ADD CONSTRAINT classroom_whiteboard_boards_background_type_check
  CHECK (background_type IN ('NONE', 'IMAGE', 'PDF', 'VIDEO'));

CREATE OR REPLACE FUNCTION classroom_set_whiteboard_background(
  p_session_id UUID, p_url TEXT, p_type TEXT, p_page INTEGER DEFAULT 0
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_board UUID;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  -- Was classroom_can_manage(): the teacher could share to students, but a
  -- student could never share back. Any authorized, non-kicked participant
  -- may share now; everyone outside the session is still refused.
  IF NOT classroom_can_view(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF p_type NOT IN ('NONE', 'IMAGE', 'PDF', 'VIDEO') THEN
    RAISE EXCEPTION 'INVALID_TYPE';
  END IF;

  v_board := classroom_ensure_whiteboard(p_session_id);

  UPDATE classroom_whiteboard_boards
  SET background_url = p_url,
      background_type = p_type,
      background_page = p_page,
      shared_by = auth.uid(),
      shared_at = now()
  WHERE id = v_board;

  -- Keep writing the CLEAR marker: it is what tells every already-connected
  -- client to drop strokes drawn over the previous material. The board UPDATE
  -- above is now replicated on its own too (see section 5), so a client that
  -- misses this row still gets the new background.
  INSERT INTO classroom_whiteboard_strokes (board_id, session_id, user_id, action, stroke)
  VALUES (v_board, p_session_id, auth.uid(), 'CLEAR', NULL);
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) TO authenticated;

-- classroom_ensure_whiteboard() is called above with PERFORM-style semantics
-- from a student's session too, so make sure it returns the existing board for
-- a viewer instead of only for a manager (it already checks can_view).

-- ----------------------------------------------------------------------------
-- 5. Publication + replica identity for every table the clients subscribe to
-- ----------------------------------------------------------------------------
DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'classroom_sessions',
    'classroom_participants',
    'classroom_chat_messages',
    'classroom_whiteboard_strokes',
    'classroom_whiteboard_boards'
  ] LOOP
    BEGIN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE %I', t);
    EXCEPTION WHEN duplicate_object THEN NULL;
    END;
  END LOOP;
END $$;

-- REPLICA IDENTITY FULL is what makes the *old* row available to Realtime on
-- UPDATE/DELETE. Without it, Realtime cannot evaluate the RLS policy for an
-- update whose policy depends on a column outside the primary key, and the
-- event is dropped instead of delivered.
ALTER TABLE classroom_sessions REPLICA IDENTITY FULL;
ALTER TABLE classroom_participants REPLICA IDENTITY FULL;
ALTER TABLE classroom_whiteboard_boards REPLICA IDENTITY FULL;
ALTER TABLE classroom_chat_messages REPLICA IDENTITY FULL;
ALTER TABLE classroom_whiteboard_strokes REPLICA IDENTITY FULL;

-- ----------------------------------------------------------------------------
-- 6. Session state the clients need for "is everyone really connected"
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_session_presence(p_session_id UUID)
RETURNS TABLE (
  user_id UUID,
  full_name TEXT,
  avatar_url TEXT,
  role_in_session TEXT,
  status TEXT,
  hand_raised BOOLEAN,
  mic_locked BOOLEAN,
  camera_locked BOOLEAN,
  last_seen_at TIMESTAMPTZ,
  online BOOLEAN
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;
  IF NOT classroom_can_view(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;

  RETURN QUERY
  SELECT
    cp.user_id,
    p.full_name,
    p.avatar_url,
    cp.role_in_session,
    cp.status,
    cp.hand_raised,
    cp.mic_locked,
    cp.camera_locked,
    cp.last_seen_at,
    (cp.status = 'JOINED' AND cp.last_seen_at IS NOT NULL
      AND cp.last_seen_at > now() - INTERVAL '45 seconds') AS online
  FROM classroom_participants cp
  JOIN profiles p ON p.id = cp.user_id
  WHERE cp.session_id = p_session_id
    AND cp.status <> 'KICKED'
  ORDER BY (cp.role_in_session = 'TEACHER') DESC, p.full_name NULLS LAST;
END;
$$;

REVOKE ALL ON FUNCTION classroom_session_presence(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_session_presence(UUID) FROM anon;
GRANT EXECUTE ON FUNCTION classroom_session_presence(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- 7. Storage: a student sharing material needs write access to their own
--    folder in the shared bucket. Scoped to `<my uid>/classroom-whiteboards/`
--    so it grants nothing anywhere else in the bucket.
-- ----------------------------------------------------------------------------
DO $$
BEGIN
  BEGIN
    CREATE POLICY "classroom_share_upload"
      ON storage.objects FOR INSERT TO authenticated
      WITH CHECK (
        bucket_id = 'course-media'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND (storage.foldername(name))[2] = 'classroom-whiteboards'
      );
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;
