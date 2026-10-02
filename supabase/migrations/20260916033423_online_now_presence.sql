ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_profiles_last_seen_at ON public.profiles (last_seen_at);

CREATE OR REPLACE FUNCTION public.ping_presence()
 RETURNS void
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  UPDATE public.profiles SET last_seen_at = now() WHERE id = auth.uid();
$function$;

GRANT EXECUTE ON FUNCTION public.ping_presence() TO authenticated;

CREATE OR REPLACE FUNCTION public.owner_analytics(p_from timestamp with time zone, p_to timestamp with time zone, p_country text, p_teacher uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v jsonb; f timestamptz; t timestamptz;
begin
  if not public.has_permission('analytics.read') then raise exception 'FORBIDDEN'; end if;
  f := coalesce(p_from, now() - interval '30 days');
  t := coalesce(p_to, now());

  select jsonb_build_object(
    'users_total', (select count(*) from public.profiles),
    'students', (select count(*) from public.profiles where role = 'STUDENT'),
    'teachers', (select count(*) from public.profiles where role = 'TEACHER'),
    'admins', (select count(*) from public.profiles where role in ('ADMIN','OWNER')),
    'courses_published', (select count(*) from public.courses where status = 'PUBLISHED'),
    'courses_pending', (select count(*) from public.courses where status = 'PENDING_REVIEW'),
    'live_published', (select count(*) from public.live_services where status = 'PUBLISHED'),
    'live_pending', (select count(*) from public.live_services where status = 'PENDING_REVIEW'),
    'teacher_applications_pending', (select count(*) from public.teacher_applications where status = 'PENDING'),
    'orders_paid', (select count(*) from public.orders o where o.status in ('PAID','PARTIALLY_REFUNDED')
        and o.item_type = 'COURSE'
        and o.paid_at between f and t
        and (p_country is null or o.country_code = upper(p_country))
        and (p_teacher is null or o.teacher_id = p_teacher)),
    'gross_revenue', (select coalesce(sum(o.total_amount),0) from public.orders o where o.status in ('PAID','PARTIALLY_REFUNDED')
        and o.item_type = 'COURSE'
        and o.paid_at between f and t
        and (p_country is null or o.country_code = upper(p_country))
        and (p_teacher is null or o.teacher_id = p_teacher)),
    'teacher_earnings', (select coalesce(sum(o.teacher_amount),0) from public.orders o where o.status in ('PAID','PARTIALLY_REFUNDED')
        and o.item_type = 'COURSE'
        and o.paid_at between f and t and (p_country is null or o.country_code = upper(p_country))
        and (p_teacher is null or o.teacher_id = p_teacher)),
    'platform_earnings', (select coalesce(sum(o.platform_amount),0) from public.orders o where o.status in ('PAID','PARTIALLY_REFUNDED')
        and o.item_type = 'COURSE'
        and o.paid_at between f and t and (p_country is null or o.country_code = upper(p_country))
        and (p_teacher is null or o.teacher_id = p_teacher)),
    'discounts', (select coalesce(sum(o.discount_amount),0) from public.orders o where o.status in ('PAID','PARTIALLY_REFUNDED')
        and o.item_type = 'COURSE' and o.paid_at between f and t),
    'refunds', (select coalesce(sum(r.amount),0) from public.refunds r
        join public.orders o on o.id = r.order_id
        where r.kind = 'REFUND' and o.item_type = 'COURSE' and r.created_at between f and t),
    'chargebacks', (select coalesce(sum(r.amount),0) from public.refunds r
        join public.orders o on o.id = r.order_id
        where r.kind = 'CHARGEBACK' and o.item_type = 'COURSE' and r.created_at between f and t),
    'net_revenue', (select coalesce(sum(o.platform_amount),0) from public.orders o
                    where o.status in ('PAID','PARTIALLY_REFUNDED') and o.item_type = 'COURSE' and o.paid_at between f and t)
                   - (select coalesce(sum(r.amount),0) from public.refunds r
                      join public.orders o on o.id = r.order_id
                      where o.item_type = 'COURSE' and r.created_at between f and t),
    'subscriptions_active', (select count(*) from public.subscriptions where status = 'ACTIVE'),
    'subscriptions_expiring', (select count(*) from public.subscriptions where status = 'EXPIRING'),
    'online_now', (select count(*) from public.profiles where last_seen_at > now() - interval '90 seconds'),
    'payouts_pending', (select coalesce(sum(amount),0) from public.payouts where status in ('PENDING','APPROVED')),
    'payouts_paid', (select coalesce(sum(amount),0) from public.payouts where status = 'PAID'),
    'tests_taken', (select count(*) from public.test_attempts where status = 'SUBMITTED' and submitted_at between f and t),
    'open_tickets', (select count(*) from public.support_tickets where status in ('OPEN','ASSIGNED')),
    'attendance_rate', (select coalesce(round(100.0 * count(*) filter (where present) / greatest(count(*),1), 1),0) from public.attendance where session_date between f::date and t::date),
    'by_level', (select coalesce(jsonb_object_agg(level, n),'{}'::jsonb) from (select coalesce(level,'UNKNOWN') as level, count(*) n from public.test_attempts where status='SUBMITTED' group by 1) l),
    'by_country', (select coalesce(jsonb_object_agg(country_code, n),'{}'::jsonb) from (select country_code, count(*) n from public.orders where status in ('PAID','PARTIALLY_REFUNDED') and item_type = 'COURSE' group by 1 order by 2 desc limit 12) cc),
    'coupon_usage', (select coalesce(sum(used_count),0) from public.coupons)
  ) into v;
  return v;
end $function$;
;
