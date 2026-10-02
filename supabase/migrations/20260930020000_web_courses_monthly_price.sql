-- 7PRO — صفحة الكورسات على الويب: إظهار سعر الاشتراك الشهري (لو الكورس مدفوع وليه سعر شهري).
-- تعديل على web_courses / web_course بس: بيضيفوا الحقل monthly_price (null لو مش متاح).

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
