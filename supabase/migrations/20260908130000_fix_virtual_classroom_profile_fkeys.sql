-- ============================================================================
-- Fix: Virtual Classroom FKs must point to public.profiles, not auth.users
--
-- Root cause of the "Something went wrong" / حدث خطأ ما error on every
-- classroom list screen:
--
--   20260908120000_create_virtual_classroom.sql declared teacher_id,
--   user_id, sender_id (and the other person-referencing columns) as
--   `REFERENCES auth.users(id)`. Every other table in this app that the
--   client embeds a profile onto (courses, payouts, reviews, audit_logs, ...)
--   instead references `profiles(id)` — that's what lets PostgREST resolve
--   embed hints like `profiles!courses_teacher_id_fkey(...)`.
--
--   ClassroomRepository.kt embeds the same way, e.g.
--     teacher:profiles!classroom_sessions_teacher_id_fkey(id, full_name, avatar_url)
--
--   But because classroom_sessions.teacher_id's actual foreign key points at
--   auth.users, not profiles, PostgREST cannot find a relationship between
--   classroom_sessions and profiles under that constraint name and fails the
--   request with a PGRST200 "Could not find a relationship" error — which the
--   app then shows as the generic "Something went wrong" screen for every
--   session list (student, teacher, and staff).
--
-- Fix: re-point every classroom person-column at profiles(id) instead, using
-- the exact same (auto-generated) constraint names the Kotlin embeds already
-- reference, so no client code needs to change. Safe to run whether or not
-- 20260908120000 already applied on the live project — every DROP is
-- IF EXISTS and every table/column already exists by the time this runs.
--
-- Requires: every auth.users row already has a matching profiles row (true
-- in this app — see 20250902000001_fix_teacher_profile_trigger_for_service_role.sql).
-- ============================================================================

DO $$
DECLARE
  fk RECORD;
BEGIN
  FOR fk IN
    SELECT * FROM (VALUES
      ('classroom_sessions',           'teacher_id',  'classroom_sessions_teacher_id_fkey',           true),
      ('classroom_sessions',           'created_by',  'classroom_sessions_created_by_fkey',           false),
      ('classroom_participants',       'user_id',     'classroom_participants_user_id_fkey',          true),
      ('classroom_participants',       'invited_by',  'classroom_participants_invited_by_fkey',       false),
      ('classroom_attendance_events',  'user_id',     'classroom_attendance_events_user_id_fkey',     true),
      ('classroom_chat_messages',      'sender_id',   'classroom_chat_messages_sender_id_fkey',        true),
      ('classroom_hand_raise_events',  'user_id',     'classroom_hand_raise_events_user_id_fkey',     true),
      ('classroom_hand_raise_events',  'actor_id',    'classroom_hand_raise_events_actor_id_fkey',    false),
      ('classroom_whiteboard_boards',  'created_by',  'classroom_whiteboard_boards_created_by_fkey',  false),
      ('classroom_whiteboard_strokes', 'user_id',     'classroom_whiteboard_strokes_user_id_fkey',    true),
      ('classroom_recordings',         'started_by',  'classroom_recordings_started_by_fkey',         false)
    ) AS t(table_name, column_name, constraint_name, cascade_delete)
  LOOP
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', fk.table_name, fk.constraint_name);
    EXECUTE format(
      'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES profiles(id)%s',
      fk.table_name,
      fk.constraint_name,
      fk.column_name,
      CASE WHEN fk.cascade_delete THEN ' ON DELETE CASCADE' ELSE '' END
    );
  END LOOP;
END $$;
