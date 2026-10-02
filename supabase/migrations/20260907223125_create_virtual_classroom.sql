-- ============================================================================
-- 7PRO Virtual Classroom (Jitsi-based) — core schema
--
-- Design notes
-- - Every table that belongs to a session carries session_id so RLS never has
--   to chase more than one join to find out who is allowed to see a row.
-- - Authorization for a session is explicit: a STUDENT only ever sees a
--   session if a row exists for them in classroom_participants. There is no
--   "public" or "all my teacher's students" shortcut — the teacher (or
--   OWNER/ADMIN) must add each student on purpose, which is what
--   "دخول للمصرح لهم فقط" (entry for authorized users only) means here.
-- - room_name/room_password are generated server-side and are never picked
--   by the client, so nobody can guess or override the join target.
-- ============================================================================

-- ============================================================================
-- 1. CLASSROOM SESSIONS
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
  description TEXT,

  -- Never exposed to clients that are not authorized for this session (see RLS
  -- policies + the classroom_join() RPC, which is the only place a STUDENT
  -- ever reads these two columns).
  room_name TEXT NOT NULL UNIQUE DEFAULT replace(gen_random_uuid()::text, '-', ''),
  room_password TEXT NOT NULL DEFAULT substr(md5(gen_random_uuid()::text), 1, 12),
  jitsi_domain TEXT NOT NULL DEFAULT 'meet.jit.si',

  scheduled_start TIMESTAMPTZ NOT NULL,
  scheduled_end TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'SCHEDULED'
    CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELED')),

  recording_enabled BOOLEAN NOT NULL DEFAULT false,
  recording_url TEXT,
  recording_started_at TIMESTAMPTZ,
  recording_ended_at TIMESTAMPTZ,

  max_participants INTEGER NOT NULL DEFAULT 30 CHECK (max_participants BETWEEN 1 AND 200),

  created_by UUID NOT NULL REFERENCES auth.users(id),
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT classroom_sessions_valid_window CHECK (scheduled_end > scheduled_start)
);

CREATE INDEX IF NOT EXISTS idx_classroom_sessions_teacher ON classroom_sessions(teacher_id);
CREATE INDEX IF NOT EXISTS idx_classroom_sessions_status ON classroom_sessions(status);
CREATE INDEX IF NOT EXISTS idx_classroom_sessions_start ON classroom_sessions(scheduled_start DESC);

-- ============================================================================
-- 2. PARTICIPANTS (the authorization list for a session)
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_participants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  role_in_session TEXT NOT NULL DEFAULT 'STUDENT'
    CHECK (role_in_session IN ('TEACHER', 'MODERATOR', 'STUDENT')),
  status TEXT NOT NULL DEFAULT 'INVITED'
    CHECK (status IN ('INVITED', 'JOINED', 'LEFT', 'KICKED', 'DECLINED')),
  invited_by UUID REFERENCES auth.users(id),
  hand_raised BOOLEAN NOT NULL DEFAULT false,
  hand_raised_at TIMESTAMPTZ,
  joined_at TIMESTAMPTZ,
  left_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_classroom_participants_session ON classroom_participants(session_id);
CREATE INDEX IF NOT EXISTS idx_classroom_participants_user ON classroom_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_classroom_participants_hand ON classroom_participants(session_id, hand_raised) WHERE hand_raised;

-- ============================================================================
-- 3. ATTENDANCE EVENTS (append-only — survives reconnects and multiple joins)
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_attendance_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  event TEXT NOT NULL CHECK (event IN ('JOIN', 'LEAVE')),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  client_info JSONB
);

CREATE INDEX IF NOT EXISTS idx_classroom_attendance_session ON classroom_attendance_events(session_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_classroom_attendance_user ON classroom_attendance_events(user_id);

-- ============================================================================
-- 4. CHAT / Q&A (our own persisted layer — Jitsi's built-in chat is disabled
--    by the app so every session keeps exactly one saved transcript)
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_chat_messages (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
  is_question BOOLEAN NOT NULL DEFAULT false,
  answered BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_classroom_chat_session ON classroom_chat_messages(session_id, created_at);

-- ============================================================================
-- 5. HAND-RAISE EVENTS (audit trail; live state lives on classroom_participants)
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_hand_raise_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  action TEXT NOT NULL CHECK (action IN ('RAISE', 'LOWER', 'ACKNOWLEDGE')),
  actor_id UUID REFERENCES auth.users(id),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_classroom_hand_events_session ON classroom_hand_raise_events(session_id, occurred_at);

-- ============================================================================
-- 6. WHITEBOARD
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_whiteboard_boards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL UNIQUE REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  background_url TEXT,
  background_type TEXT NOT NULL DEFAULT 'NONE' CHECK (background_type IN ('NONE', 'IMAGE', 'PDF')),
  background_page INTEGER NOT NULL DEFAULT 0,
  created_by UUID NOT NULL REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS classroom_whiteboard_strokes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  board_id UUID NOT NULL REFERENCES classroom_whiteboard_boards(id) ON DELETE CASCADE,
  session_id UUID NOT NULL REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  action TEXT NOT NULL DEFAULT 'STROKE' CHECK (action IN ('STROKE', 'CLEAR', 'UNDO')),
  stroke JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_classroom_wb_strokes_board ON classroom_whiteboard_strokes(board_id, id);

-- ============================================================================
-- 7. RECORDINGS (metadata only — actual capture needs a Jibri-capable Jitsi
--    deployment; see the accompanying notes in the RPC file)
-- ============================================================================
CREATE TABLE IF NOT EXISTS classroom_recordings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES classroom_sessions(id) ON DELETE CASCADE,
  started_by UUID NOT NULL REFERENCES auth.users(id),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ,
  url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_classroom_recordings_session ON classroom_recordings(session_id);

-- ============================================================================
-- 8. updated_at triggers (reuses update_updated_at_column() from the
--    exercises migration if present, otherwise defines it defensively)
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_classroom_sessions_updated_at ON classroom_sessions;
CREATE TRIGGER trg_classroom_sessions_updated_at
  BEFORE UPDATE ON classroom_sessions
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_classroom_wb_boards_updated_at ON classroom_whiteboard_boards;
CREATE TRIGGER trg_classroom_wb_boards_updated_at
  BEFORE UPDATE ON classroom_whiteboard_boards
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
;
