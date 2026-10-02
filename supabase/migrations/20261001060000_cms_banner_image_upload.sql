-- 7PRO — رفع صورة البانر/الاختصار من الموبايل: مدير المحتوى (cms.manage) يرفع في فولدره هو بس
-- (course-media/<uid>/...). قبل كده الرفع كان لمن عنده courses.manage أو معلم معتمد فقط.
drop policy if exists course_media_insert_cms on storage.objects;
create policy course_media_insert_cms on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'course-media'
    and public.has_permission('cms.manage')
    and (storage.foldername(name))[1] = auth.uid()::text
  );
