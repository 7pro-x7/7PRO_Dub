-- 7PRO — بيع الكورس بالاشتراك الشهري فقط (courses.monthly_only).
--  * لما monthly_only = true: لازم monthly_price > 0، وبيتعمل is_free=false و base_price = monthly_price
--    (عشان كل الفحوصات الحالية لـ "مجاني/مدفوع" تفضل صح من غير ما نغيّرها).
--  * الشراء بدفعة واحدة بيتقفل على السيرفر (COURSE_MONTHLY_ONLY)؛ المتاح الاشتراك الشهري بس.
--  * اللي كانوا داخلين الكورس مجانًا أو بدفعة واحدة بيتقفل عليهم (PAYMENT_REQUIRED) ويوصلهم إشعار،
--    ولازم يشتركوا شهريًا. لو رجع الكورس مش شهري-فقط، اللي اشتروه بدفعة واحدة بيرجع لهم الدخول.
--  * web_courses / web_course بيرجّعوا monthly_only.

alter table public.courses add column if not exists monthly_only boolean not null default false;

create or replace function public.courses_apply_monthly_only()
returns trigger
language plpgsql security definer set search_path to 'public'
as $$
begin
  if new.monthly_only then
    if coalesce(new.monthly_price, 0) <= 0 then raise exception 'MONTHLY_PRICE_REQUIRED'; end if;
    new.is_free := false;
    new.base_price := new.monthly_price;
  end if;
  return new;
end $$;

drop trigger if exists trg_courses_monthly_only on public.courses;
create trigger trg_courses_monthly_only
  before insert or update on public.courses
  for each row execute function public.courses_apply_monthly_only();

create or replace function public.courses_monthly_only_switch()
returns trigger
language plpgsql security definer set search_path to 'public'
as $function$
declare r record;
begin
  if new.monthly_only and not old.monthly_only then
    for r in
      with changed as (
        update public.enrollments e
           set status = 'PAYMENT_REQUIRED'
         where e.course_id = new.id and e.status = 'ACTIVE' and e.access_type in ('FREE', 'ONE_TIME')
        returning e.user_id
      )
      select user_id from changed
    loop
      perform public.push_notification(
        r.user_id, 'COURSE_NOW_PAID',
        'الكورس بقى بالاشتراك الشهري • Monthly subscription now required',
        'الكورس «' || new.title || '» بقى متاح بالاشتراك الشهري بس. اشترك عشان تكمل التعلّم. — "'
          || new.title || '" is now available by monthly subscription only. Subscribe to keep access.',
        jsonb_build_object('course_id', new.id)
      );
    end loop;
  elsif old.monthly_only and not new.monthly_only then
    update public.enrollments
       set status = 'ACTIVE'
     where course_id = new.id and status = 'PAYMENT_REQUIRED' and access_type = 'ONE_TIME';
  end if;
  return new;
end $function$;

drop trigger if exists trg_courses_monthly_only_switch on public.courses;
create trigger trg_courses_monthly_only_switch
  after update of monthly_only on public.courses
  for each row execute function public.courses_monthly_only_switch();

create or replace function public.courses_free_paid_switch()
returns trigger
language plpgsql security definer set search_path to 'public'
as $function$
declare v_was_free boolean; v_is_free boolean; r record;
begin
  v_was_free := coalesce(old.is_free, false) or coalesce(old.base_price, 0) <= 0;
  v_is_free  := coalesce(new.is_free, false) or coalesce(new.base_price, 0) <= 0;

  if v_was_free and not v_is_free then
    for r in
      with changed as (
        update public.enrollments e
           set status = 'PAYMENT_REQUIRED'
         where e.course_id = new.id and e.status = 'ACTIVE' and e.access_type = 'FREE'
        returning e.user_id
      )
      select user_id from changed
    loop
      perform public.push_notification(
        r.user_id, 'COURSE_NOW_PAID',
        case when new.monthly_only then 'الكورس بقى بالاشتراك الشهري • Monthly subscription now required'
             else 'الكورس أصبح مدفوعًا • Course is now paid' end,
        case when new.monthly_only
             then 'الكورس «' || new.title || '» بقى متاح بالاشتراك الشهري بس. اشترك عشان تكمل التعلّم. — "'
                  || new.title || '" is now available by monthly subscription only. Subscribe to keep access.'
             else 'الكورس «' || new.title || '» أصبح مدفوعًا. اشترك (شهريًا أو بدفعة واحدة) عشان تكمل التعلّم. — "'
                  || new.title || '" is now a paid course. Subscribe to keep access.' end,
        jsonb_build_object('course_id', new.id)
      );
    end loop;
  elsif v_is_free and not v_was_free then
    update public.enrollments
       set status = 'ACTIVE'
     where course_id = new.id and status = 'PAYMENT_REQUIRED';
  end if;
  return new;
end $function$;

create or replace function public.quote_checkout(
  p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid,
  p_live_group_id uuid, p_coupon_code text, p_country text
)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
begin
  if p_item_type = 'AI_TUTOR' then
    return public.ai_tutor_quote(p_user, p_live_plan_id, p_coupon_code, p_country);
  elsif p_item_type = 'DUBBING' then
    return public.dubbing_quote(p_user, p_live_plan_id, p_coupon_code, p_country);
  elsif p_item_type = 'COURSE_MONTHLY' then
    return public._course_monthly_quote(p_user, p_course_id, p_coupon_code, p_country);
  elsif p_item_type = 'COURSE'
        and exists (select 1 from public.courses c where c.id = p_course_id and c.monthly_only) then
    raise exception 'COURSE_MONTHLY_ONLY';
  end if;
  return public._quote_checkout_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country);
end $function$;

