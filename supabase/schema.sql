-- =============================================================================
-- BLADE SHORT-VIDEO SOCIAL MEDIA APP — SUPABASE POSTGRESQL SCHEMA
-- Run this script in your Supabase Project -> SQL Editor
-- =============================================================================

create extension if not exists "pgcrypto";

-- 1. USERS (Profiles linked to auth.users)
create table if not exists public.users (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null,
  display_name text not null default '',
  email text,
  phone text,
  bio text not null default '',
  avatar_url text not null default '',
  cover_url text not null default '',
  is_verified boolean not null default false,
  verification_status text not null default 'pending' check (verification_status in ('none', 'pending', 'verified', 'rejected')),
  is_blocked boolean not null default false,
  is_private boolean not null default false,
  role text not null default 'user' check (role in ('user', 'creator', 'admin')),
  followers_count integer not null default 0,
  following_count integer not null default 0,
  total_likes integer not null default 0,
  coins_balance integer not null default 100,
  earnings_usd numeric(10, 2) not null default 0.00,
  created_at timestamptz not null default now()
);

alter table public.users add column if not exists is_blocked boolean not null default false;
alter table public.users add column if not exists verification_status text not null default 'pending';
update public.users
set verification_status = case when is_verified then 'verified' else 'pending' end
where verification_status is null or verification_status = 'none';
create unique index if not exists idx_users_email_lower on public.users (lower(email)) where email is not null;

-- 2. SOUNDS
create table if not exists public.sounds (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  artist text not null,
  audio_url text not null default '',
  cover_url text not null default '',
  duration_seconds integer not null default 15,
  usage_count integer not null default 0,
  created_at timestamptz not null default now()
);

-- 3. VIDEOS
create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  video_url text not null,
  thumbnail_url text not null default '',
  title text not null default '',
  caption text not null default '',
  hashtags text[] not null default '{}',
  category text not null default 'All',
  location text not null default '',
  privacy text not null default 'public' check (privacy in ('public', 'friends', 'private')),
  sound_id uuid references public.sounds(id) on delete set null,
  sound_title text not null default 'Original Sound',
  filter_name text not null default 'Normal',
  overlay_text text not null default '',
  trim_start_ms bigint not null default 0,
  trim_end_ms bigint not null default 0,
  duration_ms bigint not null default 0,
  views_count integer not null default 0,
  likes_count integer not null default 0,
  comments_count integer not null default 0,
  shares_count integer not null default 0,
  saves_count integer not null default 0,
  is_sponsored boolean not null default false,
  sponsor_label text not null default '',
  created_at timestamptz not null default now()
);

alter table public.videos add column if not exists title text not null default '';

-- 4. COMMENTS
create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.users(id) on delete cascade,
  parent_id uuid references public.comments(id) on delete cascade,
  content text not null,
  likes_count integer not null default 0,
  created_at timestamptz not null default now()
);

-- 5. LIKES
create table if not exists public.likes (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(video_id, user_id)
);

-- 6. SAVED VIDEOS
create table if not exists public.saved_videos (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(video_id, user_id)
);

-- 7. FOLLOWERS
create table if not exists public.followers (
  id uuid primary key default gen_random_uuid(),
  follower_id uuid not null references public.users(id) on delete cascade,
  following_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(follower_id, following_id)
);

-- 8. HASHTAGS
create table if not exists public.hashtags (
  id uuid primary key default gen_random_uuid(),
  tag text unique not null,
  videos_count integer not null default 0,
  views_count bigint not null default 0,
  is_trending boolean not null default true,
  created_at timestamptz not null default now()
);

-- 9. NOTIFICATIONS
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  actor_id uuid references public.users(id) on delete set null,
  type text not null check (type in ('like', 'comment', 'follow', 'mention', 'message', 'live', 'gift')),
  title text not null,
  body text not null,
  video_id uuid references public.videos(id) on delete cascade,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

