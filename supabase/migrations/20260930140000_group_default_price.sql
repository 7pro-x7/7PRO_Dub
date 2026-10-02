-- ============================================================================
-- الجروب الجديد كان بيتسجّل بسعر 0 (العمود default 0 + السعر الافتراضي العام 0)، فأول ما يتفعّل
-- كارت المعلم بيظهر «EGP 0» والحجز بيفشل بـ PRICE_NOT_SET.
--   1) الجروب الجديد بياخد أكتر سعر بيستخدمه المعلم في جروباته (وبنفس العملة)، وإلا السعر الافتراضي العام.
--   2) السعر الافتراضي العام (pricing.group_subscription_default) بقى 400 (سعر معظم الجروبات) لو كان 0.
--   3) الجروبات الحالية اللي سعرها 0 اتصلّحت بنفس القاعدة.
--   4) شاشة الحجز للطالب مابقتش تعرض جروب من غير سعر ولا تحسبه في «ابتداءً من»؛ المالك لسه بيشوفه في لوحة الحجز.
-- ============================================================================

update public.app_settings
   set value = to_jsonb(400), updated_at = now()
 where key = 'pricing.group_subscription_default'
   and coalesce((value #>> '{}')::numeric, 0) <= 0;

-- أكتر سعر بيستخدمه المعلم في جروباته (لو متساويين: الأقل)، مش آخر جروب: المعلمة ممكن يكون عندها جروب برايفت بـ 1600.
create or replace function public._teacher_group_default_price(p_teacher uuid, p_currency text, p_exclude uuid default null)
returns numeric
language sql stable security definer set search_path = public
as $$
  select coalesce(
    (select g.monthly_price from public.teacher_groups g
      where g.teacher_id = p_teacher and g.monthly_price > 0
        and upper(replace(g.currency, 'جنيه', 'EGP')) = upper(replace(coalesce(nullif(p_currency, ''), g.currency), 'جنيه', 'EGP'))
        and (p_exclude is null or g.id <> p_exclude)
      group by g.monthly_price
      order by count(*) desc, g.monthly_price asc
      limit 1),
    nullif(public.setting_num('pricing.group_subscription_default', 0), 0),
    0);
$$;

create or replace function public._teacher_groups_default_price()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if coalesce(new.monthly_price, 0) <= 0 then
    new.monthly_price := public._teacher_group_default_price(new.teacher_id, new.currency, new.id);
  end if;
  return new;
end $$;

drop trigger if exists teacher_groups_default_price on public.teacher_groups;
create trigger teacher_groups_default_price
  before insert on public.teacher_groups
  for each row execute function public._teacher_groups_default_price();

update public.teacher_groups g
   set monthly_price = public._teacher_group_default_price(g.teacher_id, g.currency, g.id)
 where coalesce(g.monthly_price, 0) <= 0
   and public._teacher_group_default_price(g.teacher_id, g.currency, g.id) > 0;

create or replace function public.booking_groups(p_teacher uuid)
returns table (group_id uuid, teacher_id uuid, name text, level text, schedule text, price numeric, currency text,
               capacity integer, members integer, seats_left integer, photo_url text)
language sql stable security definer set search_path = public
as $$
  select
    tg.id, tg.teacher_id, tg.name, tg.level, tg.schedule,
    case when tg.monthly_price > 0 then tg.monthly_price
         else public.setting_num('pricing.group_subscription_default', 0) end,
    tg.currency, tg.capacity,
    coalesce(m.members, 0)::int,
    case when tg.capacity > 0 then greatest(tg.capacity - coalesce(m.members, 0), 0)::int else null end,
    tg.photo_url
  from public.teacher_groups tg
  left join (
    select ts.teacher_id as tid, lower(ts.group_name) as gname, count(*)::int as members
    from public.teacher_subscriptions ts
    where ts.status <> 'PAUSED' and ts.approval_status = 'APPROVED'
    group by ts.teacher_id, lower(ts.group_name)
  ) m on m.tid = tg.teacher_id and m.gname = lower(tg.name)
  where tg.teacher_id = p_teacher
    and tg.approval_status = 'APPROVED'
    and tg.is_open
    and (tg.monthly_price > 0 or public.setting_num('pricing.group_subscription_default', 0) > 0)
  order by coalesce(m.members, 0) asc, tg.name asc;
$$;

create or replace function public.booking_teachers()
returns table (teacher_id uuid, full_name text, avatar_url text, headline text, groups_count integer, min_price numeric,
               currency text, students_count integer, rating numeric, is_premium boolean, badge text, sort_order integer)
language sql stable security definer set search_path = public
as $$
  with g as (
    select tg.teacher_id as tid,
           count(*)::int as groups_count,
           min(case when tg.monthly_price > 0 then tg.monthly_price
                    else public.setting_num('pricing.group_subscription_default', 0) end) as min_price,
           min(tg.currency) as currency
    from public.teacher_groups tg
    where tg.approval_status = 'APPROVED' and tg.is_open
      and (tg.monthly_price > 0 or public.setting_num('pricing.group_subscription_default', 0) > 0)
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
$$;
