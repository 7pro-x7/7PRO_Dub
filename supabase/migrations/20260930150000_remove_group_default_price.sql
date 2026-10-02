-- لا سعر افتراضي للجروبات: المالك هو اللي بيحدد سعر كل جروب من لوحة الحجز.
-- بيلغي اللي عملناه في 20260930140000 (التريجر ودوال السعر التلقائي) ويرجّع السعر الافتراضي العام لـ 0.
-- الجروب من غير سعر مابيظهرش للطالب في شاشة الحجز لحد ما المالك يحدد سعره.
drop trigger if exists teacher_groups_default_price on public.teacher_groups;
drop function if exists public._teacher_groups_default_price();
drop function if exists public._teacher_group_default_price(uuid, text, uuid);

update public.app_settings
   set value = to_jsonb(0), updated_at = now()
 where key = 'pricing.group_subscription_default';

update public.teacher_groups
   set monthly_price = 0
 where id in ('d52b579f-d3a8-44da-9011-7c1002a2f731', '76c3db6f-deac-4d76-aa7b-757de3d33551',
              '4a346f14-fb72-4025-aa03-c51aae742f84', 'b56aca2a-4d0a-432e-b7e0-29459d0194f1')
   and monthly_price = 400;