-- 10. CONVERSATIONS & MESSAGES
create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  participant_one uuid not null references public.users(id) on delete cascade,
  participant_two uuid not null references public.users(id) on delete cascade,
  last_message text not null default '',
  updated_at timestamptz not null default now(),
  unique(participant_one, participant_two)
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.users(id) on delete cascade,
  client_id text not null default '',
  message_type text not null default 'text' check (message_type in ('text', 'image', 'voice')),
  content text not null default '',
  media_url text not null default '',
  media_duration_ms integer not null default 0,
  status text not null default 'sent' check (status in ('sending', 'sent', 'delivered', 'read')),
  is_read boolean not null default false,
  delivered_at timestamptz,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.messages add column if not exists client_id text not null default '';
alter table public.messages add column if not exists message_type text not null default 'text';
alter table public.messages add column if not exists media_url text not null default '';
alter table public.messages add column if not exists media_duration_ms integer not null default 0;
alter table public.messages add column if not exists status text not null default 'sent';
alter table public.messages add column if not exists delivered_at timestamptz;
alter table public.messages add column if not exists read_at timestamptz;
update public.messages set status = 'read', read_at = coalesce(read_at, created_at), delivered_at = coalesce(delivered_at, created_at) where is_read = true and status <> 'read';
create unique index if not exists idx_messages_conv_client_id on public.messages(conversation_id, client_id) where client_id <> '';

-- 11. LIVE STREAMS
create table if not exists public.live_streams (
  id uuid primary key default gen_random_uuid(),
  host_id uuid not null references public.users(id) on delete cascade,
  title text not null,
  stream_url text not null default '',
  viewers_count integer not null default 0,
  is_live boolean not null default true,
  started_at timestamptz not null default now()
);

-- 12. GIFTS & TRANSACTIONS
create table if not exists public.gifts (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  icon_name text not null default 'diamond',
  coin_cost integer not null
);

create table if not exists public.transactions (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.users(id) on delete cascade,
  receiver_id uuid references public.users(id) on delete set null,
  video_id uuid references public.videos(id) on delete set null,
  type text not null check (type in ('gift', 'reward_ad', 'coin_purchase', 'creator_payout')),
  coins_amount integer not null,
  amount_usd numeric(10, 2) not null default 0.00,
  description text not null default '',
  created_at timestamptz not null default now()
);

-- 13. ADVERTISEMENTS
create table if not exists public.advertisements (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  sponsor_name text not null,
  media_url text not null default '',
  cta_url text not null default '',
  placement text not null default 'feed' check (placement in ('feed', 'banner', 'rewarded')),
  is_active boolean not null default true,
  impressions integer not null default 0,
  created_at timestamptz not null default now()
);

-- 14. REPORTS & BLOCKED USERS
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.users(id) on delete cascade,
  video_id uuid references public.videos(id) on delete cascade,
  reported_user_id uuid references public.users(id) on delete cascade,
  reason text not null,
  status text not null default 'pending' check (status in ('pending', 'reviewed', 'dismissed')),
  created_at timestamptz not null default now()
);

create table if not exists public.blocked_users (
  id uuid primary key default gen_random_uuid(),
  blocker_id uuid not null references public.users(id) on delete cascade,
  blocked_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(blocker_id, blocked_id)
);

-- 15. APPLICATION SETTINGS & ADMIN CREDENTIALS
create table if not exists public.app_settings (
  key text primary key,
  value text not null default '',
  updated_at timestamptz not null default now()
);

insert into public.app_settings (key, value) values
  ('allow_registrations', 'true'),
  ('allow_video_uploads', 'true'),
  ('allow_comments', 'true'),
  ('maintenance_mode', 'false'),
  ('announcement_banner', ''),
  ('admin_user_id', 'blade_admin'),
  ('admin_password', 'BladeAdmin@2026')
on conflict (key) do nothing;

-- =============================================================================
-- INDEXES
-- =============================================================================
create index if not exists idx_videos_created_at on public.videos(created_at desc);
create index if not exists idx_videos_user_id on public.videos(user_id);
create index if not exists idx_comments_video_id on public.comments(video_id, created_at desc);
create index if not exists idx_notifications_user_id on public.notifications(user_id, created_at desc);
create index if not exists idx_messages_conversation_id on public.messages(conversation_id, created_at asc);

