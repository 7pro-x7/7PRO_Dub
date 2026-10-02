-- ============================================================================
-- Add Media Aspect Ratio Support to Exercise Questions
-- ============================================================================

-- Add aspect ratio column to store video aspect ratio (width / height)
-- Examples: 16/9 = 1.78, 9/16 = 0.56, 1/1 = 1.0
ALTER TABLE exercise_questions ADD COLUMN IF NOT EXISTS media_aspect_ratio FLOAT;
-- Create index for queries filtering by aspect ratio
CREATE INDEX IF NOT EXISTS idx_exercise_questions_aspect_ratio ON exercise_questions(media_aspect_ratio);
-- Comment explaining the field
COMMENT ON COLUMN exercise_questions.media_aspect_ratio IS 'Video aspect ratio (width / height). E.g., 1.78 for 16:9, 0.56 for 9:16, 1.0 for 1:1';
