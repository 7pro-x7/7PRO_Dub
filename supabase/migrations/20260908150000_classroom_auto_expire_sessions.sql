-- ============================================================================
-- Virtual Classroom — automatic expiry of missed SCHEDULED sessions
--
-- Bug: a session that stays 'SCHEDULED' forever (the teacher never started it
-- and never canceled it either) never leaves the SCHEDULED/LIVE lists
-- (ClassroomRepository.VISIBLE_STATUSES), and classroom_join() only ever put
-- a *time* restriction on a non-manager caller — the teacher/staff manager
-- path had no time check at all, so a slot that was simply missed stayed
-- joinable and startable indefinitely.
--
-- Fix, in three parts:
--   1. 'EXPIRED' becomes a real value of classroom_sessions.status.
--   2. classroom_join() now blocks entry into a session that is EXPIRED, or
--      that is still SCHEDULED but has already passed the same 30-minute
--      grace period it already used to cut off a non-manager below — this
--      check is computed live, so it blocks correctly even a moment before
--      the periodic sweep in part 3 has run. This applies to EVERYONE,
--      including the manager path, which is the actual behavior change: a
--      slot that was missed entirely must be recreated, not resumed.
--   3. classroom_expire_overdue_sessions() flips the stored status on every
--      overdue SCHEDULED row so it actually leaves classroom_sessions.status
--      = 'SCHEDULED', which is what makes it disappear from every list query
--      (all of which already filter to status IN ('SCHEDULED', 'LIVE') —
--      exactly the same way an ENDED or CANCELED session already disappears
--      today, no client-side change needed). Wired into the existing
--      maintenance edge function next to expire_subscriptions() and
--      classroom_cleanup_old_logs(), so it runs on the same schedule that
--      already drives this project's other time-based housekeeping — no new
--      infrastructure required. Project owners who additionally have pg_cron
--      available can schedule it more tightly; see the note near the end of
--      this file.
--
-- A PL/pgSQL function call is a single implicit transaction: if it raised an
-- exception, any UPDATE it ran earlier in the same call would be rolled back
-- with it. That is why classroom_join() below never tries to write the
-- EXPIRED status itself before raising SESSION_EXPIRED — it only computes
-- the same condition classroom_expire_overdue_sessions() persists, so the
-- two can never disagree about what counts as expired.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Widen the status CHECK constraint to allow 'EXPIRED'. Looked up
--    dynamically instead of assuming the auto-generated constraint name, so
--    this still works correctly even if the live project's constraint was
--    ever renamed.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
  v_conname TEXT;
BEGIN
  SELECT conname INTO v_conname
  FROM pg_constraint
  WHERE conrelid = 'classroom_sessions'::regclass
    AND contype = 'c'
    AND pg_get_constraintdef(oid) ILIKE '%status%SCHEDULED%';

  IF v_conname IS NOT NULL THEN
    EXECUTE format('ALTER TABLE classroom_sessions DROP CONSTRAINT %I', v_conname);
  END IF;

  ALTER TABLE classroom_sessions
    ADD CONSTRAINT classroom_sessions_status_check
    CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELED', 'EXPIRED'));
END $$;

-- Speeds up both the sweep's WHERE clause and any future admin/report query
-- that needs "which SCHEDULED sessions are closest to expiring".
CREATE INDEX IF NOT EXISTS idx_classroom_sessions_scheduled_pending
  ON classroom_sessions(scheduled_end)
  WHERE status = 'SCHEDULED';

