-- ============================================================================
-- Allow a VIDEO background on the shared whiteboard, alongside the existing
-- IMAGE and PDF types — lets a teacher share a video (from gallery or files)
-- that every participant sees in the same "whoever uploads it, everyone sees
-- it" way images/PDFs already work.
-- ============================================================================

ALTER TABLE classroom_whiteboard_boards
  DROP CONSTRAINT IF EXISTS classroom_whiteboard_boards_background_type_check;

ALTER TABLE classroom_whiteboard_boards
  ADD CONSTRAINT classroom_whiteboard_boards_background_type_check
  CHECK (background_type IN ('NONE', 'IMAGE', 'PDF', 'VIDEO'));

CREATE OR REPLACE FUNCTION classroom_set_whiteboard_background(
  p_session_id UUID, p_url TEXT, p_type TEXT, p_page INTEGER DEFAULT 0
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT classroom_can_manage(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN';
  END IF;
  IF p_type NOT IN ('NONE', 'IMAGE', 'PDF', 'VIDEO') THEN
    RAISE EXCEPTION 'INVALID_TYPE';
  END IF;

  PERFORM classroom_ensure_whiteboard(p_session_id);

  UPDATE classroom_whiteboard_boards
  SET background_url = p_url, background_type = p_type, background_page = p_page
  WHERE session_id = p_session_id;

  INSERT INTO classroom_whiteboard_strokes (board_id, session_id, user_id, action, stroke)
  SELECT id, p_session_id, auth.uid(), 'CLEAR', NULL
  FROM classroom_whiteboard_boards WHERE session_id = p_session_id;
END;
$$;

REVOKE ALL ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION classroom_set_whiteboard_background(UUID, TEXT, TEXT, INTEGER) TO authenticated;
