-- ============================================================================
-- Add bilingual (_ar / _en) columns to all owner/admin-created content tables.
--
-- Each text field gets two new columns: `field_ar` and `field_en`.
-- The original column stays as-is (acts as the "source" language).
-- A helper function `current_lang()` returns 'ar' or 'en' so queries can
-- pick the right column at read time.
-- ============================================================================

-- Helper: returns the current user's language preference from app_settings
-- Falls back to 'en' if nothing is set.
CREATE OR REPLACE FUNCTION public.current_lang()
RETURNS text
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(
    (SELECT value::text FROM public.app_settings WHERE key = 'ui.language'),
    'en'
  );
$$;
-- Helper: given source text + ar + en, returns the right one for the session language.
-- Falls back to the source text when the translation is null.
CREATE OR REPLACE FUNCTION public.t_en(source text, val_en text, val_ar text)
RETURNS text
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT CASE
    WHEN current_lang() = 'ar' THEN COALESCE(val_ar, source)
    ELSE COALESCE(val_en, source)
  END;
$$;
-- ── courses ──────────────────────────────────────────────────────────────────
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS title_ar text;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS subtitle_ar text;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS subtitle_en text;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS description_ar text;
ALTER TABLE public.courses ADD COLUMN IF NOT EXISTS description_en text;
-- ── lessons ──────────────────────────────────────────────────────────────────
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS title_ar text;
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS description_ar text;
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS description_en text;
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS content_ar text;
ALTER TABLE public.lessons ADD COLUMN IF NOT EXISTS content_en text;
-- ── course_sections ──────────────────────────────────────────────────────────
ALTER TABLE public.course_sections ADD COLUMN IF NOT EXISTS title_ar text;
ALTER TABLE public.course_sections ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.course_sections ADD COLUMN IF NOT EXISTS subtitle_ar text;
ALTER TABLE public.course_sections ADD COLUMN IF NOT EXISTS subtitle_en text;
-- ── categories ───────────────────────────────────────────────────────────────
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS name_ar text;
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS name_en text;
-- ── cms_banners ──────────────────────────────────────────────────────────────
ALTER TABLE public.cms_banners ADD COLUMN IF NOT EXISTS title_ar text;
ALTER TABLE public.cms_banners ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.cms_banners ADD COLUMN IF NOT EXISTS subtitle_ar text;
ALTER TABLE public.cms_banners ADD COLUMN IF NOT EXISTS subtitle_en text;
ALTER TABLE public.cms_banners ADD COLUMN IF NOT EXISTS cta_label_ar text;
ALTER TABLE public.cms_banners ADD COLUMN IF NOT EXISTS cta_label_en text;
-- ── exercises ────────────────────────────────────────────────────────────────
ALTER TABLE public.exercises ADD COLUMN IF NOT EXISTS title_ar text;
ALTER TABLE public.exercises ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.exercises ADD COLUMN IF NOT EXISTS description_ar text;
ALTER TABLE public.exercises ADD COLUMN IF NOT EXISTS description_en text;
-- ── exercise_questions ───────────────────────────────────────────────────────
ALTER TABLE public.exercise_questions ADD COLUMN IF NOT EXISTS question_text_ar text;
ALTER TABLE public.exercise_questions ADD COLUMN IF NOT EXISTS question_text_en text;
ALTER TABLE public.exercise_questions ADD COLUMN IF NOT EXISTS options_ar jsonb;
ALTER TABLE public.exercise_questions ADD COLUMN IF NOT EXISTS options_en jsonb;
ALTER TABLE public.exercise_questions ADD COLUMN IF NOT EXISTS correct_answer_ar text;
ALTER TABLE public.exercise_questions ADD COLUMN IF NOT EXISTS correct_answer_en text;
-- ── placement_tests ──────────────────────────────────────────────────────────
ALTER TABLE public.placement_tests ADD COLUMN IF NOT EXISTS title_ar text;
ALTER TABLE public.placement_tests ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.placement_tests ADD COLUMN IF NOT EXISTS description_ar text;
ALTER TABLE public.placement_tests ADD COLUMN IF NOT EXISTS description_en text;
-- ── test_questions ───────────────────────────────────────────────────────────
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS prompt_ar text;
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS prompt_en text;
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS options_ar jsonb;
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS options_en jsonb;
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS explanation_ar text;
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS explanation_en text;
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS correct_text_ar text[];
ALTER TABLE public.test_questions ADD COLUMN IF NOT EXISTS correct_text_en text[];
