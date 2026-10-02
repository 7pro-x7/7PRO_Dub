-- A public link is a capability, so it should stop being one the moment the class
-- it opens is over. Ending a session now mints a fresh token, which silently
-- retires every copy of the old link that was pasted into a group chat.
CREATE OR REPLACE FUNCTION classroom_rotate_guest_token_on_end()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NEW.status = 'ENDED' AND OLD.status IS DISTINCT FROM 'ENDED' THEN
    NEW.guest_token := gen_random_uuid();
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_classroom_rotate_guest_token ON classroom_sessions;
CREATE TRIGGER trg_classroom_rotate_guest_token
  BEFORE UPDATE ON classroom_sessions
  FOR EACH ROW EXECUTE FUNCTION classroom_rotate_guest_token_on_end();

-- Guests occupy the same seats students do, and 30 is low once a link is shared
-- with a whole group. The per-session value stays editable; only the default and
-- the sessions that have not happened yet are raised.
ALTER TABLE classroom_sessions ALTER COLUMN max_participants SET DEFAULT 100;

UPDATE classroom_sessions
SET max_participants = 100
WHERE max_participants = 30
  AND status IN ('SCHEDULED', 'LIVE');;
