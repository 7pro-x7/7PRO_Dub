-- ============================================================================
-- إظهار «إزاي اتفعّل الاشتراك» للمالك والمعلم:
--   APP_PAID         : الطالب دفع من التطبيق (إثبات تحويل مرفوع من حسابه)
--   TEACHER_MANUAL   : المعلم أضافه يدويًا (الدفع خارج التطبيق)
--   NO_PAYMENT       : الطالب طلب من التطبيق واتوافق عليه من غير إثبات دفع مقبول
--   AWAITING_REVIEW  : الطالب رفع إثبات الدفع وبانتظار المراجعة
--   AWAITING_PAYMENT : الطالب طلب ولسه ما دفعش
-- بتتحسب وقت القراءة من approval_requests + subscription_payment_requests، فمفيش
-- أعمدة جديدة ولا تريجرز، والتاريخ القديم بيتصنّف لوحده.
-- ============================================================================

create or replace function public._payment_source_of(p_action text, p_status text, p_data jsonb, p_pay_status text)
returns text
language sql immutable
as $$
  select case
    when p_action not in ('ADD_SUBSCRIPTION', 'RENEW_SUBSCRIPTION') then null
    when coalesce(p_data->>'self_service', 'false') <> 'true' then 'TEACHER_MANUAL'
    when p_status = 'REJECTED' then null
    when p_pay_status is not null and p_pay_status <> 'REJECTED' and p_status = 'APPROVED' then 'APP_PAID'
    when p_pay_status = 'APPROVED' then 'APP_PAID'
    when p_status = 'APPROVED' then 'NO_PAYMENT'
    when p_pay_status = 'PENDING' then 'AWAITING_REVIEW'
    when p_status = 'PENDING' then 'AWAITING_PAYMENT'
    else null
  end;
$$;

-- نفس الدالة القديمة + أعمدة الدفع. (كانت مفتوحة لأي مستخدم مسجّل؛ دلوقتي المالك/الأدمن أو المعلم لطلباته بس)
drop function if exists public.list_approval_requests(text, uuid);
create function public.list_approval_requests(p_status text default 'PENDING', p_teacher uuid default null)
returns table (
  id uuid, teacher_id uuid, action_type text, target_type text, target_id uuid, request_data jsonb,
  status text, reviewed_by uuid, reviewed_at timestamptz, review_note text, created_at timestamptz,
  teacher_name text, teacher_avatar text,
  payment_source text, payment_brand text, payment_amount numeric, payment_currency text
)
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.is_staff() and p_teacher is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  return query
  select ar.id, ar.teacher_id, ar.action_type, ar.target_type, ar.target_id, ar.request_data, ar.status,
         ar.reviewed_by, ar.reviewed_at, ar.review_note, ar.created_at, p.full_name, p.avatar_url,
         public._payment_source_of(ar.action_type, ar.status, ar.request_data, pay.status),
         pay.brand, pay.amount, pay.currency
    from public.approval_requests ar
    left join public.profiles p on p.id = ar.teacher_id
    left join public.subscription_payment_requests pay on pay.approval_request_id = ar.id
   where (p_status is null or ar.status = p_status)
     and (p_teacher is null or ar.teacher_id = p_teacher)
   order by ar.created_at desc;
end $$;
revoke all on function public.list_approval_requests(text, uuid) from public, anon;
grant execute on function public.list_approval_requests(text, uuid) to authenticated;

-- مصدر آخر تفعيل (أو طلب معلّق) لمجموعة اشتراكات. للمالك/الأدمن كلها، وللمعلم اشتراكاته بس.
-- اشتراك من غير أي طلب إضافة = المعلم أضافه مباشرة (يدويًا) والدفع خارج التطبيق.
create or replace function public.subscription_payment_sources(p_subscription_ids uuid[])
returns table (subscription_id uuid, source text, brand text, amount numeric, currency text, at timestamptz)
language plpgsql stable security definer set search_path = public
as $$
declare v_staff boolean := public.is_staff();
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  return query
  select s.id,
         coalesce(appr.src,
                  case when s.approval_status = 'APPROVED'
                        and not exists (select 1 from public.approval_requests a
                                         where a.target_type = 'subscription' and a.target_id = s.id
                                           and a.action_type = 'ADD_SUBSCRIPTION')
                       then 'TEACHER_MANUAL' end,
                  pend.src),
         case when appr.src is not null then appr.brand else pend.brand end,
         case when appr.src is not null then appr.amount else pend.amount end,
         case when appr.src is not null then appr.currency else pend.currency end,
         coalesce(appr.at, s.created_at)
    from public.teacher_subscriptions s
    left join lateral (
      select public._payment_source_of(ar.action_type, ar.status, ar.request_data, pay.status) src,
             pay.brand, pay.amount, pay.currency, coalesce(ar.reviewed_at, ar.created_at) at
        from public.approval_requests ar
        left join public.subscription_payment_requests pay on pay.approval_request_id = ar.id
       where ar.target_type = 'subscription' and ar.target_id = s.id
         and ar.action_type in ('ADD_SUBSCRIPTION', 'RENEW_SUBSCRIPTION') and ar.status = 'APPROVED'
       order by coalesce(ar.reviewed_at, ar.created_at) desc
       limit 1
    ) appr on true
    left join lateral (
      select public._payment_source_of(ar.action_type, ar.status, ar.request_data, pay.status) src,
             pay.brand, pay.amount, pay.currency
        from public.approval_requests ar
        left join public.subscription_payment_requests pay on pay.approval_request_id = ar.id
       where ar.target_type = 'subscription' and ar.target_id = s.id
         and ar.action_type = 'ADD_SUBSCRIPTION' and ar.status = 'PENDING'
       order by ar.created_at desc
       limit 1
    ) pend on true
   where s.id = any (p_subscription_ids)
     and (v_staff or s.teacher_id = auth.uid())
     and coalesce(appr.src,
                  case when s.approval_status = 'APPROVED'
                        and not exists (select 1 from public.approval_requests a
                                         where a.target_type = 'subscription' and a.target_id = s.id
                                           and a.action_type = 'ADD_SUBSCRIPTION')
                       then 'TEACHER_MANUAL' end,
                  pend.src) is not null;
end $$;
revoke all on function public.subscription_payment_sources(uuid[]) from public, anon;
grant execute on function public.subscription_payment_sources(uuid[]) to authenticated;