-- ----------------------------------------------------------------------------
-- 2. classroom_join() — add the computed EXPIRED guard. Everything else is
--    unchanged from the version in 20260908120400_virtual_classroom_security_
--    hardening.sql; the signature is identical so no grant/revoke needs to be
--    repeated.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_join(p_session_id UUID)
RETURNS TABLE (
  room_name TEXT,
  room_password TEXT,
  jitsi_domain TEXT,
  role_in_session TEXT,
  is_moderator BOOLEAN,
  display_name TEXT
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_session RECORD;
  v_participant RECORD;
  v_is_manager BOOLEAN;
  v_live_count INTEGER;
  v_name TEXT;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  v_is_manager := classroom_can_manage(p_session_id);

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

  -- A SCHEDULED session that never started and is already past the same
  -- 30-minute grace period the non-manager branch below has always enforced
  -- is expired, whether or not classroom_expire_overdue_sessions() has run
  -- yet — checked live here so entry is never allowed to slip through in the
  -- gap between two sweeps. Unlike every check below, this one is NOT
  -- skipped for a manager: a missed slot must be recreated, not resumed.
  IF v_session.status = 'EXPIRED'
     OR (v_session.status = 'SCHEDULED' AND v_session.scheduled_end + INTERVAL '30 minutes' <= now())
  THEN
    RAISE EXCEPTION 'SESSION_EXPIRED';
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

  RETURN QUERY
  SELECT
    v_session.room_name,
    v_session.room_password,
    v_session.jitsi_domain,
    CASE WHEN v_session.teacher_id = auth.uid() THEN 'TEACHER'
         WHEN v_is_manager THEN 'MODERATOR'
         ELSE 'STUDENT' END,
    (v_session.teacher_id = auth.uid() OR v_is_manager),
    v_name;
END;
$$;

-- ----------------------------------------------------------------------------
-- 3. classroom_expire_overdue_sessions() — the sweep. service_role only,
--    same posture as classroom_cleanup_old_logs(): never callable by a
--    regular signed-in user, and meant to be driven by the maintenance edge
--    function (or, optionally, pg_cron — see note below).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION classroom_expire_overdue_sessions()
RETURNS TABLE (expired_count BIGINT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_ids UUID[];
BEGIN
  SELECT array_agg(id) INTO v_ids
  FROM classroom_sessions
  WHERE status = 'SCHEDULED'
    AND scheduled_end + INTERVAL '30 minutes' <= now();

  IF v_ids IS NULL THEN
    RETURN QUERY SELECT 0::BIGINT;
    RETURN;
  END IF;

  UPDATE classroom_sessions
  SET status = 'EXPIRED'
  WHERE id = ANY(v_ids);

  -- Best-effort courtesy notice to whoever was invited (mirrors
  -- classroom_cancel_session's notification). Not essential to the fix, so a
  -- notifications problem must never take the status update down with it.
  BEGIN
    INSERT INTO notifications (user_id, kind, title, body, data)
    SELECT cp.user_id, 'CLASSROOM_EXPIRED', 'لم تبدأ الحصة في موعدها', cs.title,
           jsonb_build_object('classroom_session_id', cs.id)
    FROM classroom_participants cp
    JOIN classroom_sessions cs ON cs.id = cp.session_id
    WHERE cs.id = ANY(v_ids) AND cp.status != 'KICKED';
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;

  RETURN QUERY SELECT array_length(v_ids, 1)::BIGINT;
END;
$$;

REVOKE ALL ON FUNCTION classroom_expire_overdue_sessions() FROM PUBLIC;
REVOKE ALL ON FUNCTION classroom_expire_overdue_sessions() FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION classroom_expire_overdue_sessions() TO service_role;

-- One-time backfill: apply the same rule immediately to whatever is already
-- sitting overdue on the live project right now, instead of only preventing
-- new cases from this point forward.
DO $$
BEGIN
  PERFORM classroom_expire_overdue_sessions();
END $$;

-- ----------------------------------------------------------------------------
-- Optional: if the project has the pg_cron extension available, this keeps
-- the list/UI-facing side of the fix (part 3) tight to a 1-minute cadence
-- instead of whatever interval the maintenance function is polled on. Entry
-- is already blocked instantly regardless (part 2), so this is a UX nicety,
-- not a security requirement, and is deliberately NOT run automatically by
-- this migration (same reasoning as classroom_send_start_reminders above):
--
--   SELECT cron.schedule('classroom-expire-overdue', '*/1 * * * *',
--     $$SELECT classroom_expire_overdue_sessions()$$);
-- ----------------------------------------------------------------------------
