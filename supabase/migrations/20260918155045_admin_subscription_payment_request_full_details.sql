create or replace function public.admin_subscription_payment_requests_full(p_status text default 'PENDING')
returns table(
  id uuid,
  subscription_id uuid,
  approval_request_id uuid,
  user_id uuid,
  teacher_id uuid,
  group_name text,
  kind text,
  brand text,
  account_phone text,
  sender_phone text,
  proof_path text,
  amount numeric,
  currency text,
  note text,
  status text,
  review_note text,
  reviewed_by uuid,
  reviewed_at timestamptz,
  created_at timestamptz,
  student_name text,
  student_email text,
  student_phone text,
  teacher_name text,
  teacher_email text,
  teacher_phone text,
  parent_name text,
  parent_phone text,
  level text,
  start_date date,
  current_renewal_date date,
  requested_renewal_date date,
  billing_cycle text,
  subscription_amount numeric,
  subscription_currency text,
  subscription_status text,
  subscription_approval_status text,
  subscription_notes text
)
language plpgsql
security definer
set search_path = public
as $function$
begin
  if not public.is_staff() then
    raise exception 'FORBIDDEN';
  end if;

  return query
  select
    spr.id,
    spr.subscription_id,
    spr.approval_request_id,
    spr.user_id,
    spr.teacher_id,
    spr.group_name,
    spr.kind,
    spr.brand,
    spr.account_phone,
    spr.sender_phone,
    spr.proof_path,
    spr.amount,
    spr.currency,
    spr.note,
    spr.status,
    spr.review_note,
    spr.reviewed_by,
    spr.reviewed_at,
    spr.created_at,
    coalesce(spr.student_name_override, sp.full_name, sp.email, ts.student_name, 'Student') as student_name,
    sp.email as student_email,
    sp.phone as student_phone,
    coalesce(tp.full_name, 'Teacher') as teacher_name,
    tp.email as teacher_email,
    tp.phone as teacher_phone,
    ts.parent_name,
    ts.parent_phone,
    ts.level,
    ts.start_date,
    ts.next_renewal_date as current_renewal_date,
    case
      when spr.kind = 'RENEWAL' then
        case when ts.billing_cycle = 'WEEKLY'
          then ts.next_renewal_date + interval '1 week'
          else ts.next_renewal_date + interval '1 month'
        end
      else null
    end::date as requested_renewal_date,
    ts.billing_cycle,
    ts.monthly_amount as subscription_amount,
    ts.currency as subscription_currency,
    ts.status as subscription_status,
    ts.approval_status as subscription_approval_status,
    ts.notes as subscription_notes
  from public.subscription_payment_requests spr
  join public.teacher_subscriptions ts on ts.id = spr.subscription_id
  left join public.profiles sp on sp.id = spr.user_id
  left join public.profiles tp on tp.id = spr.teacher_id
  where (p_status is null or spr.status = p_status)
  order by spr.created_at desc;
end;
$function$;

revoke all on function public.admin_subscription_payment_requests_full(text) from public;
grant execute on function public.admin_subscription_payment_requests_full(text) to authenticated;;
