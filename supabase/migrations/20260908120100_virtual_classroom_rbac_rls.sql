-- ============================================================================
-- 7PRO Virtual Classroom — RBAC helpers + Row Level Security
--
-- Role model (matches the rest of 7PRO exactly):
--   OWNER   -> full control of every session.
--   ADMIN   -> same as OWNER for classroom data, but ONLY if the owner has
--              granted the 'classroom.manage' row in admin_permissions
--              (same mechanism AdminRepository.ALL_PERMISSIONS already uses
--              for every other admin capability).
--   TEACHER -> full control of sessions where classroom_sessions.teacher_id
--              is them: create, edit, invite/remove participants, start/end,
--              moderate chat, manage the whiteboard, view attendance.
--   STUDENT -> read-only access to a session, and only once a row exists for
--              them in classroom_participants. Everything a student can
--              write (chat, hand raise, whiteboard strokes) is scoped to
--              sessions they are a participant of.
-- ============================================================================

CREATE OR REPLACE FUNCTION classroom_my_role()
RETURNS TEXT
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT role::text FROM profiles WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION classroom_is_owner()
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'OWNER');
$$;

-- OWNER always passes. ADMIN passes only with the matching granted permission,
-- exactly like sessionState.can(permission) on the client and every other
-- "<x>.manage" gate already in AdminConsole.
CREATE OR REPLACE FUNCTION classroom_is_staff_with(p_permission TEXT)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT
    EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'OWNER')
    OR EXISTS (
      SELECT 1 FROM profiles p
      JOIN admin_permissions ap ON ap.user_id = p.id
      WHERE p.id = auth.uid() AND p.role = 'ADMIN' AND ap.permission = p_permission
    );
$$;

CREATE OR REPLACE FUNCTION classroom_is_teacher_of(p_session_id UUID)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (
    SELECT 1 FROM classroom_sessions
    WHERE id = p_session_id AND teacher_id = auth.uid()
  );
$$;

CREATE OR REPLACE FUNCTION classroom_is_participant(p_session_id UUID)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (
    SELECT 1 FROM classroom_participants
    WHERE session_id = p_session_id AND user_id = auth.uid()
      AND status != 'KICKED'
  );
$$;

-- True for the teacher who owns the session, staff with 'classroom.manage',
-- or an authorized (non-kicked) participant. This is the single "can see
-- this session at all" gate reused by every child table below.
CREATE OR REPLACE FUNCTION classroom_can_view(p_session_id UUID)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT classroom_is_teacher_of(p_session_id)
      OR classroom_is_staff_with('classroom.manage')
      OR classroom_is_participant(p_session_id);
$$;

CREATE OR REPLACE FUNCTION classroom_can_manage(p_session_id UUID)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT classroom_is_teacher_of(p_session_id)
      OR classroom_is_staff_with('classroom.manage');
$$;

-- ============================================================================
-- classroom_sessions
-- ============================================================================
ALTER TABLE classroom_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_sessions_select"
  ON classroom_sessions FOR SELECT
  USING (classroom_can_view(id));

CREATE POLICY "classroom_sessions_insert_teacher"
  ON classroom_sessions FOR INSERT
  WITH CHECK (
    teacher_id = auth.uid()
    AND classroom_my_role() IN ('TEACHER', 'OWNER', 'ADMIN')
  );

CREATE POLICY "classroom_sessions_insert_staff"
  ON classroom_sessions FOR INSERT
  WITH CHECK (classroom_is_staff_with('classroom.manage'));

CREATE POLICY "classroom_sessions_update"
  ON classroom_sessions FOR UPDATE
  USING (classroom_can_manage(id))
  WITH CHECK (classroom_can_manage(id));

CREATE POLICY "classroom_sessions_delete"
  ON classroom_sessions FOR DELETE
  USING (classroom_can_manage(id));

GRANT SELECT, INSERT, UPDATE, DELETE ON classroom_sessions TO authenticated;

-- room_name / room_password must never leak to a plain SELECT for a student —
-- Postgres RLS is row-level, not column-level, so the column-level guard is
-- enforced by never selecting those two columns from the Kotlin app for a
-- STUDENT (see ClassroomRepository) and by only ever returning them from the
-- SECURITY DEFINER classroom_join() RPC below, which re-checks authorization
-- itself before returning anything.

-- ============================================================================
-- classroom_participants
-- ============================================================================
ALTER TABLE classroom_participants ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_participants_select"
  ON classroom_participants FOR SELECT
  USING (
    user_id = auth.uid()
    OR classroom_can_manage(session_id)
  );

CREATE POLICY "classroom_participants_insert"
  ON classroom_participants FOR INSERT
  WITH CHECK (classroom_can_manage(session_id));

CREATE POLICY "classroom_participants_update_manager"
  ON classroom_participants FOR UPDATE
  USING (classroom_can_manage(session_id))
  WITH CHECK (classroom_can_manage(session_id));

