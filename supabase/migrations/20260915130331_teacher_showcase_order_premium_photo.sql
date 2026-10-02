-- Owner/admin control over how teachers are presented on the booking screen:
-- their order, a premium badge, and their photo.

alter table public.teacher_profiles
  add column if not exists sort_order integer not null default 1000,
  add column if not exists is_premium boolean not null default false;

comment on column public.teacher_profiles.sort_order is
  'Booking-screen position. Lower shows first. Default 1000 = unordered, so admin-ordered teachers (10,20,30...) come first and the rest keep the old rating order behind them.';
comment on column public.teacher_profiles.is_premium is
  'Owner/admin only. Shows the premium badge (label in teacher_profiles.badge) on the booking card.';

create index if not exists teacher_profiles_sort_order_idx on public.teacher_profiles (sort_order);

-- A teacher must not be able to promote themselves: order, premium flag and badge are
-- platform decisions, exactly like commission and status already were.
create or replace function public.guard_teacher_profile_fields()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if auth.uid() is null then return new; end if;
  if public.is_owner() or public.has_permission('teachers.manage') then return new; end if;
  if new.commission_rate is distinct from old.commission_rate
     or new.status is distinct from old.status
     or new.can_manage_tests is distinct from old.can_manage_tests
     or new.live_enabled is distinct from old.live_enabled
     or new.badge is distinct from old.badge
     or new.is_premium is distinct from old.is_premium
     or new.sort_order is distinct from old.sort_order
     or new.rating_avg is distinct from old.rating_avg
     or new.rating_count is distinct from old.rating_count
     or new.students_count is distinct from old.students_count then
    raise exception 'FORBIDDEN';
  end if;
  return new;
end
$function$;

-- ── The student-facing list ────────────────────────────────────────────────
-- Same rows as before; now ordered by the admin's own arrangement first and
-- carrying the badge so the card can show it.
drop function if exists public.booking_teachers();

create function public.booking_teachers()
returns table(
  teacher_id uuid, full_name text, avatar_url text, headline text,
  groups_count integer, min_price numeric, currency text,
  students_count integer, rating numeric,
  is_premium boolean, badge text, sort_order integer
)
language sql
stable security definer
set search_path to 'public'
as $function$
  with g as (
    select tg.teacher_id as tid,
           count(*)::int as groups_count,
           min(case when tg.monthly_price > 0 then tg.monthly_price
                    else public.setting_num('pricing.group_subscription_default', 0) end) as min_price,
           min(tg.currency) as currency
    from public.teacher_groups tg
    where tg.approval_status = 'APPROVED' and tg.is_open
    group by tg.teacher_id
  )
  select p.id, coalesce(p.full_name, 'Teacher'), coalesce(tp.photo_url, p.avatar_url), tp.headline,
         g.groups_count, g.min_price, coalesce(g.currency, 'EGP'),
         coalesce(tp.students_count, 0), coalesce(tp.rating_avg, 0),
         coalesce(tp.is_premium, false), tp.badge, coalesce(tp.sort_order, 1000)
  from g
  join public.profiles p on p.id = g.tid
  join public.teacher_profiles tp on tp.id = g.tid
  where tp.status = 'APPROVED' and tp.accepting_new_students and p.status = 'ACTIVE'
  order by coalesce(tp.sort_order, 1000) asc,
           coalesce(tp.rating_avg, 0) desc,
           g.min_price asc;
$function$;

grant execute on function public.booking_teachers() to anon, authenticated, service_role;

-- ── The admin roster ───────────────────────────────────────────────────────
-- Every approved teacher, in the exact order the booking screen will use, with
-- what the owner needs to change it. `visible` says whether this teacher would
-- actually appear to a student right now (open group + accepting), so ordering a
-- teacher who is currently hidden doesn't look broken.
create or replace function public.admin_showcase_teachers()
returns table(
  teacher_id uuid, full_name text, photo_url text, headline text,
  is_premium boolean, badge text, sort_order integer,
  groups_count integer, rating numeric, visible boolean
)
language sql
stable security definer
set search_path to 'public'
as $function$
  select p.id,
         coalesce(p.full_name, 'Teacher'),
         coalesce(tp.photo_url, p.avatar_url),
         tp.headline,
         coalesce(tp.is_premium, false),
         tp.badge,
         coalesce(tp.sort_order, 1000),
         coalesce(g.groups_count, 0),
         coalesce(tp.rating_avg, 0),
         (coalesce(g.groups_count, 0) > 0 and tp.accepting_new_students and p.status = 'ACTIVE')
  from public.teacher_profiles tp
  join public.profiles p on p.id = tp.id
  left join (
    select tg.teacher_id as tid, count(*)::int as groups_count
    from public.teacher_groups tg
    where tg.approval_status = 'APPROVED' and tg.is_open
    group by tg.teacher_id
  ) g on g.tid = tp.id
  where tp.status = 'APPROVED'
    and (public.is_staff() or public.has_permission('teachers.manage'))
  order by coalesce(tp.sort_order, 1000) asc,
           coalesce(tp.rating_avg, 0) desc,
           coalesce(p.full_name, 'Teacher') asc;
