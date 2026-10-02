-- ============================================================================
-- 7PRO Virtual Classroom — enable Realtime on the tables the app subscribes
-- to for live sync (chat/Q&A, hand-raise state, participant status, and
-- whiteboard strokes). Wrapped defensively so re-running this migration, or
-- running it on a project where a table was already added, never errors.
-- ============================================================================

DO $$
BEGIN
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE classroom_chat_messages;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;

  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE classroom_participants;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;

  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE classroom_whiteboard_strokes;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;

  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE classroom_sessions;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;

-- REPLICA IDENTITY FULL so UPDATE/DELETE payloads include the old row values
-- (needed to tell, e.g., which participant's hand_raised flag just changed).
ALTER TABLE classroom_participants REPLICA IDENTITY FULL;
ALTER TABLE classroom_sessions REPLICA IDENTITY FULL;
