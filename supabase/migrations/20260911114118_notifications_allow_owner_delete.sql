-- Allow a user to delete their own notifications (clear / dismiss from the app).
create policy notif_own_delete
  on public.notifications
  for delete
  using (user_id = auth.uid());;
