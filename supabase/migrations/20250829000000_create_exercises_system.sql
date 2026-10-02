-- ============================================================================
-- 7PRO Tests & Exercises System - Database Migrations
-- ============================================================================

-- ============================================================================
-- 1. EXERCISES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS exercises (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type TEXT NOT NULL CHECK (type IN ('PLACEMENT_TEST', 'TEACHER_EXERCISE')),
  title TEXT NOT NULL,
  description TEXT,
  teacher_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  is_published BOOLEAN DEFAULT false,
  language TEXT DEFAULT 'AR' CHECK (language IN ('AR', 'EN')),
  duration_minutes INTEGER,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  
  -- Placement tests (NULL teacher_id)
  -- Teacher exercises (NOT NULL teacher_id)
  CONSTRAINT placement_test_no_teacher CHECK (
    (type = 'PLACEMENT_TEST' AND teacher_id IS NULL) OR
    (type = 'TEACHER_EXERCISE' AND teacher_id IS NOT NULL)
  )
);
CREATE INDEX idx_exercises_type ON exercises(type);
CREATE INDEX idx_exercises_teacher_id ON exercises(teacher_id);
CREATE INDEX idx_exercises_is_published ON exercises(is_published);
CREATE INDEX idx_exercises_created_at ON exercises(created_at DESC);
-- ============================================================================
-- 2. EXERCISE QUESTIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS exercise_questions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  exercise_id UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
  question_text TEXT NOT NULL,
  question_type TEXT NOT NULL CHECK (
    question_type IN ('MULTIPLE_CHOICE', 'SHORT_ANSWER', 'ESSAY')
  ),
  media_url TEXT,
  media_type TEXT CHECK (media_type IN ('image', 'video', 'audio')),
  options JSONB, -- For MULTIPLE_CHOICE: ["option1", "option2", ...]
  correct_answer TEXT, -- For MULTIPLE_CHOICE or SHORT_ANSWER
  order_index INTEGER NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  
  CONSTRAINT valid_media CHECK (
    (media_url IS NULL AND media_type IS NULL) OR
    (media_url IS NOT NULL AND media_type IS NOT NULL)
  ),
  CONSTRAINT valid_multiple_choice CHECK (
    (question_type != 'MULTIPLE_CHOICE') OR (options IS NOT NULL)
  )
);
CREATE INDEX idx_exercise_questions_exercise_id ON exercise_questions(exercise_id);
CREATE INDEX idx_exercise_questions_order ON exercise_questions(exercise_id, order_index);
-- ============================================================================
-- 3. STUDENT RESULTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS student_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  exercise_id UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
  submitted_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  score NUMERIC(5, 2),
  answers JSONB NOT NULL DEFAULT '[]'::jsonb, -- Store all answers with media URLs
  is_hidden BOOLEAN DEFAULT false, -- Hide from UI only, data kept in DB
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  
  UNIQUE(student_id, exercise_id) -- One result per student per exercise
);
CREATE INDEX idx_student_results_student_id ON student_results(student_id);
CREATE INDEX idx_student_results_exercise_id ON student_results(exercise_id);
CREATE INDEX idx_student_results_is_hidden ON student_results(is_hidden);
CREATE INDEX idx_student_results_submitted_at ON student_results(submitted_at DESC);
-- ============================================================================
-- 4. STORAGE BUCKET FOR MEDIA (via Supabase dashboard)
-- ============================================================================
-- Create bucket manually in Supabase: "exercise_media"
-- Settings:
--   - Public: true
--   - Max upload size: 50MB
--   - Allowed MIME types: image/*, video/mp4, audio/*

-- ============================================================================
-- 5. UPDATE PROFILES TABLE (if role column doesn't exist)
-- ============================================================================
-- Run this only if profiles.role doesn't exist
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'STUDENT' 
  CHECK (role IN ('STUDENT', 'TEACHER', 'OWNER', 'ADMIN'));
CREATE INDEX IF NOT EXISTS idx_profiles_role ON profiles(role);
-- ============================================================================
-- 6. TIMESTAMPS TRIGGERS
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ language 'plpgsql';
DROP TRIGGER IF EXISTS update_exercises_updated_at ON exercises;
CREATE TRIGGER update_exercises_updated_at
  BEFORE UPDATE ON exercises
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();
DROP TRIGGER IF EXISTS update_student_results_updated_at ON student_results;
CREATE TRIGGER update_student_results_updated_at
  BEFORE UPDATE ON student_results
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();
-- ============================================================================
-- 7. CASCADE DELETE FUNCTION
-- ============================================================================
CREATE OR REPLACE FUNCTION delete_exercise_cascade()
RETURNS TRIGGER AS $$
BEGIN
  DELETE FROM exercise_questions WHERE exercise_id = OLD.id;
  DELETE FROM student_results WHERE exercise_id = OLD.id;
  RETURN OLD;
END;
$$ language 'plpgsql';
DROP TRIGGER IF EXISTS delete_exercise_cascade_trigger ON exercises;
CREATE TRIGGER delete_exercise_cascade_trigger
  BEFORE DELETE ON exercises
  FOR EACH ROW
  EXECUTE FUNCTION delete_exercise_cascade();
