-- Guarantee an AdMob banner on every screen the app requests one for, and an
-- interstitial on the course list, per owner request (ads.free_content_only
-- was already false, so paid courses are unaffected by that gate).

insert into admob_placements (screen, format, ad_unit_id, frequency, spacing, max_impressions_per_session, sort_order, is_enabled, free_content_only, display_interval_seconds)
values
  ('PROFILE',       'BANNER', 'ca-app-pub-2143545712755970/5531457415', 1, 4, 10, 0, true, false, 10),
  ('TESTS',         'BANNER', 'ca-app-pub-2143545712755970/5531457415', 1, 4, 10, 0, true, false, 10),
  ('EXERCISES',     'BANNER', 'ca-app-pub-2143545712755970/5531457415', 1, 4, 10, 0, true, false, 10),
  ('COURSE_LIST',   'BANNER', 'ca-app-pub-2143545712755970/5531457415', 1, 4, 10, 0, true, false, 10),
  ('COURSE_DETAIL', 'BANNER', 'ca-app-pub-2143545712755970/5531457415', 1, 4, 10, 0, true, false, 10),
  ('FREE_LESSON',   'BANNER', 'ca-app-pub-2143545712755970/5531457415', 1, 4, 10, 0, true, false, 10),
  ('COURSE_LIST',   'INTERSTITIAL', 'ca-app-pub-2143545712755970/9675928002', 1, 4, 10, 0, true, false, 90)
on conflict do nothing;

-- Home's existing row also gets free_content_only = false for consistency with the rest.
update admob_placements set free_content_only = false, is_enabled = true
where screen = 'HOME';
;
