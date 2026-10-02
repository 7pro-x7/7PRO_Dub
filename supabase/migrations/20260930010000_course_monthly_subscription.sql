-- 7PRO — الاشتراك الشهري في الكورسات المسجّلة + تحويل الكورس المجاني لمدفوع.
--
--  1) courses.monthly_price: لو متحدد (> 0) الكورس المدفوع بيتباع كمان باشتراك شهري
--     (بجانب الشراء بدفعة واحدة base_price).
--  2) الاشتراك الشهري = order عادي item_type='COURSE' + billing_period='MONTH'، فكل منطق
--     الفلوس (العمولة، دفتر أرباح المعلم، الإشعارات) هو نفسه بتاع شراء الكورس. الدخول
--     بيتفتح 'ACTIVE' لمدة شهر (expires_at) وبعدين بيتقفل تلقائي لحد ما الطالب يجدد.
--  3) لو معلم/مالك حوّل كورس مجاني لمدفوع: كل اللي كانوا داخلين عليه مجانًا حالتهم بتبقى
--     PAYMENT_REQUIRED (الدخول بيتقفل)، وبيوصلهم إشعار إن الكورس بقى مدفوع ولازم يشتركوا.
--     ولو رجع مجاني تاني الدخول بيرجع لهم.
--  4) فحوصات الدخول (has_course_access / web_lesson_media / التمارين) بقت بتحترم expires_at.

-- ── الأعمدة ──────────────────────────────────────────────────────────────────────────

alter table public.courses add column if not exists monthly_price numeric(12,2);
alter table public.courses drop constraint if exists courses_monthly_price_positive;
alter table public.courses
  add constraint courses_monthly_price_positive check (monthly_price is null or monthly_price > 0);

alter table public.enrollments add column if not exists access_type text not null default 'ONE_TIME';
alter table public.enrollments drop constraint if exists enrollments_access_type_check;
alter table public.enrollments
  add constraint enrollments_access_type_check check (access_type in ('FREE', 'ONE_TIME', 'MONTHLY'));
alter table public.enrollments add column if not exists expires_at timestamptz;

alter table public.orders add column if not exists billing_period text;
alter table public.orders drop constraint if exists orders_billing_period_check;
alter table public.orders
  add constraint orders_billing_period_check check (billing_period is null or billing_period = 'MONTH');

create index if not exists idx_enrollments_monthly_expiry
  on public.enrollments (expires_at)
  where access_type = 'MONTHLY' and status = 'ACTIVE';

-- التسجيلات القديمة اللي جت من كورس مجاني (طلب بقيمة 0 وبدون كوبون) تتعلّم FREE.
-- (كوبون 100% ≠ كورس مجاني: ده اتدفع له كوبون فمش هيتقفل لو الكورس بقى مدفوع.)
update public.enrollments e
   set access_type = 'FREE'
  from public.orders o
 where o.id = e.order_id
   and o.total_amount = 0
   and o.coupon_id is null
   and e.access_type = 'ONE_TIME';

-- نوع الدخول بيتحدد أوتوماتيك من الطلب اللي فتحه (من غير ما نلمس دوال التأكيد الحالية).
create or replace function public.enrollments_set_access_type()
returns trigger
language plpgsql security definer set search_path to 'public'
as $$
declare o public.orders;
begin
  if new.order_id is null then
    return new;
  end if;
  if tg_op = 'UPDATE' and new.order_id is not distinct from old.order_id then
    return new;
  end if;
  select * into o from public.orders where id = new.order_id;
  if found then
    new.access_type := case
      when o.billing_period = 'MONTH' then 'MONTHLY'
      when o.total_amount = 0 and o.coupon_id is null then 'FREE'
      else 'ONE_TIME'
    end;
    if new.access_type <> 'MONTHLY' then
      new.expires_at := null;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_enrollments_set_access_type on public.enrollments;
create trigger trg_enrollments_set_access_type
  before insert or update of order_id on public.enrollments
  for each row execute function public.enrollments_set_access_type();

-- ── تسجيل ساري (بيحترم تاريخ الانتهاء) ───────────────────────────────────────────────