revoke all on function public.courses_apply_monthly_only() from public, anon, authenticated;
revoke all on function public.courses_monthly_only_switch() from public, anon, authenticated;

create or replace function public.web_courses()
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare v_country text; v_raw jsonb;
begin
  select value into v_raw from public.app_settings where key = 'pricing.default_country';
  v_country := upper(coalesce(nullif(btrim(v_raw #>> '{}'), ''), 'EG'));

  return coalesce((
    select jsonb_agg(x order by (x->>'is_featured')::boolean desc, (x->>'rating_avg')::numeric desc, (x->>'enrollments_count')::int desc)
    from (
      select jsonb_build_object(
        'id', c.id,
        'title', c.title, 'title_ar', c.title_ar, 'title_en', c.title_en,
        'subtitle', c.subtitle, 'subtitle_ar', c.subtitle_ar, 'subtitle_en', c.subtitle_en,
        'thumbnail_url', c.thumbnail_url, 'level', c.level,
        'is_free', f.free,
        'price', case when f.free then 0 else (f.pr->>'price')::numeric end,
        'currency', coalesce(f.pr->>'currency', c.base_currency),
        'monthly_price', case when f.mp is null then null else (f.mp->>'price')::numeric end,
        'monthly_only', coalesce(c.monthly_only, false),
        'rating_avg', coalesce(c.rating_avg, 0), 'rating_count', coalesce(c.rating_count, 0),
        'enrollments_count', coalesce(c.enrollments_count, 0),
        'is_featured', coalesce(c.is_featured, false),
        'teacher_name', p.full_name, 'teacher_avatar', p.avatar_url,
        'lessons_count', (select count(*) from public.lessons l where l.course_id = c.id)
      ) as x
      from public.courses c
      left join public.profiles p on p.id = c.teacher_id
      cross join lateral (
        select (coalesce(c.is_free, false) or coalesce(c.base_price, 0) <= 0) as free,
               case when coalesce(c.is_free, false) or coalesce(c.base_price, 0) <= 0 then null::jsonb
                    else public.resolve_price('COURSE', c.id, v_country) end as pr,
               case when coalesce(c.is_free, false) or coalesce(c.base_price, 0) <= 0
                         or coalesce(c.monthly_price, 0) <= 0 then null::jsonb
                    else public.resolve_price('COURSE_MONTHLY', c.id, v_country) end as mp
      ) f
      where c.status = 'PUBLISHED'
    ) s
  ), '[]'::jsonb);
end $function$;

create or replace function public.web_course(p_id uuid)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare c public.courses; v_country text; v_raw jsonb; v_pr jsonb; v_mp jsonb; v_free boolean;
  v_teacher text; v_avatar text;
begin
  select * into c from public.courses where id = p_id and status = 'PUBLISHED';
  if not found then raise exception 'COURSE_NOT_FOUND'; end if;

  select value into v_raw from public.app_settings where key = 'pricing.default_country';
  v_country := upper(coalesce(nullif(btrim(v_raw #>> '{}'), ''), 'EG'));
  v_free := coalesce(c.is_free, false) or coalesce(c.base_price, 0) <= 0;
  if not v_free then
    v_pr := public.resolve_price('COURSE', c.id, v_country);
    if coalesce(c.monthly_price, 0) > 0 then
      v_mp := public.resolve_price('COURSE_MONTHLY', c.id, v_country);
    end if;
  end if;

  select full_name, avatar_url into v_teacher, v_avatar from public.profiles where id = c.teacher_id;

  return jsonb_build_object(
    'id', c.id,
    'title', c.title, 'title_ar', c.title_ar, 'title_en', c.title_en,
    'subtitle', c.subtitle, 'subtitle_ar', c.subtitle_ar, 'subtitle_en', c.subtitle_en,
    'description', c.description, 'description_ar', c.description_ar, 'description_en', c.description_en,
    'thumbnail_url', c.thumbnail_url, 'level', c.level,
    'is_free', v_free,
    'price', case when v_free then 0 else (v_pr->>'price')::numeric end,
    'currency', coalesce(v_pr->>'currency', c.base_currency),
    'monthly_price', case when v_mp is null then null else (v_mp->>'price')::numeric end,
    'monthly_only', coalesce(c.monthly_only, false),
    'rating_avg', coalesce(c.rating_avg, 0), 'rating_count', coalesce(c.rating_count, 0),
    'enrollments_count', coalesce(c.enrollments_count, 0),
    'certificate_enabled', coalesce(c.certificate_enabled, false),
    'teacher_name', v_teacher, 'teacher_avatar', v_avatar,
    'sections', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', s.id, 'title', s.title, 'title_ar', s.title_ar, 'title_en', s.title_en,
        'lessons', coalesce((
          select jsonb_agg(jsonb_build_object(
            'id', l.id, 'title', l.title, 'title_ar', l.title_ar, 'title_en', l.title_en,
            'kind', l.kind, 'duration_seconds', l.duration_seconds, 'is_preview', coalesce(l.is_preview, false)
          ) order by l.sort_order, l.created_at)
          from public.lessons l where l.section_id = s.id
        ), '[]'::jsonb)
      ) order by s.sort_order, s.created_at)
      from public.course_sections s where s.course_id = c.id
    ), '[]'::jsonb),
    'loose_lessons', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', l.id, 'title', l.title, 'title_ar', l.title_ar, 'title_en', l.title_en,
        'kind', l.kind, 'duration_seconds', l.duration_seconds, 'is_preview', coalesce(l.is_preview, false)
      ) order by l.sort_order, l.created_at)
      from public.lessons l
      where l.course_id = c.id and (l.section_id is null or not exists (select 1 from public.course_sections s2 where s2.id = l.section_id))
    ), '[]'::jsonb)
  );
end $function$;
