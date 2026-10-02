-- Anonymous browser guests: flag the profile as a guest (so the nightly
-- classroom_purge_guest_accounts job cleans them up and the guest storage rule
-- applies) and give an empty name a readable default.
CREATE OR REPLACE FUNCTION public.classroom_mark_anonymous_guest()
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF COALESCE((auth.jwt() ->> 'is_anonymous')::boolean, false) THEN
    UPDATE profiles
    SET is_guest = true,
        full_name = COALESCE(NULLIF(btrim(full_name), ''), 'ضيف')
    WHERE id = auth.uid();
  END IF;
END;
$$;
REVOKE ALL ON FUNCTION public.classroom_mark_anonymous_guest() FROM PUBLIC, anon, authenticated;

DO $$
DECLARE v_def TEXT;
BEGIN
  v_def := pg_get_functiondef('public.classroom_guest_join(uuid,text)'::regprocedure);
  IF position('classroom_mark_anonymous_guest' in v_def) = 0 THEN
    v_def := replace(v_def,
      E'  INSERT INTO public.classroom_attendance_events (session_id, user_id, event)',
      E'  PERFORM public.classroom_mark_anonymous_guest();\n\n  INSERT INTO public.classroom_attendance_events (session_id, user_id, event)');
    EXECUTE v_def;
  END IF;
END $$;;
