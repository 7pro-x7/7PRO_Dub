-- ============================================================================
-- Add level column to teacher_subscriptions
-- Stores the proficiency level / class tier (e.g. متندى, أطفال, متقدمة)
-- ============================================================================

ALTER TABLE teacher_subscriptions
  ADD COLUMN IF NOT EXISTS level TEXT;
COMMENT ON COLUMN teacher_subscriptions.level IS 'Proficiency level or class tier for this subscription group';