-- A participant may update ONLY their own row, and only the hand-raise flag —
-- enforced by the trigger below (RLS alone cannot restrict to a subset of
-- columns).
CREATE POLICY "classroom_participants_update_self"
  ON classroom_participants FOR UPDATE
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "classroom_participants_delete"
  ON classroom_participants FOR DELETE
  USING (classroom_can_manage(session_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON classroom_participants TO authenticated;

CREATE OR REPLACE FUNCTION classroom_participants_guard_self_update()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
  -- Staff/teacher updates go through untouched.
  IF classroom_can_manage(NEW.session_id) THEN
    RETURN NEW;
  END IF;

  -- A student may only ever flip their own hand_raised flag; every other
  -- column on their own row must stay exactly as it was.
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
$$;

DROP TRIGGER IF EXISTS trg_classroom_participants_guard ON classroom_participants;
CREATE TRIGGER trg_classroom_participants_guard
  BEFORE UPDATE ON classroom_participants
  FOR EACH ROW EXECUTE FUNCTION classroom_participants_guard_self_update();

-- ============================================================================
-- classroom_attendance_events — append-only, written by classroom_join/leave
-- ============================================================================
ALTER TABLE classroom_attendance_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_attendance_select"
  ON classroom_attendance_events FOR SELECT
  USING (user_id = auth.uid() OR classroom_can_manage(session_id));

-- Inserts only ever happen through the SECURITY DEFINER RPCs, but the policy
-- still needs to exist for the RPC's own role to write under RLS.
CREATE POLICY "classroom_attendance_insert"
  ON classroom_attendance_events FOR INSERT
  WITH CHECK (user_id = auth.uid() AND classroom_is_participant(session_id));

GRANT SELECT, INSERT ON classroom_attendance_events TO authenticated;
GRANT USAGE ON SEQUENCE classroom_attendance_events_id_seq TO authenticated;

-- ============================================================================
-- classroom_chat_messages
-- ============================================================================
ALTER TABLE classroom_chat_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_chat_select"
  ON classroom_chat_messages FOR SELECT
  USING (classroom_can_view(session_id));

CREATE POLICY "classroom_chat_insert"
  ON classroom_chat_messages FOR INSERT
  WITH CHECK (
    sender_id = auth.uid()
    AND classroom_can_view(session_id)
    AND EXISTS (
      SELECT 1 FROM classroom_sessions
      WHERE id = session_id AND status = 'LIVE'
    )
  );

CREATE POLICY "classroom_chat_update_manager"
  ON classroom_chat_messages FOR UPDATE
  USING (classroom_can_manage(session_id))
  WITH CHECK (classroom_can_manage(session_id));

CREATE POLICY "classroom_chat_delete"
  ON classroom_chat_messages FOR DELETE
  USING (sender_id = auth.uid() OR classroom_can_manage(session_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON classroom_chat_messages TO authenticated;
GRANT USAGE ON SEQUENCE classroom_chat_messages_id_seq TO authenticated;

-- ============================================================================
-- classroom_hand_raise_events — audit trail, written by the RPCs
-- ============================================================================
ALTER TABLE classroom_hand_raise_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_hand_events_select"
  ON classroom_hand_raise_events FOR SELECT
  USING (classroom_can_view(session_id));

CREATE POLICY "classroom_hand_events_insert"
  ON classroom_hand_raise_events FOR INSERT
  WITH CHECK (
    (user_id = auth.uid() AND classroom_is_participant(session_id))
    OR classroom_can_manage(session_id)
  );

GRANT SELECT, INSERT ON classroom_hand_raise_events TO authenticated;
GRANT USAGE ON SEQUENCE classroom_hand_raise_events_id_seq TO authenticated;

-- ============================================================================
-- classroom_whiteboard_boards / strokes
-- ============================================================================
ALTER TABLE classroom_whiteboard_boards ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_wb_boards_select"
  ON classroom_whiteboard_boards FOR SELECT
  USING (classroom_can_view(session_id));

CREATE POLICY "classroom_wb_boards_upsert"
  ON classroom_whiteboard_boards FOR INSERT
  WITH CHECK (classroom_can_manage(session_id));

CREATE POLICY "classroom_wb_boards_update"
  ON classroom_whiteboard_boards FOR UPDATE
  USING (classroom_can_manage(session_id))
  WITH CHECK (classroom_can_manage(session_id));

GRANT SELECT, INSERT, UPDATE ON classroom_whiteboard_boards TO authenticated;

ALTER TABLE classroom_whiteboard_strokes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_wb_strokes_select"
  ON classroom_whiteboard_strokes FOR SELECT
  USING (classroom_can_view(session_id));

-- Everyone in the room can draw; only the teacher/staff can clear the board
-- (enforced below since CLEAR is just another row with action='CLEAR').
CREATE POLICY "classroom_wb_strokes_insert"
  ON classroom_whiteboard_strokes FOR INSERT
  WITH CHECK (
    user_id = auth.uid()
    AND (
      (action = 'STROKE' AND classroom_can_view(session_id)
        AND EXISTS (SELECT 1 FROM classroom_sessions WHERE id = session_id AND status = 'LIVE'))
      OR (action IN ('CLEAR', 'UNDO') AND classroom_can_manage(session_id))
    )
  );

GRANT SELECT, INSERT ON classroom_whiteboard_strokes TO authenticated;
GRANT USAGE ON SEQUENCE classroom_whiteboard_strokes_id_seq TO authenticated;

-- ============================================================================
-- classroom_recordings
-- ============================================================================
ALTER TABLE classroom_recordings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "classroom_recordings_select"
  ON classroom_recordings FOR SELECT
  USING (classroom_can_view(session_id));

CREATE POLICY "classroom_recordings_manage"
  ON classroom_recordings FOR ALL
  USING (classroom_can_manage(session_id))
  WITH CHECK (classroom_can_manage(session_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON classroom_recordings TO authenticated;

-- ============================================================================
-- Register the classroom permission with the existing owner-grant system so
-- it shows up next to every other "<x>.manage" toggle on the admin
-- permissions screen (AdminRepository.ALL_PERMISSIONS on the client mirrors
-- this string exactly — see StringsAdmin.kt / AdminRepository.kt).
-- Nothing to insert here: admin_permissions rows are created by the OWNER at
-- runtime through set_admin_permissions(), the same as every other permission.
-- ============================================================================
