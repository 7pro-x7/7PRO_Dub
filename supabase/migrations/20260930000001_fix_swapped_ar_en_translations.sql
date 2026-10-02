-- translate-content used to write the source text into *_ar and the translation into *_en (inverted).
-- Swap back only where provably inverted: *_ar has no Arabic letters while *_en does.
UPDATE public.courses SET title_ar = title_en, title_en = title_ar WHERE title_ar IS NOT NULL AND title_ar !~ '[\u0600-\u06FF]' AND title_en ~ '[\u0600-\u06FF]';
UPDATE public.courses SET subtitle_ar = subtitle_en, subtitle_en = subtitle_ar WHERE subtitle_ar IS NOT NULL AND subtitle_ar !~ '[\u0600-\u06FF]' AND subtitle_en ~ '[\u0600-\u06FF]';
UPDATE public.courses SET description_ar = description_en, description_en = description_ar WHERE description_ar IS NOT NULL AND description_ar !~ '[\u0600-\u06FF]' AND description_en ~ '[\u0600-\u06FF]';
UPDATE public.lessons SET title_ar = title_en, title_en = title_ar WHERE title_ar IS NOT NULL AND title_ar !~ '[\u0600-\u06FF]' AND title_en ~ '[\u0600-\u06FF]';
UPDATE public.lessons SET description_ar = description_en, description_en = description_ar WHERE description_ar IS NOT NULL AND description_ar !~ '[\u0600-\u06FF]' AND description_en ~ '[\u0600-\u06FF]';
UPDATE public.lessons SET content_ar = content_en, content_en = content_ar WHERE content_ar IS NOT NULL AND content_ar !~ '[\u0600-\u06FF]' AND content_en ~ '[\u0600-\u06FF]';
UPDATE public.course_sections SET title_ar = title_en, title_en = title_ar WHERE title_ar IS NOT NULL AND title_ar !~ '[\u0600-\u06FF]' AND title_en ~ '[\u0600-\u06FF]';
UPDATE public.course_sections SET subtitle_ar = subtitle_en, subtitle_en = subtitle_ar WHERE subtitle_ar IS NOT NULL AND subtitle_ar !~ '[\u0600-\u06FF]' AND subtitle_en ~ '[\u0600-\u06FF]';
UPDATE public.categories SET name_ar = name_en, name_en = name_ar WHERE name_ar IS NOT NULL AND name_ar !~ '[\u0600-\u06FF]' AND name_en ~ '[\u0600-\u06FF]';