$function$;

grant execute on function public.admin_showcase_teachers() to authenticated, service_role;

-- ── Writes ─────────────────────────────────────────────────────────────────

-- The whole order in one call: the client sends the list as it now looks on
-- screen and every position is rewritten from it. Positions are spaced by 10 so
-- a later single-teacher nudge has room to land between two others without
-- renumbering anything.
create or replace function public.admin_reorder_teachers(p_teacher_ids uuid[])
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if not (public.is_owner() or public.has_permission('teachers.manage')) then
    raise exception 'FORBIDDEN';
  end if;
  if p_teacher_ids is null or array_length(p_teacher_ids, 1) is null then
    raise exception 'EMPTY_ORDER';
  end if;

  update public.teacher_profiles tp
  set sort_order = o.position * 10,
      updated_at = now()
  from (
    select id, ordinality::int as position
    from unnest(p_teacher_ids) with ordinality as t(id, ordinality)
  ) o
  where tp.id = o.id;

  perform public.write_audit(
    'teachers.reorder', 'teacher', null,
    jsonb_build_object('count', array_length(p_teacher_ids, 1))
  );
end
$function$;

grant execute on function public.admin_reorder_teachers(uuid[]) to authenticated, service_role;

-- Premium badge. A null p_badge with p_is_premium = true means "use the app's
-- default label"; p_clear_badge wipes a custom label without touching the flag.
create or replace function public.admin_set_teacher_badge(
  p_teacher uuid,
  p_is_premium boolean default null,
  p_badge text default null,
  p_clear_badge boolean default false
)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare v_premium boolean;
begin
  if not (public.is_owner() or public.has_permission('teachers.manage')) then
    raise exception 'FORBIDDEN';
  end if;

  update public.teacher_profiles set
    is_premium = coalesce(p_is_premium, is_premium),
    badge = case
              when p_clear_badge then null
              when nullif(btrim(coalesce(p_badge, '')), '') is not null then btrim(p_badge)
              else badge
            end,
    updated_at = now()
  where id = p_teacher
  returning is_premium into v_premium;

  if not found then raise exception 'TEACHER_NOT_FOUND'; end if;

  perform public.write_audit('teacher.badge', 'teacher', p_teacher::text,
    jsonb_build_object('is_premium', v_premium, 'badge', p_badge, 'cleared', p_clear_badge));
end
$function$;

grant execute on function public.admin_set_teacher_badge(uuid, boolean, text, boolean) to authenticated, service_role;

-- The teacher's picture, set by staff. Writes teacher_profiles.photo_url, which
-- is what booking_teachers already prefers over the account avatar.
create or replace function public.admin_set_teacher_photo(p_teacher uuid, p_photo_url text default null)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if not (public.is_owner() or public.has_permission('teachers.manage')) then
    raise exception 'FORBIDDEN';
  end if;

  update public.teacher_profiles
  set photo_url = nullif(btrim(coalesce(p_photo_url, '')), ''),
      updated_at = now()
  where id = p_teacher;

  if not found then raise exception 'TEACHER_NOT_FOUND'; end if;

  perform public.write_audit('teacher.photo', 'teacher', p_teacher::text,
    jsonb_build_object('photo_url', p_photo_url));
end
$function$;

grant execute on function public.admin_set_teacher_photo(uuid, text) to authenticated, service_role;

-- ── Group pictures in the admin group list ────────────────────────────────
-- set_group_photo already accepts owner/admin; the list simply never returned
-- the current photo, so the admin screen had nothing to show or edit.
drop function if exists public.admin_booking_groups();

create function public.admin_booking_groups()
returns table(
  group_id uuid, teacher_id uuid, teacher_name text, name text, level text, schedule text,
  monthly_price numeric, currency text, is_open boolean, capacity integer, members integer,
  available boolean, photo_url text
)
language sql
stable security definer
set search_path to 'public'
as $function$
  select tg.id, tg.teacher_id, coalesce(p.full_name, 'Teacher'), tg.name, tg.level, tg.schedule,
         tg.monthly_price, tg.currency, tg.is_open, tg.capacity,
         coalesce(m.members, 0)::int, coalesce(tp.accepting_new_students, false),
         tg.photo_url
  from public.teacher_groups tg
  join public.profiles p on p.id = tg.teacher_id
  left join public.teacher_profiles tp on tp.id = tg.teacher_id
  left join (
    select ts.teacher_id as tid, lower(ts.group_name) as gname, count(*)::int as members
    from public.teacher_subscriptions ts
    where ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED'
    group by ts.teacher_id, lower(ts.group_name)
  ) m on m.tid = tg.teacher_id and m.gname = lower(tg.name)
  where public.is_staff() or public.has_permission('settings.manage')
  order by coalesce(p.full_name, 'Teacher') asc, tg.name asc;
$function$;

grant execute on function public.admin_booking_groups() to authenticated, service_role;;