-- =============================================================================
-- ROW LEVEL SECURITY (RLS)
-- =============================================================================
alter table public.users enable row level security;
alter table public.sounds enable row level security;
alter table public.videos enable row level security;
alter table public.comments enable row level security;
alter table public.likes enable row level security;
alter table public.saved_videos enable row level security;
alter table public.followers enable row level security;
alter table public.hashtags enable row level security;
alter table public.notifications enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.live_streams enable row level security;
alter table public.gifts enable row level security;
alter table public.transactions enable row level security;
alter table public.advertisements enable row level security;
alter table public.reports enable row level security;
alter table public.blocked_users enable row level security;
alter table public.app_settings enable row level security;

-- Public read & RLS policies (idempotent)
drop policy if exists "Public users readable" on public.users;
create policy "Public users readable" on public.users for select using (true);
drop policy if exists "Users insert own profile" on public.users;
create policy "Users insert own profile" on public.users for insert with check (auth.uid() = id);
drop policy if exists "Users update own profile" on public.users;
create policy "Users update own profile" on public.users for update using (auth.uid() = id);

drop policy if exists "Sounds readable" on public.sounds;
create policy "Sounds readable" on public.sounds for select using (true);
drop policy if exists "Authenticated insert sounds" on public.sounds;
create policy "Authenticated insert sounds" on public.sounds for insert with check (auth.role() = 'authenticated');

drop policy if exists "Public videos readable" on public.videos;
create policy "Public videos readable" on public.videos for select using (privacy = 'public' or auth.uid() = user_id);
drop policy if exists "Users insert own videos" on public.videos;
create policy "Users insert own videos" on public.videos for insert with check (auth.uid() = user_id);
drop policy if exists "Users update own videos" on public.videos;
create policy "Users update own videos" on public.videos for update using (auth.uid() = user_id);
drop policy if exists "Users delete own videos" on public.videos;
create policy "Users delete own videos" on public.videos for delete using (auth.uid() = user_id);

drop policy if exists "Comments readable" on public.comments;
create policy "Comments readable" on public.comments for select using (true);
drop policy if exists "Authenticated insert comments" on public.comments;
create policy "Authenticated insert comments" on public.comments for insert with check (auth.uid() = user_id);
drop policy if exists "Users delete own comments" on public.comments;
create policy "Users delete own comments" on public.comments for delete using (auth.uid() = user_id);

drop policy if exists "Likes readable" on public.likes;
create policy "Likes readable" on public.likes for select using (true);
drop policy if exists "Users manage own likes" on public.likes;
create policy "Users manage own likes" on public.likes for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Saved readable by owner" on public.saved_videos;
create policy "Saved readable by owner" on public.saved_videos for select using (auth.uid() = user_id);
drop policy if exists "Users manage own saves" on public.saved_videos;
create policy "Users manage own saves" on public.saved_videos for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Followers readable" on public.followers;
create policy "Followers readable" on public.followers for select using (true);
drop policy if exists "Users manage own follows" on public.followers;
create policy "Users manage own follows" on public.followers for all using (auth.uid() = follower_id) with check (auth.uid() = follower_id);

drop policy if exists "Hashtags readable" on public.hashtags;
create policy "Hashtags readable" on public.hashtags for select using (true);
drop policy if exists "Authenticated upsert hashtags" on public.hashtags;
create policy "Authenticated upsert hashtags" on public.hashtags for all using (auth.role() = 'authenticated') with check (auth.role() = 'authenticated');

drop policy if exists "Notifications readable by recipient" on public.notifications;
create policy "Notifications readable by recipient" on public.notifications for select using (auth.uid() = user_id);
drop policy if exists "Authenticated create notifications" on public.notifications;
create policy "Authenticated create notifications" on public.notifications for insert with check (auth.role() = 'authenticated');
drop policy if exists "Recipient updates notifications" on public.notifications;
create policy "Recipient updates notifications" on public.notifications for update using (auth.uid() = user_id);

