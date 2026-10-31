-- KatMusic Jam persistence schema
-- Run this in Supabase SQL Editor using the project owner.

create table if not exists public.jam_rooms (
  code text primary key check (code ~ '^[A-Z0-9]{6}$'),
  host_member_id text not null,
  playback jsonb,
  queue jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null
);

create table if not exists public.jam_members (
  id text primary key,
  room_code text not null references public.jam_rooms(code) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 32),
  session_token_hash text not null unique,
  joined_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create index if not exists jam_members_room_code_idx on public.jam_members(room_code);
create index if not exists jam_rooms_expires_at_idx on public.jam_rooms(expires_at);

create table if not exists public.jam_chat (
  id bigint generated always as identity primary key,
  room_code text not null references public.jam_rooms(code) on delete cascade,
  member_id text not null,
  display_name text not null,
  message text not null check (char_length(message) between 1 and 500),
  created_at timestamptz not null default now()
);

create index if not exists jam_chat_room_id_desc_idx on public.jam_chat(room_code, id desc);

alter table public.jam_rooms enable row level security;
alter table public.jam_members enable row level security;
alter table public.jam_chat enable row level security;

-- No client-side policies: only the server's service-role key accesses these tables.
revoke all on table public.jam_rooms from anon, authenticated;
revoke all on table public.jam_members from anon, authenticated;
revoke all on table public.jam_chat from anon, authenticated;
