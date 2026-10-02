-- ============================================================================
-- booking_groups() never returned photo_url, so a teacher could set a group
-- picture (teacher_group_photo.sql) but students browsing groups to book
-- would never see it — only the owning teacher, via their own management
-- screen, ever saw it. This adds photo_url to the student-facing RPC so the
-- picture shows to everyone browsing that teacher's groups.
--
-- The return signature is changing, so the function must be dropped first —
-- CREATE OR REPLACE FUNCTION cannot alter the shape of a table return.
-- ============================================================================

drop function if exists public.booking_groups(uuid);

create function public.booking_groups(p_teacher uuid)
returns table(
  group_id   uuid,
  teacher_id uuid,
  name       text,
  level      text,
  schedule   text,
  price      numeric,
  currency   text,
  capacity   int,
  members    int,
  seats_left int,
  photo_url  text
)
language sql
stable
security definer
set search_path to 'public'
as $function$
  select
    tg.id,
    tg.teacher_id,
    tg.name,
    tg.level,
    tg.schedule,
    case when tg.monthly_price > 0 then tg.monthly_price
         else public.setting_num('pricing.group_subscription_default', 0) end,
    tg.currency,
    tg.capacity,
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
  order by coalesce(m.members, 0) asc, tg.name asc;
$function$;

revoke all on function public.booking_groups(uuid) from public;
revoke all on function public.booking_groups(uuid) from anon;
grant execute on function public.booking_groups(uuid) to authenticated;