drop policy if exists "Conversations readable by participants" on public.conversations;
create policy "Conversations readable by participants" on public.conversations for select using (auth.uid() = participant_one or auth.uid() = participant_two);
drop policy if exists "Conversations insert by participants" on public.conversations;
create policy "Conversations insert by participants" on public.conversations for insert with check (auth.uid() = participant_one or auth.uid() = participant_two);
drop policy if exists "Conversations update by participants" on public.conversations;
create policy "Conversations update by participants" on public.conversations for update using (auth.uid() = participant_one or auth.uid() = participant_two);

drop policy if exists "Messages readable by conversation participants" on public.messages;
create policy "Messages readable by conversation participants" on public.messages for select using (
  exists (select 1 from public.conversations c where c.id = conversation_id and (c.participant_one = auth.uid() or c.participant_two = auth.uid()))
);
drop policy if exists "Messages insert by sender" on public.messages;
create policy "Messages insert by sender" on public.messages for insert with check (
  auth.uid() = sender_id and
  exists (select 1 from public.conversations c where c.id = conversation_id and (c.participant_one = auth.uid() or c.participant_two = auth.uid()))
);
drop policy if exists "Messages update by participants" on public.messages;
create policy "Messages update by participants" on public.messages for update using (
  exists (select 1 from public.conversations c where c.id = conversation_id and (c.participant_one = auth.uid() or c.participant_two = auth.uid()))
);

drop policy if exists "Live streams readable" on public.live_streams;
create policy "Live streams readable" on public.live_streams for select using (true);
drop policy if exists "Gifts readable" on public.gifts;
create policy "Gifts readable" on public.gifts for select using (true);
drop policy if exists "Transactions readable by participant" on public.transactions;
create policy "Transactions readable by participant" on public.transactions for select using (auth.uid() = sender_id or auth.uid() = receiver_id);
drop policy if exists "Transactions insert by sender" on public.transactions;
create policy "Transactions insert by sender" on public.transactions for insert with check (auth.uid() = sender_id);
drop policy if exists "Ads readable" on public.advertisements;
create policy "Ads readable" on public.advertisements for select using (true);

drop policy if exists "Reports insert by authenticated" on public.reports;
create policy "Reports insert by authenticated" on public.reports for insert with check (auth.uid() = reporter_id);
drop policy if exists "Reports readable by reporter or admin" on public.reports;
create policy "Reports readable by reporter or admin" on public.reports for select using (
  auth.uid() = reporter_id or exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'admin')
);
drop policy if exists "Reports update by admin" on public.reports;
create policy "Reports update by admin" on public.reports for update using (
  exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'admin')
);

drop policy if exists "Blocked readable by blocker" on public.blocked_users;
create policy "Blocked readable by blocker" on public.blocked_users for select using (auth.uid() = blocker_id);
drop policy if exists "Blocked managed by blocker" on public.blocked_users;
create policy "Blocked managed by blocker" on public.blocked_users for all using (auth.uid() = blocker_id) with check (auth.uid() = blocker_id);

drop policy if exists "Public settings readable" on public.app_settings;
create policy "Public settings readable" on public.app_settings for select using (key not in ('admin_user_id', 'admin_password'));

-- =============================================================================
-- RPC FUNCTIONS FOR ATOMIC COUNTERS & ENGAGEMENT
-- =============================================================================
create or replace function public.increment_video_views(p_video_id uuid)
returns void language plpgsql security definer as $$
begin
  update public.videos set views_count = views_count + 1 where id = p_video_id;
end;
$$;

create or replace function public.increment_video_shares(p_video_id uuid)
returns void language plpgsql security definer as $$
begin
  update public.videos set shares_count = shares_count + 1 where id = p_video_id;
end;
$$;

create or replace function public.toggle_video_like(p_video_id uuid)
returns boolean language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
  v_exists boolean;
  v_owner uuid;
