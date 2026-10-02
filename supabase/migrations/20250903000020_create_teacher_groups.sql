-- ============================================================================
-- Independent teacher_groups table
-- Allows teachers to create empty groups before adding people.
-- ============================================================================

CREATE TABLE IF NOT EXISTS teacher_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  level TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(teacher_id, name)
);
CREATE INDEX idx_teacher_groups_teacher ON teacher_groups(teacher_id);
ALTER TABLE teacher_groups ENABLE ROW LEVEL SECURITY;
-- Teachers manage their own groups
CREATE POLICY "teacher_groups_self_access"
  ON teacher_groups FOR ALL
  USING (auth.uid() = teacher_id);
-- Staff (admin/owner) can read all groups
CREATE POLICY "teacher_groups_staff_read"
  ON teacher_groups FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
        AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
GRANT SELECT, INSERT, UPDATE, DELETE ON teacher_groups TO authenticated;
