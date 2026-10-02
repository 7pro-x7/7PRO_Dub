-- AI Tutor custom characters: a precise face rig.
--
-- Until now a character only stored one mouth point (mouth_x, mouth_y, mouth_h) and a single
-- horizontal "eye line" (eye_y). That is too coarse to blink real eyes or open a real mouth
-- convincingly, so each character now also stores where BOTH eyes are (centre + size) and how
-- wide the mouth is. Every value is a fraction (0..1) of the cutout image; sizes are half-extents.
--
-- The app fills these in automatically when a picture is chosen (face-landmark model, or an
-- estimate for drawings) and lets the owner refine them by hand; rig_source records which.
-- Existing characters keep working: their eyes are placed on the old eye line, symmetric about
-- the centre, and rig_source = 'legacy'.

alter table public.ai_tutor_characters
  add column if not exists mouth_w    real not null default 0.09 check (mouth_w    between 0.01 and 0.45),
  add column if not exists eye_l_x    real not null default 0.38 check (eye_l_x    between 0 and 1),
  add column if not exists eye_l_y    real not null default 0.35 check (eye_l_y    between 0 and 1),
  add column if not exists eye_r_x    real not null default 0.62 check (eye_r_x    between 0 and 1),
  add column if not exists eye_r_y    real not null default 0.35 check (eye_r_y    between 0 and 1),
  add column if not exists eye_w      real not null default 0.06 check (eye_w      between 0.01 and 0.25),
  add column if not exists eye_h      real not null default 0.035 check (eye_h     between 0.005 and 0.2),
  add column if not exists rig_source text not null default 'legacy'
    check (rig_source in ('legacy', 'landmarks', 'estimated', 'manual'));

-- Existing characters: put both eyes on their old single eye line.
update public.ai_tutor_characters
   set eye_l_y = eye_y, eye_r_y = eye_y
 where rig_source = 'legacy';

-- Characters inserted by an older app build (which only knows eye_y) get the same treatment.
create or replace function public.ai_tutor_characters_legacy_rig()
returns trigger language plpgsql set search_path to 'public' as $$
begin
  if new.rig_source = 'legacy' then
    new.eye_l_y := new.eye_y;
    new.eye_r_y := new.eye_y;
  end if;
  return new;
end $$;

drop trigger if exists ai_tutor_characters_legacy_rig on public.ai_tutor_characters;
create trigger ai_tutor_characters_legacy_rig
  before insert on public.ai_tutor_characters
  for each row execute function public.ai_tutor_characters_legacy_rig();
