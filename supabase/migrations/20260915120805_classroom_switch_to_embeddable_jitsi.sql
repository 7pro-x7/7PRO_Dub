-- The browser classroom could not show the conference inline because
-- meet.ffmuc.net answers with `frame-ancestors 'self' *.ffmuc.net …` — it permits
-- framing only by its own sites. Measured, not guessed: see the probe results
-- behind the `probe-embed` function.
--
-- jitsi.riot.im sends no X-Frame-Options and no frame-ancestors directive, serves
-- external_api.js, and its config.js declares neither authdomain nor
-- anonymousdomain — so a room can be opened by anyone, with no account, and the
-- page may embed it. Element runs it and embeds it the same way inside their own
-- client, which is the strongest available signal that framing is intended.
--
-- Both clients read this column (the Android app hands it to the native SDK, the
-- web page to the IFrame API), so changing it here moves teacher and students
-- together and keeps them in the same room.
ALTER TABLE classroom_sessions ALTER COLUMN jitsi_domain SET DEFAULT 'jitsi.riot.im';

UPDATE classroom_sessions
SET jitsi_domain = 'jitsi.riot.im'
WHERE status IN ('SCHEDULED', 'LIVE')
  AND jitsi_domain <> 'jitsi.riot.im';;
