-- ============================================================================
-- دردشة مباشرة مع خدمة العملاء (رسائل نصية فقط) بدل التذاكر.
--   * support_chats: محادثة لكل طالب (واحدة مفتوحة في المرة)، بموضوع وحالة
--     WAITING (مستنية حد يستلمها) → OPEN (مع حد من الفريق) → CLOSED (+ تقييم).
--   * support_chat_messages: TEXT (رسالة) / NOTE (ملاحظة داخلية للفريق بس) / EVENT (حدث: استلام، تحويل، إنهاء).
--   * support_availability: زر «متاح للرد» لكل عضو في الفريق.
-- كل الكتابة من خلال دوال السيرفر؛ القراءة بـ RLS (الطالب عمره ما يشوف الملاحظات الداخلية).
-- الفريق = المالك + أي أدمن معاه support.manage.
-- ============================================================================

create table if not exists public.support_chats (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  topic text not null default 'OTHER' check (topic in ('PAYMENT', 'COURSE', 'ACCOUNT', 'OTHER')),
  status text not null default 'WAITING' check (status in ('WAITING', 'OPEN', 'CLOSED')),
  assigned_to uuid references public.profiles(id) on delete set null,
  last_message_at timestamptz not null default now(),
  last_message_preview text,
  last_sender text,
  user_unread int not null default 0,
  staff_unread int not null default 0,
  user_last_read_at timestamptz,
  staff_last_read_at timestamptz,
  first_response_at timestamptz,
  closed_at timestamptz,
  closed_by uuid references public.profiles(id) on delete set null,
  rating smallint check (rating between 1 and 5),
  rating_comment text,
  rated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists support_chats_one_active on public.support_chats (user_id) where status <> 'CLOSED';
create index if not exists support_chats_status_idx on public.support_chats (status, last_message_at desc);
create index if not exists support_chats_assignee_idx on public.support_chats (assigned_to) where status <> 'CLOSED';
alter table public.support_chats enable row level security;

create table if not exists public.support_chat_messages (
  id bigint generated always as identity primary key,
  chat_id uuid not null references public.support_chats(id) on delete cascade,
  sender_id uuid references public.profiles(id) on delete set null,
  kind text not null default 'TEXT' check (kind in ('TEXT', 'NOTE', 'EVENT')),
  from_staff boolean not null default false,
  body text not null check (char_length(body) between 1 and 2000),
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists support_chat_messages_chat_idx on public.support_chat_messages (chat_id, id);
alter table public.support_chat_messages enable row level security;

create table if not exists public.support_availability (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  available boolean not null default true,
  updated_at timestamptz not null default now()
);
alter table public.support_availability enable row level security;

drop policy if exists support_chats_read on public.support_chats;
create policy support_chats_read on public.support_chats for select
  using (user_id = auth.uid() or public.has_permission('support.manage'));

drop policy if exists support_chat_messages_read on public.support_chat_messages;
create policy support_chat_messages_read on public.support_chat_messages for select
  using (
    public.has_permission('support.manage')
    or (kind <> 'NOTE' and exists (select 1 from public.support_chats c where c.id = chat_id and c.user_id = auth.uid()))
  );

drop policy if exists support_availability_read on public.support_availability;
create policy support_availability_read on public.support_availability for select
  using (public.has_permission('support.manage'));

do $$ begin
  begin alter publication supabase_realtime add table public.support_chats; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.support_chat_messages; exception when duplicate_object then null; end;
end $$;

-- ── helpers ────────────────────────────────────────────────────────────────
create or replace function public._support_staff_ids()
returns setof uuid language sql stable security definer set search_path = public
as $$
  select p.id from public.profiles p
   where p.status::text = 'ACTIVE'
     and (p.role = 'OWNER'
          or (p.role = 'ADMIN' and exists (select 1 from public.admin_permissions ap
                                           where ap.user_id = p.id and ap.permission = 'support.manage')));
$$;

create or replace function public._support_preview(t text)
returns text language sql immutable
as $$ select left(btrim(regexp_replace(coalesce(t, ''), '\s+', ' ', 'g')), 140); $$;

create or replace function public._support_first_name(p_user uuid)
returns text language sql stable security definer set search_path = public
as $$
  select coalesce(nullif(split_part(btrim(full_name), ' ', 1), ''), split_part(email, '@', 1), '7PRO')
    from public.profiles where id = p_user;
$$;

-- يكتب رسالة ويحدّث ملخص المحادثة والعدادات
create or replace function public._support_post(p_chat uuid, p_sender uuid, p_kind text, p_staff boolean, p_body text, p_meta jsonb default '{}'::jsonb)
returns public.support_chat_messages
language plpgsql security definer set search_path = public
as $$
declare m public.support_chat_messages;
begin
  insert into public.support_chat_messages (chat_id, sender_id, kind, from_staff, body, meta)
  values (p_chat, p_sender, p_kind, p_staff, p_body, coalesce(p_meta, '{}'::jsonb))
  returning * into m;

  if p_kind = 'TEXT' then
    update public.support_chats set
      last_message_at = m.created_at,
      last_message_preview = public._support_preview(p_body),
      last_sender = case when p_staff then 'STAFF' else 'USER' end,
      user_unread = case when p_staff then user_unread + 1 else 0 end,
      staff_unread = case when p_staff then 0 else staff_unread + 1 end,
      user_last_read_at = case when p_staff then user_last_read_at else m.created_at end,
      staff_last_read_at = case when p_staff then m.created_at else staff_last_read_at end,
      first_response_at = case when p_staff then coalesce(first_response_at, m.created_at) else first_response_at end,
      updated_at = now()
    where id = p_chat;
  else
    update public.support_chats set updated_at = now() where id = p_chat;
  end if;
  return m;
end $$;

-- ── الطالب ───────────────────────────────────────────────────────────────────

-- بيبدأ محادثة (أو يكمل المفتوحة لو فيه)؛ بيرجع رقم المحادثة
create or replace function public.support_start_chat(p_topic text, p_body text)
returns uuid
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_body text := btrim(coalesce(p_body, ''));
  v_topic text := case when p_topic in ('PAYMENT', 'COURSE', 'ACCOUNT', 'OTHER') then p_topic else 'OTHER' end;
  c public.support_chats;
  v_targets uuid[];
  v_name text;
  s uuid;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if v_body = '' then raise exception 'MESSAGE_EMPTY'; end if;
  if char_length(v_body) > 2000 then raise exception 'MESSAGE_TOO_LONG'; end if;

  select * into c from public.support_chats where user_id = v_uid and status <> 'CLOSED' for update;
  if found then
    perform public.support_send(c.id, v_body, false);
    return c.id;
  end if;

  insert into public.support_chats (user_id, topic) values (v_uid, v_topic) returning * into c;
  perform public._support_post(c.id, v_uid, 'TEXT', false, v_body);

  -- يرن على الفريق المتاح؛ لو مفيش حد متاح يرن على الكل
  select array_agg(id) into v_targets from public._support_staff_ids() id
   where id <> v_uid and coalesce((select available from public.support_availability a where a.user_id = id), true);
  if v_targets is null then
    select array_agg(id) into v_targets from public._support_staff_ids() id where id <> v_uid;
  end if;
  select coalesce(nullif(btrim(full_name), ''), email) into v_name from public.profiles where id = v_uid;
  foreach s in array coalesce(v_targets, '{}') loop
    perform public.push_notification(s, 'SUPPORT',
      'محادثة دعم جديدة • New support chat',
      coalesce(v_name, '') || ': ' || public._support_preview(v_body),
      jsonb_build_object('route', 'admin/support/chat/' || c.id, 'support_chat_id', c.id));
  end loop;
  return c.id;
end $$;

-- رسالة من الطالب أو من الفريق (p_note = ملاحظة داخلية للفريق بس)
create or replace function public.support_send(p_chat uuid, p_body text, p_note boolean default false)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_body text := btrim(coalesce(p_body, ''));
  c public.support_chats;
  v_is_user boolean;
  v_is_staff boolean;
  v_recent int;
  m public.support_chat_messages;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  if v_body = '' then raise exception 'MESSAGE_EMPTY'; end if;
  if char_length(v_body) > 2000 then raise exception 'MESSAGE_TOO_LONG'; end if;

  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  v_is_user := c.user_id = v_uid;
  v_is_staff := not v_is_user and public.has_permission('support.manage');
  if not v_is_user and not v_is_staff then raise exception 'FORBIDDEN'; end if;
  if c.status = 'CLOSED' then raise exception 'CHAT_CLOSED'; end if;
  if coalesce(p_note, false) and not v_is_staff then raise exception 'FORBIDDEN'; end if;

  select count(*) into v_recent from public.support_chat_messages
   where sender_id = v_uid and created_at > now() - interval '1 minute';
  if v_recent >= 20 then raise exception 'TOO_MANY_MESSAGES'; end if;

  if coalesce(p_note, false) then
    m := public._support_post(c.id, v_uid, 'NOTE', true, v_body);
    return to_jsonb(m);
  end if;

  if v_is_staff then
    if c.assigned_to is null then
      update public.support_chats set assigned_to = v_uid, status = 'OPEN' where id = c.id;
      perform public._support_post(c.id, v_uid, 'EVENT', true, 'CLAIMED',
        jsonb_build_object('name', public._support_first_name(v_uid)));
    elsif c.assigned_to <> v_uid then
      raise exception 'ASSIGNED_TO_OTHER';
    end if;
    m := public._support_post(c.id, v_uid, 'TEXT', true, v_body);
    -- إشعار واحد لحد ما الطالب يقرا، مش إشعار لكل رسالة
    if c.user_unread = 0 then
      perform public.push_notification(c.user_id, 'SUPPORT',
        'رد جديد من الدعم • New reply from support',
        public._support_first_name(v_uid) || ': ' || public._support_preview(v_body),
        jsonb_build_object('route', 'support/chat/' || c.id, 'support_chat_id', c.id));
    end if;
  else
    m := public._support_post(c.id, v_uid, 'TEXT', false, v_body);
    if c.assigned_to is not null and c.staff_unread = 0 then
      perform public.push_notification(c.assigned_to, 'SUPPORT',
        'رسالة جديدة في الدعم • New support message',
        public._support_first_name(v_uid) || ': ' || public._support_preview(v_body),
        jsonb_build_object('route', 'admin/support/chat/' || c.id, 'support_chat_id', c.id));
    end if;
  end if;
  return to_jsonb(m);
end $$;

create or replace function public.support_mark_read(p_chat uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare c public.support_chats;
begin
  select * into c from public.support_chats where id = p_chat;
  if not found then return; end if;
  if c.user_id = auth.uid() then
    update public.support_chats set user_unread = 0, user_last_read_at = now() where id = p_chat;
  elsif public.has_permission('support.manage') and (c.assigned_to is null or c.assigned_to = auth.uid()) then
    update public.support_chats set staff_unread = 0, staff_last_read_at = now() where id = p_chat;
  end if;
end $$;

create or replace function public.support_rate(p_chat uuid, p_rating int, p_comment text default null)
returns void
language plpgsql security definer set search_path = public
as $$
declare c public.support_chats;
begin
  select * into c from public.support_chats where id = p_chat for update;
  if not found or c.user_id <> auth.uid() then raise exception 'FORBIDDEN'; end if;
  if c.status <> 'CLOSED' then raise exception 'CHAT_NOT_CLOSED'; end if;
  if c.rating is not null then raise exception 'ALREADY_RATED'; end if;
  if p_rating is null or p_rating < 1 or p_rating > 5 then raise exception 'INVALID_RATING'; end if;
  update public.support_chats
     set rating = p_rating, rating_comment = nullif(left(btrim(coalesce(p_comment, '')), 500), ''), rated_at = now()
   where id = p_chat;
end $$;

-- حالة الفريق لصفحة الطالب
create or replace function public.support_status()
returns jsonb
language sql stable security definer set search_path = public
as $$
  with online as (
    select p.id, public._support_first_name(p.id) as name
      from public._support_staff_ids() s join public.profiles p on p.id = s
     where coalesce((select available from public.support_availability a where a.user_id = p.id), true)
       and p.last_seen_at > now() - interval '10 minutes'
     order by p.last_seen_at desc
     limit 3)
  select jsonb_build_object(
    'online', exists (select 1 from online),
    'agents', coalesce((select jsonb_agg(name) from online), '[]'::jsonb));
$$;

create or replace function public.support_my_chats()
returns table (id uuid, topic text, status text, agent_name text, last_message_at timestamptz,
               last_message_preview text, last_sender text, user_unread int, rating smallint, created_at timestamptz)
language sql stable security definer set search_path = public
as $$
  select c.id, c.topic, c.status,
         case when c.assigned_to is not null then public._support_first_name(c.assigned_to) end,
         c.last_message_at, c.last_message_preview, c.last_sender, c.user_unread, c.rating, c.created_at
    from public.support_chats c
   where c.user_id = auth.uid()
   order by (c.status <> 'CLOSED') desc, c.last_message_at desc
   limit 30;
$$;

-- ── الفريق ───────────────────────────────────────────────────────────────────

create or replace function public._support_require_staff()
returns uuid language plpgsql stable security definer set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'UNAUTHORIZED'; end if;
  if not public.has_permission('support.manage') then raise exception 'FORBIDDEN'; end if;
  return auth.uid();
end $$;

create or replace function public.support_claim(p_chat uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := public._support_require_staff(); c public.support_chats;
begin
  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  if c.status = 'CLOSED' then raise exception 'CHAT_CLOSED'; end if;
  if c.assigned_to = v_uid then return; end if;
  if c.assigned_to is not null then raise exception 'ALREADY_CLAIMED'; end if;
  update public.support_chats set assigned_to = v_uid, status = 'OPEN' where id = p_chat;
  perform public._support_post(p_chat, v_uid, 'EVENT', true, 'CLAIMED',
    jsonb_build_object('name', public._support_first_name(v_uid)));
end $$;

create or replace function public.support_transfer(p_chat uuid, p_to uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := public._support_require_staff(); c public.support_chats; v_name text;
begin
  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  if c.status = 'CLOSED' then raise exception 'CHAT_CLOSED'; end if;
  if not exists (select 1 from public._support_staff_ids() s where s = p_to) then raise exception 'NOT_SUPPORT_STAFF'; end if;
  if c.assigned_to is not distinct from p_to then return; end if;
  v_name := public._support_first_name(p_to);
  update public.support_chats set assigned_to = p_to, status = 'OPEN', staff_unread = greatest(staff_unread, 1) where id = p_chat;
  perform public._support_post(p_chat, v_uid, 'EVENT', true, 'TRANSFERRED', jsonb_build_object('name', v_name));
  perform public.push_notification(p_to, 'SUPPORT',
    'اتحولت لك محادثة دعم • A support chat was passed to you',
    public._support_first_name(v_uid) || ' → ' || v_name,
    jsonb_build_object('route', 'admin/support/chat/' || p_chat, 'support_chat_id', p_chat));
end $$;

-- الإنهاء: من الفريق أو من الطالب نفسه
create or replace function public.support_close(p_chat uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid(); c public.support_chats; v_staff boolean;
begin
  if v_uid is null then raise exception 'UNAUTHORIZED'; end if;
  select * into c from public.support_chats where id = p_chat for update;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  v_staff := c.user_id <> v_uid and public.has_permission('support.manage');
  if c.user_id <> v_uid and not v_staff then raise exception 'FORBIDDEN'; end if;
  if c.status = 'CLOSED' then return; end if;
  update public.support_chats set status = 'CLOSED', closed_at = now(), closed_by = v_uid, staff_unread = 0 where id = p_chat;
  perform public._support_post(p_chat, v_uid, 'EVENT', v_staff, 'CLOSED',
    jsonb_build_object('name', public._support_first_name(v_uid), 'by_staff', v_staff));
  if v_staff then
    perform public.push_notification(c.user_id, 'SUPPORT',
      'انتهت محادثة الدعم • Support chat ended',
      'قيّم تجربتك مع ' || public._support_first_name(v_uid) || ' • Rate your chat',
      jsonb_build_object('route', 'support/chat/' || p_chat, 'support_chat_id', p_chat));
  end if;
end $$;

create or replace function public.support_set_available(p_available boolean)
returns void
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := public._support_require_staff();
begin
  insert into public.support_availability (user_id, available, updated_at) values (v_uid, coalesce(p_available, true), now())
  on conflict (user_id) do update set available = excluded.available, updated_at = now();
end $$;

create or replace function public.support_staff_state()
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
declare v_uid uuid := public._support_require_staff();
begin
  return jsonb_build_object(
    'available', coalesce((select available from public.support_availability where user_id = v_uid), true),
    'waiting', (select count(*) from public.support_chats where status = 'WAITING'),
    'mine', (select count(*) from public.support_chats where status = 'OPEN' and assigned_to = v_uid),
    'mine_unread', (select count(*) from public.support_chats where status = 'OPEN' and assigned_to = v_uid and staff_unread > 0),
    'team', (select count(*) from public.support_chats where status = 'OPEN' and assigned_to is distinct from v_uid));
end $$;

-- صندوق المحادثات: WAITING / MINE / ALL (المفتوحة) / CLOSED + بحث دقيق بالاسم والبريد ونص الرسائل
create or replace function public.support_inbox(p_filter text default 'ALL', p_query text default null, p_limit int default 100)
returns table (id uuid, user_id uuid, full_name text, email text, avatar_url text, topic text, status text,
               assigned_to uuid, assigned_name text, last_message_at timestamptz, last_message_preview text,
               last_sender text, staff_unread int, rating smallint, created_at timestamptz)
language plpgsql stable security definer set search_path = public
as $$
declare
  v_uid uuid := public._support_require_staff();
  v_q text := public._grant_norm(p_query);
  v_tokens text[] := coalesce(string_to_array(nullif(v_q, ''), ' '), '{}');
begin
  return query
  select c.id, c.user_id, p.full_name, p.email, p.avatar_url, c.topic, c.status,
         c.assigned_to, case when c.assigned_to is not null then public._support_first_name(c.assigned_to) end,
         c.last_message_at, c.last_message_preview, c.last_sender, c.staff_unread, c.rating, c.created_at
    from public.support_chats c
    join public.profiles p on p.id = c.user_id
   where case upper(coalesce(p_filter, 'ALL'))
           when 'WAITING' then c.status = 'WAITING'
           when 'MINE' then c.status = 'OPEN' and c.assigned_to = v_uid
           when 'CLOSED' then c.status = 'CLOSED'
           else c.status <> 'CLOSED'
         end
     and not exists (
       select 1 from unnest(v_tokens) tok
        where position(tok in public._grant_norm(coalesce(p.full_name, '') || ' ' || coalesce(p.email, ''))) = 0
          and not exists (select 1 from public.support_chat_messages m
                           where m.chat_id = c.id and m.kind <> 'EVENT'
                             and position(tok in public._grant_norm(m.body)) > 0))
   order by case when c.status = 'WAITING' then 0 else 1 end,
            case when c.status = 'WAITING' then extract(epoch from c.created_at) else -extract(epoch from c.last_message_at) end
   limit least(greatest(coalesce(p_limit, 100), 1), 200);
end $$;

create or replace function public.support_team()
returns table (user_id uuid, full_name text, avatar_url text, available boolean, online boolean)
language plpgsql stable security definer set search_path = public
as $$
begin
  perform public._support_require_staff();
  return query
  select p.id, p.full_name, p.avatar_url,
         coalesce((select a.available from public.support_availability a where a.user_id = p.id), true),
         coalesce(p.last_seen_at > now() - interval '10 minutes', false)
    from public._support_staff_ids() s join public.profiles p on p.id = s
   order by p.full_name nulls last;
end $$;

-- تفاصيل المحادثة للشاشة: للطالب (اسم اللي بيرد عليه) وللفريق (بيانات الطالب وكورساته)
create or replace function public.support_chat_info(p_chat uuid)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
declare c public.support_chats; v_staff boolean; u public.profiles;
begin
  select * into c from public.support_chats where id = p_chat;
  if not found then raise exception 'CHAT_NOT_FOUND'; end if;
  v_staff := c.user_id <> auth.uid() and public.has_permission('support.manage');
  if c.user_id <> auth.uid() and not v_staff then raise exception 'FORBIDDEN'; end if;

  if not v_staff then
    return jsonb_build_object(
      'id', c.id, 'topic', c.topic, 'status', c.status, 'rating', c.rating,
      'agent_name', case when c.assigned_to is not null then public._support_first_name(c.assigned_to) end,
      'agent_avatar', (select avatar_url from public.profiles where id = c.assigned_to),
      'agent_online', coalesce((select last_seen_at > now() - interval '10 minutes' from public.profiles where id = c.assigned_to), false),
      'other_last_read_at', c.staff_last_read_at,
      'is_staff_view', false);
  end if;

  select * into u from public.profiles where id = c.user_id;
  return jsonb_build_object(
    'id', c.id, 'topic', c.topic, 'status', c.status, 'rating', c.rating, 'rating_comment', c.rating_comment,
    'assigned_to', c.assigned_to,
    'agent_name', case when c.assigned_to is not null then public._support_first_name(c.assigned_to) end,
    'other_last_read_at', c.user_last_read_at,
    'is_staff_view', true,
    'user', jsonb_build_object(
      'id', u.id, 'full_name', u.full_name, 'email', u.email, 'avatar_url', u.avatar_url,
      'online', coalesce(u.last_seen_at > now() - interval '10 minutes', false)),
    'courses', coalesce((
      select jsonb_agg(jsonb_build_object('id', co.id, 'title', co.title, 'title_ar', co.title_ar, 'title_en', co.title_en,
                                          'access_type', e.access_type) order by e.created_at desc)
        from public.enrollments e join public.courses co on co.id = e.course_id
       where e.user_id = c.user_id and e.status = 'ACTIVE' and (e.expires_at is null or e.expires_at > now())), '[]'::jsonb));
end $$;

-- لوحة المالك: «يحتاج انتباه» بقت تعد المحادثات المستنية أو فيها رسائل جديدة بدل التذاكر
do $$
declare d text;
begin
  d := pg_get_functiondef('public.owner_analytics'::regproc);
  d := replace(d,
    $q$(select count(*) from public.support_tickets where status in ('OPEN','ASSIGNED'))$q$,
    $q$(select count(*) from public.support_chats where status = 'WAITING' or (status = 'OPEN' and staff_unread > 0))$q$);
  execute d;
end $$;

revoke all on function public._support_staff_ids() from public, anon, authenticated;
revoke all on function public._support_post(uuid, uuid, text, boolean, text, jsonb) from public, anon, authenticated;
revoke all on function public._support_require_staff() from public, anon, authenticated;
revoke all on function public._support_first_name(uuid) from public, anon, authenticated;
revoke all on function public.support_start_chat(text, text) from public, anon;
revoke all on function public.support_send(uuid, text, boolean) from public, anon;
revoke all on function public.support_mark_read(uuid) from public, anon;
revoke all on function public.support_rate(uuid, int, text) from public, anon;
revoke all on function public.support_status() from public, anon;
revoke all on function public.support_my_chats() from public, anon;
revoke all on function public.support_claim(uuid) from public, anon;
revoke all on function public.support_transfer(uuid, uuid) from public, anon;
revoke all on function public.support_close(uuid) from public, anon;
revoke all on function public.support_set_available(boolean) from public, anon;
revoke all on function public.support_staff_state() from public, anon;
revoke all on function public.support_inbox(text, text, int) from public, anon;
revoke all on function public.support_team() from public, anon;
revoke all on function public.support_chat_info(uuid) from public, anon;
grant execute on function public.support_start_chat(text, text) to authenticated;
grant execute on function public.support_send(uuid, text, boolean) to authenticated;
grant execute on function public.support_mark_read(uuid) to authenticated;
grant execute on function public.support_rate(uuid, int, text) to authenticated;
grant execute on function public.support_status() to authenticated;
grant execute on function public.support_my_chats() to authenticated;
grant execute on function public.support_claim(uuid) to authenticated;
grant execute on function public.support_transfer(uuid, uuid) to authenticated;
grant execute on function public.support_close(uuid) to authenticated;
grant execute on function public.support_set_available(boolean) to authenticated;
grant execute on function public.support_staff_state() to authenticated;
grant execute on function public.support_inbox(text, text, int) to authenticated;
grant execute on function public.support_team() to authenticated;
grant execute on function public.support_chat_info(uuid) to authenticated;
