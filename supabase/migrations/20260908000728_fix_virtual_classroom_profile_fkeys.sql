-- Re-point every classroom person-column FK from auth.users to public.profiles.
-- Root cause of "Something went wrong" on the classroom list screens: the
-- Kotlin client embeds profiles via e.g.
--   teacher:profiles!classroom_sessions_teacher_id_fkey(...)
-- which PostgREST can only resolve if that named constraint actually points
-- at profiles (the convention every other table in this app already follows
-- for courses/payouts/reviews/audit_logs). Safe/idempotent: every DROP is
-- IF EXISTS. Requires every auth.users row to already have a matching
-- profiles row, which the existing profile-creation trigger guarantees.

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
END $$;;
