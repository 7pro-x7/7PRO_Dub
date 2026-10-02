-- ============================================================================
-- Virtual Classroom — make the conferencing domain configurable
--
-- Problem found on a real device: every session was pinned to the PUBLIC
-- meet.jit.si server, which now enforces moderator authentication. Joining
-- shows "لم يبدأ المؤتمر بعد… WAIT FOR MODERATOR / أنا المضيف", and pressing
-- "أنا المضيف" hands the user off to an external browser/Jitsi app to sign
-- in. That is a SERVER-SIDE policy of meet.jit.si — no client flag can turn
-- it off, so the only real fixes are (a) point at a server that permits
-- anonymous room creation, or (b) run/rent your own Jitsi (self-hosted or
-- 8x8 JaaS) and issue JWTs via the classroom-jitsi-token edge function.
--
-- This migration stops the domain from being hard-coded so operators can
-- switch it without an app release, and moves the default off the gated
-- public server.
--
-- IMPORTANT: meet.ffmuc.net is a free community-run instance with no SLA and
-- no data-processing agreement. It is set here as an interim default so
-- classes work immediately without the moderator wall; for a production
-- education product handling minors' video, replace it with your own
-- self-hosted Jitsi or a paid 8x8 JaaS tenant and re-run:
--     UPDATE classroom_settings SET jitsi_domain = '<your-domain>';
-- ============================================================================

CREATE TABLE IF NOT EXISTS classroom_settings (
  id BOOLEAN PRIMARY KEY DEFAULT true CHECK (id),
  jitsi_domain TEXT NOT NULL DEFAULT 'meet.ffmuc.net',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO classroom_settings (id) VALUES (true) ON CONFLICT (id) DO NOTHING;

ALTER TABLE classroom_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS classroom_settings_read ON classroom_settings;
CREATE POLICY classroom_settings_read ON classroom_settings
  FOR SELECT TO authenticated USING (true);

-- Only staff holding classroom.manage may change the conferencing domain.
DROP POLICY IF EXISTS classroom_settings_write ON classroom_settings;
CREATE POLICY classroom_settings_write ON classroom_settings
  FOR UPDATE TO authenticated
  USING (classroom_is_staff_with('classroom.manage'))
  WITH CHECK (classroom_is_staff_with('classroom.manage'));

-- New sessions pick the configured domain up automatically.
CREATE OR REPLACE FUNCTION classroom_default_jitsi_domain()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.jitsi_domain IS NULL OR NEW.jitsi_domain = '' OR NEW.jitsi_domain = 'meet.jit.si' THEN
    SELECT jitsi_domain INTO NEW.jitsi_domain FROM classroom_settings WHERE id = true;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_classroom_sessions_domain ON classroom_sessions;
CREATE TRIGGER trg_classroom_sessions_domain
  BEFORE INSERT ON classroom_sessions
  FOR EACH ROW EXECUTE FUNCTION classroom_default_jitsi_domain();

-- Move every existing not-yet-finished session off the gated public server so
-- already-scheduled classes stop hitting the moderator wall.
UPDATE classroom_sessions
SET jitsi_domain = (SELECT jitsi_domain FROM classroom_settings WHERE id = true)
WHERE jitsi_domain = 'meet.jit.si'
  AND status IN ('SCHEDULED', 'LIVE');
;
