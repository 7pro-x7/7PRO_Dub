-- 7PRO — رابط تمارين المعلم للويب (للطلاب المشتركين معاه فقط) + إعلانات صفحات الويب.
-- إضافة فقط: جدول ودوال جديدة، من غير أي تعديل على دوال أو جداول موجودة.

-- ───────────────────────────────────────────────────────────── web ads
create table if not exists public.web_ad_slots (
  id             uuid primary key default gen_random_uuid(),
  page           text not null default 'EXERCISES' check (page ~ '^[A-Z_]{2,32}$'),
  slot           text not null check (slot in ('TOP', 'QUESTION', 'RESULT', 'BOTTOM')),
  kind           text not null check (kind in ('IMAGE', 'ADSENSE', 'HTML')),
  is_enabled     boolean not null default true,
  title          text,
  image_url      text check (image_url is null or image_url ~* '^https://'),
  link_url       text check (link_url is null or link_url ~* '^https?://'),
  adsense_client text check (adsense_client is null or adsense_client ~ '^ca-pub-[0-9]{6,20}$'),
  adsense_slot   text check (adsense_slot is null or adsense_slot ~ '^[0-9]{4,20}$'),
  html_code      text check (html_code is null or length(html_code) <= 20000),
  height_px      integer not null default 120 check (height_px between 40 and 700),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (page, slot),
  check (
    (kind = 'IMAGE'   and image_url is not null)
    or (kind = 'ADSENSE' and adsense_client is not null and adsense_slot is not null)
    or (kind = 'HTML'    and html_code is not null and btrim(html_code) <> '')
  )
);

comment on table public.web_ad_slots is
  'Ads shown on 7PRO web pages (not the Android app — AdMob units cannot serve on the web). Managed by owner/admin with ads.manage.';

create or replace function public.web_ad_slots_touch()
returns trigger language plpgsql as $$
begin new.updated_at := now(); return new; end $$;

drop trigger if exists web_ad_slots_touch on public.web_ad_slots;
create trigger web_ad_slots_touch before update on public.web_ad_slots
  for each row execute function public.web_ad_slots_touch();

alter table public.web_ad_slots enable row level security;

drop policy if exists web_ad_slots_admin on public.web_ad_slots;
create policy web_ad_slots_admin on public.web_ad_slots
  for all to authenticated
  using (public.has_permission('ads.manage'))
  with check (public.has_permission('ads.manage'));

-- The page reads only through this function: enabled slots of one page, and nothing at all when
-- the owner has switched web ads off (app_settings key web_ads.enabled; missing means on).
create or replace function public.web_ads(p_page text default 'EXERCISES')
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare v_raw jsonb; v_on boolean := true;
begin
  select value into v_raw from public.app_settings where key = 'web_ads.enabled';
  if v_raw is not null then
    v_on := case when jsonb_typeof(v_raw) = 'boolean' then (v_raw #>> '{}')::boolean
                 else lower(btrim(v_raw #>> '{}')) <> 'false' end;
  end if;
  if not v_on then return '[]'::jsonb; end if;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'slot', s.slot, 'kind', s.kind, 'title', s.title, 'image_url', s.image_url,
      'link_url', s.link_url, 'adsense_client', s.adsense_client, 'adsense_slot', s.adsense_slot,
      'html_code', s.html_code, 'height_px', s.height_px
    ) order by s.slot)
    from public.web_ad_slots s
    where s.page = upper(p_page) and s.is_enabled
  ), '[]'::jsonb);
end $function$;

revoke all on function public.web_ads(text) from public;
grant execute on function public.web_ads(text) to anon, authenticated;

-- ─────────────────────────────────────────────── teacher exercise links
-- Who may open a teacher's exercises through a shared link: the teacher, staff, or a student
-- whose subscription with that teacher is approved and not paused.
create or replace function public.can_open_teacher_exercises(p_teacher uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $function$
  select auth.uid() is not null and (
    auth.uid() = p_teacher
    or public.is_staff()
    or public.has_permission('tests.manage')
    or exists (
      select 1 from public.teacher_subscriptions s
      where s.teacher_id = p_teacher
        and s.student_user_id = auth.uid()
        and s.approval_status = 'APPROVED'
        and s.status <> 'PAUSED'
    )
  );
$function$;

revoke all on function public.can_open_teacher_exercises(uuid) from public;
revoke all on function public.can_open_teacher_exercises(uuid) from anon;
grant execute on function public.can_open_teacher_exercises(uuid) to authenticated;

create or replace function public.web_teacher_exercises(p_teacher uuid)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare v_name text; v_avatar text;
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.can_open_teacher_exercises(p_teacher) then raise exception 'NOT_SUBSCRIBED'; end if;

  select full_name, avatar_url into v_name, v_avatar from public.profiles where id = p_teacher;

  return jsonb_build_object(
    'teacher', jsonb_build_object('id', p_teacher, 'full_name', coalesce(v_name, ''), 'avatar_url', v_avatar),
    'sections', coalesce((
      select jsonb_agg(jsonb_build_object('id', e.id, 'title', e.title, 'sort_order', e.sort_order) order by e.sort_order, e.created_at)
      from public.exercise_sections e where e.owner_id = p_teacher
    ), '[]'::jsonb),
    'exercises', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', t.id, 'title', t.title, 'title_ar', t.title_ar,
        'description', t.description, 'description_ar', t.description_ar,
        'question_count', t.question_count, 'time_limit_seconds', t.time_limit_seconds,
        'passing_score', t.passing_score, 'is_locked', coalesce(t.is_locked, false),
        'section_id', t.section_id, 'is_adaptive', t.is_adaptive, 'sort_order', t.sort_order
      ) order by t.sort_order, t.created_at)
      from public.placement_tests t
      where t.owner_id = p_teacher and t.kind = 'EXERCISE' and t.status = 'PUBLISHED' and t.course_id is null
    ), '[]'::jsonb),
    'best', coalesce((
      select jsonb_object_agg(b.test_id, b.pct)
      from (
        select a.test_id, max(a.percent) as pct
        from public.test_attempts a
        join public.placement_tests t on t.id = a.test_id
        where a.user_id = auth.uid() and a.status = 'SUBMITTED' and t.owner_id = p_teacher
        group by a.test_id
      ) b
    ), '{}'::jsonb)
  );
end $function$;

revoke all on function public.web_teacher_exercises(uuid) from public;
revoke all on function public.web_teacher_exercises(uuid) from anon;
grant execute on function public.web_teacher_exercises(uuid) to authenticated;

-- Starting goes through the same subscription check, then hands over to the normal attempt flow
-- (next_test_question / submit_test_answer / finish_test_attempt are already bound to the attempt).
create or replace function public.web_start_exercise(p_test_id uuid)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare t public.placement_tests;
begin
  select * into t from public.placement_tests where id = p_test_id;
  if not found or t.kind <> 'EXERCISE' or t.status <> 'PUBLISHED' or t.owner_id is null or t.course_id is not null then
    raise exception 'TEST_NOT_AVAILABLE';
  end if;
  if not public.can_open_teacher_exercises(t.owner_id) then raise exception 'NOT_SUBSCRIBED'; end if;
  return public.start_test_attempt(p_test_id);
end $function$;

revoke all on function public.web_start_exercise(uuid) from public;
revoke all on function public.web_start_exercise(uuid) from anon;
grant execute on function public.web_start_exercise(uuid) to authenticated;

notify pgrst, 'reload schema';
