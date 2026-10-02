-- classroom_guest_join(uuid) is the ORIGINAL guest entry point: it takes only a session id and
-- never checks the invite key. It was superseded by classroom_guest_join(uuid, text), which
-- validates the key (room_password) — that is the one web/classroom.html calls. Both overloads
-- were left in the database, so anyone with an anonymous auth session and a session id could
-- still call the old one and skip the key check. Nothing in the web page or the app calls it.
DROP FUNCTION IF EXISTS public.classroom_guest_join(uuid);
