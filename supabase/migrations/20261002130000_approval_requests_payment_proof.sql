-- ============================================================================
-- طلبات التجديد عند المالك: صورة التحويل اللي الطالب رفعها + الوقت الفعلي للعملية.
--
-- list_approval_requests بقت بترجّع كمان:
--   payment_proof_path     مسار صورة التحويل (للمالك/الأدمن بس — المعلم ما بيشوفش الصورة)
--   payment_sender_phone   الرقم اللي اتحوّل منه
--   payment_submitted_at   وقت رفع التحويل بالظبط (timestamptz)
--   payment_reviewed_at    وقت قبول/رفض التحويل بالظبط
--
-- وفيه فلاتر جديدة اختيارية (الاستدعاءات القديمة بتشتغل زي ما هي):
--   p_status = 'DECIDED'   كل الطلبات اللي اتبتّ فيها (موافقة أو رفض) — لسجل التجديدات
--   p_action               نوع الطلب (مثلًا RENEW_SUBSCRIPTION)
--   p_limit                أقصى عدد صفوف
-- ============================================================================

drop function if exists public.list_approval_requests(text, uuid);
drop function if exists public.list_approval_requests(text, uuid, text, int);

create function public.list_approval_requests(
  p_status  text default 'PENDING',
  p_teacher uuid default null,
  p_action  text default null,
  p_limit   int  default null
)
returns table (
  id uuid, teacher_id uuid, action_type text, target_type text, target_id uuid, request_data jsonb,
  status text, reviewed_by uuid, reviewed_at timestamptz, review_note text, created_at timestamptz,
  teacher_name text, teacher_avatar text,
  payment_source text, payment_brand text, payment_amount numeric, payment_currency text,
  payment_proof_path text, payment_sender_phone text,
  payment_submitted_at timestamptz, payment_reviewed_at timestamptz
)
language plpgsql security definer set search_path = public
as $$
declare v_staff boolean := public.is_staff();
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not v_staff and p_teacher is distinct from auth.uid() then raise exception 'FORBIDDEN'; end if;
  return query
  select ar.id, ar.teacher_id, ar.action_type, ar.target_type, ar.target_id, ar.request_data, ar.status,
         ar.reviewed_by, ar.reviewed_at, ar.review_note, ar.created_at, p.full_name, p.avatar_url,
         public._payment_source_of(ar.action_type, ar.status, ar.request_data, pay.status),
         pay.brand, pay.amount, pay.currency,
         case when v_staff then pay.proof_path end,
         case when v_staff then pay.sender_phone end,
         pay.created_at, pay.reviewed_at
    from public.approval_requests ar
    left join public.profiles p on p.id = ar.teacher_id
    left join public.subscription_payment_requests pay on pay.approval_request_id = ar.id
   where (p_status is null
          or (p_status = 'DECIDED' and ar.status <> 'PENDING')
          or ar.status = p_status)
     and (p_teacher is null or ar.teacher_id = p_teacher)
     and (p_action is null or ar.action_type = p_action)
   order by ar.created_at desc
   limit p_limit;
end $$;

revoke all on function public.list_approval_requests(text, uuid, text, int) from public, anon;
grant execute on function public.list_approval_requests(text, uuid, text, int) to authenticated;
