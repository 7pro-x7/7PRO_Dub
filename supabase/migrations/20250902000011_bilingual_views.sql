-- ============================================================================
-- Bilingual views: auto-select the correct language at query time.
--
-- Each view wraps the original table and uses t_en() to return the right
-- translation based on the user's language preference in app_settings.
-- When a translation is missing, the original (source) text is returned.
-- ============================================================================

-- ── courses ──────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_courses AS
SELECT
    id, teacher_id, category_id,
    t_en(title, title_en, title_ar)         AS title,
    t_en(subtitle, subtitle_en, subtitle_ar) AS subtitle,
    t_en(description, description_en, description_ar) AS description,
    thumbnail_url, level, base_price, base_currency, is_free,
    status, rejection_note,
    certificate_enabled, certificate_min_percent,
    views_count, enrollments_count,
    rating_avg, rating_count, is_featured, created_at,
    teacher
FROM public.courses;
-- ── lessons ──────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_lessons AS
SELECT
    id, course_id, section_id,
    t_en(title, title_en, title_ar)         AS title,
    t_en(description, description_en, description_ar) AS description,
    kind, video_url, document_url,
    t_en(content, content_en, content_ar)   AS content,
    duration_seconds, sort_order, is_preview
FROM public.lessons;
-- ── course_sections ──────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_course_sections AS
SELECT
    id, course_id,
    t_en(title, title_en, title_ar)         AS title,
    t_en(subtitle, subtitle_en, subtitle_ar) AS subtitle,
    sort_order
FROM public.course_sections;
-- ── categories ───────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_categories AS
SELECT
    id,
    t_en(name, name_en, name_ar)            AS name
FROM public.categories;
-- ── cms_banners ──────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_cms_banners AS
SELECT
    id,
    t_en(title, title_en, title_ar)         AS title,
    t_en(subtitle, subtitle_en, subtitle_ar) AS subtitle,
    t_en(cta_label, cta_label_en, cta_label_ar) AS cta_label,
    target, image_url, sort_order, is_enabled, created_at
FROM public.cms_banners;
-- ── exercises ────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_exercises AS
SELECT
    id, teacher_id, status,
    t_en(title, title_en, title_ar)         AS title,
    t_en(description, description_en, description_ar) AS description,
    question_count, created_at, updated_at
FROM public.exercises;
-- ── placement_tests ──────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_placement_tests AS
SELECT
    id, owner_id, kind, status,
    t_en(title, title_en, title_ar)         AS title,
    t_en(description, description_en, description_ar) AS description,
    is_adaptive, question_count, time_limit_seconds, passing_score,
    created_at
FROM public.placement_tests;
-- ── test_questions ───────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW public.v_test_questions AS
SELECT
    id, test_id, kind, skill, difficulty,
    t_en(prompt, prompt_en, prompt_ar)      AS prompt,
    t_en(options, options_en, options_ar)   AS options,
    correct_indexes, correct_text,
    t_en(explanation, explanation_en, explanation_ar) AS explanation,
    points,
    media_image_url, media_audio_url, media_video_url,
    created_at
FROM public.test_questions;
-- ============================================================================
-- Permissions: allow authenticated users to query the views.
-- ============================================================================
GRANT SELECT ON public.v_courses TO authenticated;
GRANT SELECT ON public.v_lessons TO authenticated;
GRANT SELECT ON public.v_course_sections TO authenticated;
GRANT SELECT ON public.v_categories TO authenticated;
GRANT SELECT ON public.v_cms_banners TO authenticated;
GRANT SELECT ON public.v_exercises TO authenticated;
GRANT SELECT ON public.v_placement_tests TO authenticated;
GRANT SELECT ON public.v_test_questions TO authenticated;
-- Also grant to anon (for course browsing before sign-in)
GRANT SELECT ON public.v_courses TO anon;
GRANT SELECT ON public.v_lessons TO anon;
GRANT SELECT ON public.v_course_sections TO anon;
GRANT SELECT ON public.v_categories TO anon;
GRANT SELECT ON public.v_cms_banners TO anon;