begin
  if v_uid is null then raise exception 'Unauthorized'; end if;
  select exists(select 1 from public.likes where video_id = p_video_id and user_id = v_uid) into v_exists;
  select user_id into v_owner from public.videos where id = p_video_id;
  if v_exists then
    delete from public.likes where video_id = p_video_id and user_id = v_uid;
    update public.videos set likes_count = greatest(0, likes_count - 1) where id = p_video_id;
    if v_owner is not null then
      update public.users set total_likes = greatest(0, total_likes - 1) where id = v_owner;
    end if;
    return false;
  else
    insert into public.likes (video_id, user_id) values (p_video_id, v_uid);
    update public.videos set likes_count = likes_count + 1 where id = p_video_id;
    if v_owner is not null then
      update public.users set total_likes = total_likes + 1 where id = v_owner;
      if v_owner <> v_uid then
        insert into public.notifications (user_id, actor_id, type, title, body, video_id)
        values (v_owner, v_uid, 'like', 'New Like', 'liked your Blade video', p_video_id);
      end if;
    end if;
    return true;
  end if;
end;
$$;

create or replace function public.toggle_follow_user(p_target_id uuid)
returns boolean language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
  v_exists boolean;
begin
  if v_uid is null or v_uid = p_target_id then raise exception 'Invalid follow request'; end if;
  select exists(select 1 from public.followers where follower_id = v_uid and following_id = p_target_id) into v_exists;
  if v_exists then
    delete from public.followers where follower_id = v_uid and following_id = p_target_id;
    update public.users set following_count = greatest(0, following_count - 1) where id = v_uid;
    update public.users set followers_count = greatest(0, followers_count - 1) where id = p_target_id;
    return false;
  else
    insert into public.followers (follower_id, following_id) values (v_uid, p_target_id);
    update public.users set following_count = following_count + 1 where id = v_uid;
    update public.users set followers_count = followers_count + 1 where id = p_target_id;
    insert into public.notifications (user_id, actor_id, type, title, body)
    values (p_target_id, v_uid, 'follow', 'New Follower', 'started following you');
    return true;
  end if;
end;
$$;

create or replace function public.send_virtual_gift(p_receiver_id uuid, p_video_id uuid, p_coins integer, p_gift_name text)
returns integer language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
  v_balance integer;
begin
  if v_uid is null then raise exception 'Unauthorized'; end if;
  select coins_balance into v_balance from public.users where id = v_uid;
  if v_balance is null or v_balance < p_coins then
    raise exception 'Insufficient Blade Coins';
  end if;
  update public.users set coins_balance = coins_balance - p_coins where id = v_uid;
  update public.users set earnings_usd = earnings_usd + round((p_coins * 0.01)::numeric, 2) where id = p_receiver_id;
  insert into public.transactions (sender_id, receiver_id, video_id, type, coins_amount, amount_usd, description)
  values (v_uid, p_receiver_id, p_video_id, 'gift', p_coins, round((p_coins * 0.01)::numeric, 2), 'Sent ' || p_gift_name);
  if p_receiver_id <> v_uid then
    insert into public.notifications (user_id, actor_id, type, title, body, video_id)
    values (p_receiver_id, v_uid, 'gift', 'Gift Received', 'sent you a ' || p_gift_name || ' (' || p_coins || ' coins)', p_video_id);
  end if;
  return v_balance - p_coins;
end;
$$;

create or replace function public.claim_reward_coins(p_coins integer, p_source text)
returns integer language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
  v_new_balance integer;
begin
  if v_uid is null then raise exception 'Unauthorized'; end if;
  update public.users set coins_balance = coins_balance + p_coins where id = v_uid returning coins_balance into v_new_balance;
  insert into public.transactions (sender_id, type, coins_amount, description)
  values (v_uid, 'reward_ad', p_coins, p_source);
  return v_new_balance;
end;
$$;

-- =============================================================================
-- STORAGE BUCKETS
-- =============================================================================
insert into storage.buckets (id, name, public) values ('videos', 'videos', true) on conflict (id) do nothing;
insert into storage.buckets (id, name, public) values ('avatars', 'avatars', true) on conflict (id) do nothing;
insert into storage.buckets (id, name, public) values ('chat-media', 'chat-media', true) on conflict (id) do nothing;

