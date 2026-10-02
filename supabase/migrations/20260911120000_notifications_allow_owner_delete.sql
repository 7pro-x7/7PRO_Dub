-- Lets a signed-in user clear their own notifications from the app.
-- The "notifications" table already had SELECT and UPDATE policies scoped to
-- user_id = auth.uid(), but no DELETE policy existed, so "clear" actions in
-- the app had nothing to call. This adds the matching DELETE policy, scoped
-- the same way, so a user can only ever delete rows that belong to them.
create policy notif_own_delete
  on public.notifications
  for delete
  using (user_id = auth.uid());
