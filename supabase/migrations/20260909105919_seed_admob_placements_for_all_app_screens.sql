-- The app renders ad slots on eight screen/format pairs, but only two of them ever had a row in
-- admob_placements. ad_config returns enabled=false with reason 'no_placements' for the rest, so
-- those slots could never show an ad no matter how the SDK behaved. This seeds the missing rows
-- using the owner's own live ad units (the same ones already configured on HOME / COURSE_LIST).
--
-- Existing rows are left exactly as they are: the insert only fills gaps.

-- Guard against duplicates for repeat runs and for future admin inserts.
create unique index if not exists uq_admob_placement_screen_format
  on public.admob_placements (screen, format);

with units as (
  select
    (select ad_unit_id from public.admob_placements
      where format = 'BANNER' and ad_unit_id <> '' order by created_at limit 1) as banner_unit,
    (select ad_unit_id from public.admob_placements
      where format = 'INTERSTITIAL' and ad_unit_id <> '' order by created_at limit 1) as inter_unit
),
wanted(screen, format) as (
  values
    ('HOME','BANNER'),
    ('COURSE_LIST','BANNER'),
    ('COURSE_DETAIL','BANNER'),
    ('FREE_LESSON','BANNER'),
    ('TESTS','BANNER'),
    ('EXERCISES','BANNER'),
    ('PROFILE','BANNER'),
    ('COURSE_LIST','INTERSTITIAL')
)
insert into public.admob_placements
  (screen, format, ad_unit_id, is_enabled, free_content_only, display_interval_seconds,
   max_impressions_per_session, sort_order)
select
  w.screen,
  w.format,
  case when w.format = 'BANNER' then u.banner_unit else u.inter_unit end,
  true,
  false,   -- matches the account's ads.free_content_only = false setting
  case when w.format = 'BANNER' then 60 else 90 end,
  10,
  0
from wanted w cross join units u
where case when w.format = 'BANNER' then u.banner_unit else u.inter_unit end is not null
on conflict (screen, format) do nothing;;