drop policy if exists "Public read videos storage" on storage.objects;
create policy "Public read videos storage" on storage.objects for select using (bucket_id = 'videos');
drop policy if exists "Authenticated upload videos storage" on storage.objects;
create policy "Authenticated upload videos storage" on storage.objects for insert with check (bucket_id = 'videos');
drop policy if exists "Authenticated update videos storage" on storage.objects;
create policy "Authenticated update videos storage" on storage.objects for update using (bucket_id = 'videos');
drop policy if exists "Delete videos storage" on storage.objects;
create policy "Delete videos storage" on storage.objects for delete using (bucket_id = 'videos');
drop policy if exists "Public read avatars storage" on storage.objects;
create policy "Public read avatars storage" on storage.objects for select using (bucket_id = 'avatars');
drop policy if exists "Authenticated upload avatars storage" on storage.objects;
create policy "Authenticated upload avatars storage" on storage.objects for insert with check (bucket_id = 'avatars' and auth.role() = 'authenticated');
drop policy if exists "Authenticated update avatars storage" on storage.objects;
create policy "Authenticated update avatars storage" on storage.objects for update using (bucket_id = 'avatars' and auth.role() = 'authenticated');
drop policy if exists "Public read chat media storage" on storage.objects;
create policy "Public read chat media storage" on storage.objects for select using (bucket_id = 'chat-media');
drop policy if exists "Authenticated upload chat media storage" on storage.objects;
create policy "Authenticated upload chat media storage" on storage.objects for insert with check (bucket_id = 'chat-media' and auth.role() = 'authenticated');
drop policy if exists "Authenticated update chat media storage" on storage.objects;
create policy "Authenticated update chat media storage" on storage.objects for update using (bucket_id = 'chat-media' and auth.role() = 'authenticated');

-- Default virtual gifts catalog (idempotent)
insert into public.gifts (name, icon_name, coin_cost)
select v.name, v.icon_name, v.coin_cost
from (values
  ('Spark', 'bolt', 10),
  ('Rose', 'favorite', 25),
  ('Bengal Tiger', 'pets', 100),
  ('Blade Crown', 'workspace_premium', 250),
  ('Prism Rocket', 'rocket_launch', 500)
) as v(name, icon_name, coin_cost)
where not exists (select 1 from public.gifts g where g.name = v.name);

-- =============================================================================
-- DISABLE EMAIL CONFIRMATION FOR INSTANT SIGN-UP (Safe auto-confirm trigger)
-- Also toggle OFF "Confirm email" in Supabase -> Authentication -> Providers -> Email
-- =============================================================================
create or replace function public.auto_confirm_new_user()
returns trigger language plpgsql security definer as $$
begin
  new.email_confirmed_at := coalesce(new.email_confirmed_at, now());
  return new;
end;
$$;

do $$
begin
  if not exists (
    select 1 from pg_trigger where tgname = 'trg_auto_confirm_new_user'
  ) then
    create trigger trg_auto_confirm_new_user
    before insert on auth.users
    for each row execute function public.auto_confirm_new_user();
  end if;
exception when insufficient_privilege then
  null;
end $$;

-- =============================================================================
-- ADMIN MANAGEMENT RPC FUNCTIONS (Protected by Admin ID & Password verification)
-- =============================================================================
create or replace function public.admin_verify_credentials(p_admin_id text, p_password text)
returns boolean language plpgsql security definer as $$
declare
  v_expected_id text;
  v_expected_pass text;
begin
  select value into v_expected_id from public.app_settings where key = 'admin_user_id';
  select value into v_expected_pass from public.app_settings where key = 'admin_password';
  v_expected_id := coalesce(nullif(v_expected_id, ''), 'blade_admin');
  v_expected_pass := coalesce(nullif(v_expected_pass, ''), 'BladeAdmin@2026');
  return p_admin_id = v_expected_id and p_password = v_expected_pass;
