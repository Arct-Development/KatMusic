-- KatMusic Jam: isolated Supabase project schema.
-- Run this only in the dedicated Jam project. Never use the Blend project here.
-- The Go server talks to this table with the service-role key; the Android app
-- never receives that key and does not query this table directly.

create table if not exists public.jam_rooms (
  code text primary key check (code ~ '^[A-Z0-9]{4,8}$'),
  mode text not null default 'host_only' check (mode in ('host_only', 'collaborative')),
  name text not null default '',
  permanent boolean not null default false,
  radio boolean not null default false,
  radio_key text not null default '',
  playback jsonb not null default '{}'::jsonb,
  chat jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '6 hours'
);

create index if not exists jam_rooms_expires_at_idx on public.jam_rooms (expires_at);

-- Only the server (service role bypasses RLS) may read or write room state.
alter table public.jam_rooms enable row level security;
revoke all on table public.jam_rooms from anon, authenticated;

-- Optional housekeeping; the Go server also deletes expired rows on startup.
-- select cron.schedule('jam-expire-rooms', '17 * * * *',
--   $$delete from public.jam_rooms where expires_at < now()$$);