create or replace function public.has_active_enrollment(p_user uuid, p_course uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $$
  select exists (
    select 1
    from public.enrollments e
    where e.user_id = p_user
      and e.course_id = p_course
      and e.status = 'ACTIVE'
      and (e.expires_at is null or e.expires_at > now())
  );
$$;

create or replace function public.has_course_access(p_user uuid, p_course uuid)
returns boolean
language sql stable security definer set search_path to 'public'
as $function$
  select public.has_active_enrollment(p_user, p_course)
      or exists (select 1 from public.courses c where c.id = p_course and c.is_free and c.status = 'PUBLISHED')
      or exists (select 1 from public.courses c where c.id = p_course and c.teacher_id = p_user);
$function$;

create or replace function public.web_lesson_media(p_lesson_id uuid)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare l public.lessons; c public.courses; v_uid uuid := auth.uid(); v_ok boolean := false;
begin
  select * into l from public.lessons where id = p_lesson_id;
  if not found then raise exception 'LESSON_NOT_FOUND'; end if;
  select * into c from public.courses where id = l.course_id;
  if not found then raise exception 'LESSON_NOT_FOUND'; end if;

  if v_uid is not null and (
       c.teacher_id = v_uid
    or exists (select 1 from public.profiles p where p.id = v_uid and p.role in ('OWNER','ADMIN'))
  ) then
    v_ok := true;
  elsif c.status = 'PUBLISHED' then
    if coalesce(l.is_preview, false) then
      v_ok := true;
    elsif v_uid is null then
      raise exception 'UNAUTHORIZED';
    else
      v_ok := public.has_active_enrollment(v_uid, c.id);
    end if;
  else
    raise exception 'LESSON_NOT_FOUND';
  end if;

  if not v_ok then raise exception 'NOT_ENROLLED'; end if;

  return jsonb_build_object('id', l.id, 'kind', l.kind, 'video_url', nullif(btrim(l.video_url), ''));
end $function$;

-- ── السعر: فرع الاشتراك الشهري (نفس قواعد التسعير حسب البلد) ─────────────────────────

create or replace function public.resolve_price(p_item_type text, p_item_id uuid, p_country text)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  v_base numeric; v_base_cur text; v_country text := upper(coalesce(p_country,''));
  v_is_free boolean := false;
  cp record; ov record; v_price numeric; v_cur text; v_rule jsonb;
begin
  if p_item_type = 'COURSE' then
    select base_price, base_currency, coalesce(is_free, false)
      into v_base, v_base_cur, v_is_free
      from public.courses where id = p_item_id;
  elsif p_item_type = 'COURSE_MONTHLY' then
    select monthly_price, base_currency into v_base, v_base_cur
      from public.courses where id = p_item_id;
  elsif p_item_type = 'LIVE_PLAN' then
    select p.base_price, s.base_currency into v_base, v_base_cur
    from public.live_plans p join public.live_services s on s.id = p.live_service_id where p.id = p_item_id;
  else
    raise exception 'UNKNOWN_ITEM_TYPE';
  end if;
  if v_base is null then raise exception 'ITEM_NOT_FOUND'; end if;
  v_base_cur := coalesce(v_base_cur, public.setting_text('pricing.base_currency','USD'));

  if p_item_type = 'COURSE' and v_is_free then
    return jsonb_build_object(
      'base_price', 0, 'base_currency', v_base_cur,
      'price', 0, 'currency', v_base_cur,
      'country_code', v_country, 'pricing_rule', jsonb_build_object('source','free_course','country',v_country)
    );
  end if;

  select * into ov from public.price_overrides
   where item_type = p_item_type and item_id = p_item_id and is_active
     and (country_code = v_country or country_code is null)
   order by (country_code is not null) desc limit 1;

  if found then
    v_price := ov.price; v_cur := ov.currency;
    v_rule := jsonb_build_object('source','override','override_id',ov.id,'country',v_country);
  else
    select * into cp from public.country_pricing where country_code = v_country and is_active;
    if found then
      v_price := v_base * cp.fx_multiplier;
      v_price := v_price * (1 - cp.discount_percent/100.0);
      if cp.round_to > 0 then v_price := round(v_price / cp.round_to) * cp.round_to; end if;
      v_cur := cp.currency;
      v_rule := jsonb_build_object('source','country_pricing','country',v_country,
        'fx_multiplier',cp.fx_multiplier,'discount_percent',cp.discount_percent,'region_group',cp.region_group);
    else
      v_price := v_base; v_cur := v_base_cur;
      v_rule := jsonb_build_object('source','global_default','country',v_country);
    end if;
  end if;

  return jsonb_build_object(
    'base_price', round(v_base,2), 'base_currency', v_base_cur,
    'price', round(greatest(v_price,0),2), 'currency', v_cur,
    'country_code', v_country, 'pricing_rule', v_rule
  );
end $function$;

-- ── عرض السعر (quote) للاشتراك الشهري ────────────────────────────────────────────────

create or replace function public._course_monthly_quote(
  p_user uuid, p_course_id uuid, p_coupon_code text, p_country text
)
returns jsonb
language plpgsql stable security definer set search_path to 'public'
as $function$
declare
  c public.courses; e public.enrollments; v_free boolean;
  v_price jsonb; v_list numeric; v_cur text; v_coupon jsonb; v_total numeric;
begin
  select * into c from public.courses where id = p_course_id and status = 'PUBLISHED';
  if not found then raise exception 'COURSE_NOT_AVAILABLE'; end if;

  v_free := coalesce(c.is_free, false) or coalesce(c.base_price, 0) <= 0;
  if v_free or c.monthly_price is null then raise exception 'COURSE_NOT_AVAILABLE'; end if;

  -- اللي مالك الكورس بدفعة واحدة مش محتاج اشتراك. اللي عنده اشتراك شهري ساري بيقدر يجدد.
  select * into e from public.enrollments where user_id = p_user and course_id = p_course_id;
  if found and e.status = 'ACTIVE' and e.access_type <> 'MONTHLY' then
    raise exception 'ALREADY_ENROLLED';
  end if;

  v_price := public.resolve_price('COURSE_MONTHLY', p_course_id, p_country);
  v_list := (v_price->>'price')::numeric;
  v_cur := v_price->>'currency';
  v_coupon := public.evaluate_coupon(p_coupon_code, p_user, 'COURSE', p_course_id, c.teacher_id, p_country, v_list, v_cur);
  v_total := round(greatest(v_list - (v_coupon->>'discount')::numeric, 0), 2);

  return jsonb_build_object(
    'item_type', 'COURSE_MONTHLY',
    'billing_period', 'MONTH',
    'course_id', p_course_id,
    'live_service_id', null,
    'live_plan_id', null,
    'live_group_id', null,
    'teacher_id', c.teacher_id,
    'country_code', v_price->>'country_code',
    'currency', v_cur,
    'base_price', (v_price->>'base_price')::numeric,
    'list_price', v_list,
    'discount_amount', (v_coupon->>'discount')::numeric,
    'total_amount', v_total,
    'coupon', v_coupon,
    'pricing_rule', v_price->'pricing_rule',
    'seats_left', null,
    'commission_rate', public.resolve_commission_rate('COURSE', p_course_id, c.teacher_id)
  );
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
  end if;
  return public._quote_checkout_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country);