end;
$$;

create or replace function public.admin_verify_gate_password(p_password text)
returns boolean language plpgsql security definer as $$
declare
  v_expected_pass text;
begin
  select value into v_expected_pass from public.app_settings where key = 'admin_password';
  v_expected_pass := coalesce(nullif(v_expected_pass, ''), 'BladeAdmin@2026');
  return p_password = v_expected_pass;
end;
$$;

create or replace function public.admin_update_credentials(
  p_current_id text,
  p_current_password text,
  p_new_admin_id text,
  p_new_password text
)
returns boolean language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_current_id, p_current_password) then
    raise exception 'Invalid current admin credentials';
  end if;
  insert into public.app_settings (key, value, updated_at)
  values ('admin_user_id', p_new_admin_id, now()), ('admin_password', p_new_password, now())
  on conflict (key) do update set value = excluded.value, updated_at = now();
  return true;
end;
$$;

create or replace function public.request_user_verification()
returns void language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'Unauthorized'; end if;
  update public.users
  set verification_status = 'pending',
      is_verified = false
  where id = v_uid;
end;
$$;

create or replace function public.admin_update_user(
  p_admin_id text,
  p_admin_password text,
  p_user_id uuid,
  p_display_name text default null,
  p_username text default null,
  p_bio text default null,
  p_role text default null,
  p_coins_balance integer default null,
  p_is_verified boolean default null,
  p_is_blocked boolean default null,
  p_verification_status text default null
)
returns void language plpgsql security definer as $$
declare
  v_next_status text;
  v_next_verified boolean;
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  v_next_status := case
    when p_verification_status is not null then p_verification_status
    when p_is_verified = true then 'verified'
    when p_is_verified = false then 'rejected'
    else null
  end;
  v_next_verified := case
    when p_is_verified is not null then p_is_verified
    when p_verification_status = 'verified' then true
    when p_verification_status in ('pending', 'rejected', 'none') then false
    else null
  end;
  update public.users set
    display_name = coalesce(p_display_name, display_name),
    username = coalesce(p_username, username),
    bio = coalesce(p_bio, bio),
    role = coalesce(p_role, role),
    coins_balance = coalesce(p_coins_balance, coins_balance),
    is_verified = coalesce(v_next_verified, is_verified),
    verification_status = coalesce(v_next_status, verification_status),
    is_blocked = coalesce(p_is_blocked, is_blocked)
  where id = p_user_id;
  if not found then
    raise exception 'User not found';
  end if;
end;
$$;

create or replace function public.admin_delete_user(
  p_admin_id text,
  p_admin_password text,
  p_user_id uuid
)
returns void language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  delete from public.users where id = p_user_id;
end;
$$;

create or replace function public.admin_create_video(
  p_admin_id text,
  p_admin_password text,
  p_user_id uuid,
  p_video_url text,
  p_thumbnail_url text default '',
  p_title text default '',
  p_caption text default '',
  p_hashtags text[] default '{}',
  p_category text default 'All',
  p_location text default '',
  p_privacy text default 'public',
  p_sound_id uuid default null,
  p_sound_title text default 'Original Sound',
  p_duration_ms bigint default 0
)
returns setof public.videos language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  return query
  insert into public.videos (
    user_id, video_url, thumbnail_url, title, caption, hashtags, category,
    location, privacy, sound_id, sound_title, duration_ms
  )
  values (
    p_user_id, p_video_url, coalesce(p_thumbnail_url, ''), coalesce(p_title, ''),
    coalesce(p_caption, ''), coalesce(p_hashtags, '{}'), coalesce(p_category, 'All'),
    coalesce(p_location, ''), coalesce(p_privacy, 'public'), p_sound_id,
    coalesce(p_sound_title, 'Original Sound'), coalesce(p_duration_ms, 0)
  )
  returning *;
end;
$$;

