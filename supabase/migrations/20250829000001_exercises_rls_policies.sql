-- ============================================================================
-- Row Level Security (RLS) Policies for Exercises System
-- ============================================================================

-- ============================================================================
-- 1. EXERCISES TABLE POLICIES
-- ============================================================================

-- Enable RLS
ALTER TABLE exercises ENABLE ROW LEVEL SECURITY;
-- Policy: Anyone can view published exercises
CREATE POLICY "View published exercises"
  ON exercises FOR SELECT
  USING (is_published = true);
-- Policy: Teachers can view their own exercises
CREATE POLICY "Teachers view own exercises"
  ON exercises FOR SELECT
  USING (auth.uid() = teacher_id);
-- Policy: Owners and Admins can view all exercises
CREATE POLICY "Owners and Admins view all exercises"
  ON exercises FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- Policy: Teachers can create exercises (only TEACHER_EXERCISE)
CREATE POLICY "Teachers create exercises"
  ON exercises FOR INSERT
  WITH CHECK (
    auth.uid() = teacher_id
    AND type = 'TEACHER_EXERCISE'
    AND (
      SELECT role FROM profiles WHERE id = auth.uid()
    ) IN ('TEACHER', 'OWNER', 'ADMIN')
  );
-- Policy: Teachers can update their own exercises
CREATE POLICY "Teachers update own exercises"
  ON exercises FOR UPDATE
  USING (auth.uid() = teacher_id)
  WITH CHECK (auth.uid() = teacher_id);
-- Policy: Teachers can delete their own exercises
CREATE POLICY "Teachers delete own exercises"
  ON exercises FOR DELETE
  USING (auth.uid() = teacher_id);
-- Policy: Owners and Admins can create placement tests (teacher_id IS NULL)
CREATE POLICY "Owners and Admins create placement tests"
  ON exercises FOR INSERT
  WITH CHECK (
    teacher_id IS NULL
    AND type = 'PLACEMENT_TEST'
    AND (
      SELECT role FROM profiles WHERE id = auth.uid()
    ) IN ('OWNER', 'ADMIN')
  );
-- Policy: Owners and Admins can update all exercises
CREATE POLICY "Owners and Admins update all exercises"
  ON exercises FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- Policy: Owners and Admins can delete all exercises
CREATE POLICY "Owners and Admins delete all exercises"
  ON exercises FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- ============================================================================
-- 2. EXERCISE_QUESTIONS TABLE POLICIES
-- ============================================================================

ALTER TABLE exercise_questions ENABLE ROW LEVEL SECURITY;
-- Policy: Anyone can view questions from published exercises
CREATE POLICY "View questions from published exercises"
  ON exercise_questions FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = exercise_questions.exercise_id
      AND exercises.is_published = true
    )
  );
-- Policy: Teachers can view questions from their exercises
CREATE POLICY "Teachers view own exercise questions"
  ON exercise_questions FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = exercise_questions.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Owners and Admins can view all questions
CREATE POLICY "Owners and Admins view all questions"
  ON exercise_questions FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- Policy: Teachers can insert questions to their exercises
CREATE POLICY "Teachers insert questions"
  ON exercise_questions FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = exercise_questions.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Teachers can update questions in their exercises
CREATE POLICY "Teachers update exercise questions"
  ON exercise_questions FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = exercise_questions.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Teachers can delete questions from their exercises
CREATE POLICY "Teachers delete exercise questions"
  ON exercise_questions FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = exercise_questions.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Owners and Admins can manage all questions
CREATE POLICY "Owners and Admins manage all questions"
  ON exercise_questions FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
CREATE POLICY "Owners and Admins update all questions"
  ON exercise_questions FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
CREATE POLICY "Owners and Admins delete all questions"
  ON exercise_questions FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- ============================================================================
-- 3. STUDENT_RESULTS TABLE POLICIES
-- ============================================================================

ALTER TABLE student_results ENABLE ROW LEVEL SECURITY;
-- Policy: Students can view their own results (non-hidden)
CREATE POLICY "Students view own results"
  ON student_results FOR SELECT
  USING (
    auth.uid() = student_id
    AND is_hidden = false
  );
-- Policy: Students can insert their own results
CREATE POLICY "Students submit results"
  ON student_results FOR INSERT
  WITH CHECK (auth.uid() = student_id);
-- Policy: Students can update their own results
CREATE POLICY "Students update own results"
  ON student_results FOR UPDATE
  USING (auth.uid() = student_id)
  WITH CHECK (auth.uid() = student_id);
-- Policy: Teachers can view results for their exercises
CREATE POLICY "Teachers view exercise results"
  ON student_results FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = student_results.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Teachers can update (hide/show) results for their exercises
CREATE POLICY "Teachers manage exercise results"
  ON student_results FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = student_results.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Teachers can delete results from their exercises
CREATE POLICY "Teachers delete exercise results"
  ON student_results FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM exercises
      WHERE exercises.id = student_results.exercise_id
      AND exercises.teacher_id = auth.uid()
    )
  );
-- Policy: Owners and Admins can view all results
CREATE POLICY "Owners and Admins view all results"
  ON student_results FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- Policy: Owners and Admins can manage all results
CREATE POLICY "Owners and Admins manage all results"
  ON student_results FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
CREATE POLICY "Owners and Admins delete all results"
  ON student_results FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role IN ('OWNER', 'ADMIN')
    )
  );
-- ============================================================================
-- 4. STORAGE POLICIES FOR MEDIA
-- ============================================================================

-- These should be configured via Supabase dashboard:
-- Bucket: exercise_media
-- Allow public read (for viewing media in exercises)
-- Allow authenticated write (users upload their media)
-- Authenticated users can only delete their own files;