end $function$;

-- ── إنشاء الطلب ──────────────────────────────────────────────────────────────────────

create or replace function public.create_order(
  p_user uuid, p_item_type text, p_course_id uuid, p_live_plan_id uuid,
  p_live_group_id uuid, p_coupon_code text, p_country text, p_idempotency_key text
)
returns public.orders
language plpgsql security definer set search_path to 'public'
as $function$
declare q jsonb; o public.orders; v_existing public.orders;
begin
  if p_item_type = 'AI_TUTOR' then
    if p_idempotency_key is not null then
      select * into v_existing from public.orders where idempotency_key = p_idempotency_key;
      if found then return v_existing; end if;
    end if;
    if not coalesce((select status = 'ACTIVE' and not coalesce(is_guest, false) from public.profiles where id = p_user), false) then
      raise exception 'ACCOUNT_NOT_ACTIVE';
    end if;
    q := public.quote_checkout(p_user, 'AI_TUTOR', null, p_live_plan_id, null, p_coupon_code, p_country);
    insert into public.orders(
      user_id, teacher_id, item_type, ai_tutor_plan_id,
      country_code, currency, base_price, list_price, discount_amount, total_amount,
      coupon_id, pricing_rule, commission_rate, status, idempotency_key
    ) values (
      p_user, null, 'AI_TUTOR', p_live_plan_id,
      q->>'country_code', q->>'currency', (q->>'base_price')::numeric, (q->>'list_price')::numeric,
      (q->>'discount_amount')::numeric, (q->>'total_amount')::numeric,
      nullif(q#>>'{coupon,coupon_id}', '')::uuid, q->'pricing_rule', 0, 'PENDING', p_idempotency_key
    ) returning * into o;
    return o;
  end if;

  if p_item_type = 'DUBBING' then
    if p_idempotency_key is not null then
      select * into v_existing from public.orders where idempotency_key = p_idempotency_key;
      if found then return v_existing; end if;
    end if;
    if not coalesce((select status = 'ACTIVE' and not coalesce(is_guest, false) from public.profiles where id = p_user), false) then
      raise exception 'ACCOUNT_NOT_ACTIVE';
    end if;
    q := public.quote_checkout(p_user, 'DUBBING', null, p_live_plan_id, null, p_coupon_code, p_country);

    insert into public.orders(
      user_id, teacher_id, item_type, dubbing_job_id,
      country_code, currency, base_price, list_price, discount_amount, total_amount,
      coupon_id, pricing_rule, commission_rate, status, idempotency_key
    ) values (
      p_user, null, 'DUBBING', p_live_plan_id,
      q->>'country_code', q->>'currency', (q->>'base_price')::numeric, (q->>'list_price')::numeric,
      (q->>'discount_amount')::numeric, (q->>'total_amount')::numeric,
      null, q->'pricing_rule', 0, 'PENDING', p_idempotency_key
    ) returning * into o;

    update public.dubbing_jobs set
      order_id = o.id, status = 'AWAITING_PAYMENT',
      minutes_billed = (q->>'billable_minutes')::numeric,
      minutes_free = (q->>'free_minutes_applied')::numeric,
      amount_charged = (q->>'total_amount')::numeric,
      currency = q->>'currency'
    where id = p_live_plan_id;
    return o;
  end if;

  if p_item_type = 'COURSE_MONTHLY' then
    if p_idempotency_key is not null then
      select * into v_existing from public.orders where idempotency_key = p_idempotency_key;
      if found then return v_existing; end if;
    end if;
    if not coalesce((select status = 'ACTIVE' from public.profiles where id = p_user), false) then
      raise exception 'ACCOUNT_NOT_ACTIVE';
    end if;
    q := public.quote_checkout(p_user, 'COURSE_MONTHLY', p_course_id, null, null, p_coupon_code, p_country);

    -- الطلب بيتسجّل كطلب كورس عادي (item_type='COURSE') بس بفترة فوترة شهرية.
    insert into public.orders(
      user_id, teacher_id, item_type, course_id, billing_period,
      country_code, currency, base_price, list_price, discount_amount, total_amount,
      coupon_id, pricing_rule, commission_rate, status, idempotency_key
    ) values (
      p_user, (q->>'teacher_id')::uuid, 'COURSE', p_course_id, 'MONTH',
      q->>'country_code', q->>'currency', (q->>'base_price')::numeric, (q->>'list_price')::numeric,
      (q->>'discount_amount')::numeric, (q->>'total_amount')::numeric,
      nullif(q#>>'{coupon,coupon_id}', '')::uuid, q->'pricing_rule',
      (q->>'commission_rate')::numeric, 'PENDING', p_idempotency_key
    ) returning * into o;
    return o;
  end if;

  return public._create_order_base(p_user, p_item_type, p_course_id, p_live_plan_id, p_live_group_id, p_coupon_code, p_country, p_idempotency_key);
end $function$;

-- ── تأكيد الدفع: نفس منطق الكورس + مدة الاشتراك ──────────────────────────────────────

create or replace function public._course_monthly_confirm(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb
)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  o public.orders; e public.enrollments; v_res jsonb;
  v_had boolean; v_prev_exp timestamptz; v_new_exp timestamptz;
begin
  select * into o from public.orders where id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;

  select * into e from public.enrollments where user_id = o.user_id and course_id = o.course_id;
  v_had := found;
  -- تجديد بدري: الشهر الجديد بيتضاف على آخر يوم متبقي، مش على النهارده.
  v_prev_exp := case
    when v_had and e.status = 'ACTIVE' and e.access_type = 'MONTHLY' and e.expires_at > now()
      then e.expires_at
  end;

  v_res := public._confirm_order_payment_base(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  if coalesce((v_res ->> 'duplicate')::boolean, false) then
    return v_res;
  end if;

  v_new_exp := coalesce(v_prev_exp, now()) + interval '1 month';

  update public.enrollments
     set access_type = 'MONTHLY', status = 'ACTIVE', expires_at = v_new_exp
   where user_id = o.user_id and course_id = o.course_id;

  -- الدالة الأساسية بتزوّد عدّاد المشتركين مع كل تأكيد؛ التجديد مش مشترك جديد.
  if v_had then
    update public.courses set enrollments_count = greatest(enrollments_count - 1, 0) where id = o.course_id;
  end if;

  return v_res || jsonb_build_object('expires_at', v_new_exp);
end $function$;

create or replace function public.confirm_order_payment(
  p_order_id uuid, p_provider text, p_provider_ref text, p_event_id text, p_payload jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare v_type text; v_billing text;
begin
  select item_type, billing_period into v_type, v_billing from public.orders where id = p_order_id;
  if v_type = 'AI_TUTOR' then
    return public.ai_tutor_confirm_order(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  elsif v_type = 'DUBBING' then
    return public.dubbing_confirm_order(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  elsif v_type = 'COURSE' and v_billing = 'MONTH' then
    return public._course_monthly_confirm(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
  end if;
  return public._confirm_order_payment_base(p_order_id, p_provider, p_provider_ref, p_event_id, p_payload);
end $function$;

-- ── انتهاء الاشتراك الشهري ───────────────────────────────────────────────────────────

create or replace function public.expire_monthly_enrollments()
returns integer
language plpgsql security definer set search_path to 'public'
as $function$
declare r record; n integer := 0;
begin
  for r in
    with x as (
      update public.enrollments e
         set status = 'EXPIRED'
       where e.access_type = 'MONTHLY' and e.status = 'ACTIVE'
         and e.expires_at is not null and e.expires_at <= now()
      returning e.user_id, e.course_id
    )
    select x.user_id, x.course_id, c.title
      from x join public.courses c on c.id = x.course_id
  loop
    n := n + 1;
    perform public.push_notification(
      r.user_id, 'COURSE_SUBSCRIPTION_EXPIRED',
      'انتهى اشتراكك الشهري • Monthly subscription ended',
      'انتهى اشتراكك في «' || r.title || '». جدّد الاشتراك عشان تكمل التعلّم. — Your subscription to "'
        || r.title || '" has ended. Renew to keep learning.',
      jsonb_build_object('course_id', r.course_id)
    );
  end loop;
  return n;
end $function$;

-- ── كورس مجاني ↔ مدفوع ───────────────────────────────────────────────────────────────

create or replace function public.courses_free_paid_switch()
returns trigger
language plpgsql security definer set search_path to 'public'
as $function$
declare v_was_free boolean; v_is_free boolean; r record;
begin
  v_was_free := coalesce(old.is_free, false) or coalesce(old.base_price, 0) <= 0;
  v_is_free  := coalesce(new.is_free, false) or coalesce(new.base_price, 0) <= 0;

  if v_was_free and not v_is_free then
    -- كان مجاني وبقى مدفوع: اللي دخلوا مجانًا لازم يشتركوا.
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
        'الكورس أصبح مدفوعًا • Course is now paid',
        'الكورس «' || new.title || '» أصبح مدفوعًا. اشترك (شهريًا أو بدفعة واحدة) عشان تكمل التعلّم. — "'
          || new.title || '" is now a paid course. Subscribe to keep access.',
        jsonb_build_object('course_id', new.id)
      );
    end loop;
  elsif v_is_free and not v_was_free then
    -- رجع مجاني: نرجّع الدخول لمن قفلناه بسبب التحويل.
    update public.enrollments
       set status = 'ACTIVE'
     where course_id = new.id and status = 'PAYMENT_REQUIRED';
  end if;
  return new;
end $function$;

drop trigger if exists trg_courses_free_paid_switch on public.courses;
create trigger trg_courses_free_paid_switch
  after update of is_free, base_price on public.courses
  for each row execute function public.courses_free_paid_switch();

-- ── الصلاحيات: الدوال الداخلية للـ service_role فقط (زي باقي دوال الدفع) ───────────────

revoke all on function public._course_monthly_quote(uuid, uuid, text, text) from public, anon, authenticated;
revoke all on function public._course_monthly_confirm(uuid, text, text, text, jsonb) from public, anon, authenticated;
revoke all on function public.expire_monthly_enrollments() from public, anon, authenticated;
revoke all on function public.courses_free_paid_switch() from public, anon, authenticated;
revoke all on function public.enrollments_set_access_type() from public, anon, authenticated;
grant execute on function public._course_monthly_quote(uuid, uuid, text, text) to service_role;
grant execute on function public._course_monthly_confirm(uuid, text, text, text, jsonb) to service_role;
grant execute on function public.expire_monthly_enrollments() to service_role;

-- كل ٥ دقايق: اقفل الاشتراكات الشهرية اللي خلصت.
select cron.schedule('expire-monthly-enrollments', '*/5 * * * *', 'select public.expire_monthly_enrollments()');