create or replace function public.admin_update_video(
  p_admin_id text,
  p_admin_password text,
  p_video_id uuid,
  p_title text default null,
  p_caption text default null,
  p_category text default null,
  p_privacy text default null,
  p_views_count integer default null,
  p_likes_count integer default null,
  p_shares_count integer default null,
  p_saves_count integer default null,
  p_is_sponsored boolean default null,
  p_sponsor_label text default null
)
returns void language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  update public.videos set
    title = coalesce(p_title, title),
    caption = coalesce(p_caption, caption),
    category = coalesce(p_category, category),
    privacy = coalesce(p_privacy, privacy),
    views_count = greatest(0, coalesce(p_views_count, views_count)),
    likes_count = greatest(0, coalesce(p_likes_count, likes_count)),
    shares_count = greatest(0, coalesce(p_shares_count, shares_count)),
    saves_count = greatest(0, coalesce(p_saves_count, saves_count)),
    is_sponsored = coalesce(p_is_sponsored, is_sponsored),
    sponsor_label = coalesce(p_sponsor_label, sponsor_label)
  where id = p_video_id;
end;
$$;

create or replace function public.admin_delete_video(
  p_admin_id text,
  p_admin_password text,
  p_video_id uuid
)
returns void language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  delete from public.videos where id = p_video_id;
  if not found then
    raise exception 'Video not found';
  end if;
end;
$$;

create or replace function public.admin_delete_comment(
  p_admin_id text,
  p_admin_password text,
  p_comment_id uuid
)
returns void language plpgsql security definer as $$
declare
  v_vid uuid;
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  select video_id into v_vid from public.comments where id = p_comment_id;
  delete from public.comments where id = p_comment_id;
  if v_vid is not null then
    update public.videos set comments_count = greatest(0, comments_count - 1) where id = v_vid;
  end if;
end;
$$;

create or replace function public.admin_resolve_report(
  p_admin_id text,
  p_admin_password text,
  p_report_id uuid,
  p_status text
)
returns void language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  update public.reports set status = p_status where id = p_report_id;
end;
$$;

create or replace function public.admin_delete_report(
  p_admin_id text,
  p_admin_password text,
  p_report_id uuid
)
returns void language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  delete from public.reports where id = p_report_id;
end;
$$;

create or replace function public.admin_upsert_app_setting(
  p_admin_id text,
  p_admin_password text,
  p_key text,
  p_value text
)
returns void language plpgsql security definer as $$
begin
  if not public.admin_verify_credentials(p_admin_id, p_admin_password) then
    raise exception 'Unauthorized admin action';
  end if;
  insert into public.app_settings (key, value, updated_at)
  values (p_key, p_value, now())
  on conflict (key) do update set value = excluded.value, updated_at = now();
end;
$$;

create or replace function public.mark_conversation_delivered(p_conversation_id uuid)
returns void language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then return; end if;
  update public.messages
  set status = 'delivered',
      delivered_at = coalesce(delivered_at, now())
  where conversation_id = p_conversation_id
    and sender_id <> v_uid
    and status = 'sent';
end;
$$;

create or replace function public.mark_inbox_delivered()
returns void language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then return; end if;
  update public.messages m
  set status = 'delivered',
      delivered_at = coalesce(m.delivered_at, now())
  from public.conversations c
  where m.conversation_id = c.id
    and (c.participant_one = v_uid or c.participant_two = v_uid)
    and m.sender_id <> v_uid
    and m.status = 'sent';
end;
$$;

create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void language plpgsql security definer as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then return; end if;
  update public.messages
  set is_read = true,
      status = 'read',
      delivered_at = coalesce(delivered_at, now()),
      read_at = coalesce(read_at, now())
  where conversation_id = p_conversation_id
    and sender_id <> v_uid
    and (is_read = false or status <> 'read');
end;
$$;

do $$
begin
  alter publication supabase_realtime add table public.messages;
exception when duplicate_object or undefined_object then
  null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.conversations;
exception when duplicate_object or undefined_object then
  null;
end $$;

-- Immediately reload PostgREST schema cache so REST endpoints recognize new columns & RPCs
notify pgrst, 'reload schema';


