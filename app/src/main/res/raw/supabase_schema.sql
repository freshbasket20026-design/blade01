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
  is_private boolean not null default false,
  role text not null default 'user' check (role in ('user', 'creator', 'admin')),
  followers_count integer not null default 0,
  following_count integer not null default 0,
  total_likes integer not null default 0,
  coins_balance integer not null default 100,
  earnings_usd numeric(10, 2) not null default 0.00,
  created_at timestamptz not null default now()
);

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
  content text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

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
create policy "Messages insert by sender" on public.messages for insert with check (auth.uid() = sender_id);

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

drop policy if exists "Public read videos storage" on storage.objects;
create policy "Public read videos storage" on storage.objects for select using (bucket_id = 'videos');
drop policy if exists "Authenticated upload videos storage" on storage.objects;
create policy "Authenticated upload videos storage" on storage.objects for insert with check (bucket_id = 'videos' and auth.role() = 'authenticated');
drop policy if exists "Authenticated update videos storage" on storage.objects;
create policy "Authenticated update videos storage" on storage.objects for update using (bucket_id = 'videos' and auth.role() = 'authenticated');
drop policy if exists "Public read avatars storage" on storage.objects;
create policy "Public read avatars storage" on storage.objects for select using (bucket_id = 'avatars');
drop policy if exists "Authenticated upload avatars storage" on storage.objects;
create policy "Authenticated upload avatars storage" on storage.objects for insert with check (bucket_id = 'avatars' and auth.role() = 'authenticated');
drop policy if exists "Authenticated update avatars storage" on storage.objects;
create policy "Authenticated update avatars storage" on storage.objects for update using (bucket_id = 'avatars' and auth.role() = 'authenticated');

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

-- Immediately reload PostgREST schema cache so REST endpoints recognize new tables
notify pgrst, 'reload schema';
