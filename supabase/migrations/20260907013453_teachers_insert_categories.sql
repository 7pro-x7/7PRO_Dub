-- Lets a teacher add a new course category themselves from the course editor.
-- Existing "cat_write" policy (owner/admin with cms.manage) is untouched and still
-- covers rename/delete/publish-toggle — this only adds INSERT for the TEACHER role,
-- so a teacher can create a category but never edit or remove one afterwards.
create policy "teachers_insert_categories" on public.categories
  for insert
  to authenticated
  with check (
    (select role from public.profiles where id = auth.uid()) = 'TEACHER'
  );;
