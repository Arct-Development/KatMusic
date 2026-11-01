-- KatMusic Group Blend: isolated Supabase project schema.
-- Run this only in the dedicated Blend Supabase project's SQL Editor.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.blend_rooms (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 1 and 60),
    owner_id uuid not null references auth.users(id) on delete cascade,
    invite_code_hash text not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint blend_rooms_invite_hash_length check (char_length(invite_code_hash) = 64)
);

create table if not exists public.blend_members (
    blend_id uuid not null references public.blend_rooms(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    display_name text not null check (char_length(display_name) between 1 and 32),
    joined_at timestamptz not null default now(),
    primary key (blend_id, user_id)
);

create index if not exists blend_members_user_id_idx on public.blend_members(user_id);
create index if not exists blend_rooms_owner_id_idx on public.blend_rooms(owner_id);

create table if not exists public.blend_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    schema_version integer not null check (schema_version > 0),
    profile jsonb not null,
    updated_at timestamptz not null default now(),
    constraint blend_profile_size_limit check (octet_length(profile::text) <= 65536)
);

alter table public.blend_rooms enable row level security;
alter table public.blend_members enable row level security;
alter table public.blend_profiles enable row level security;

-- SECURITY DEFINER helpers avoid recursive RLS evaluation on blend_members.
create or replace function public.is_blend_member(p_blend_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $
    select exists (
        select 1 from public.blend_members
        where blend_id = p_blend_id and user_id = (select auth.uid())
    );
$;

create or replace function public.shares_blend_with(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $
    select exists (
        select 1
        from public.blend_members mine
        join public.blend_members theirs on theirs.blend_id = mine.blend_id
        where mine.user_id = (select auth.uid())
          and theirs.user_id = p_user_id
    );
$;

revoke all on function public.is_blend_member(uuid) from public, anon;
revoke all on function public.shares_blend_with(uuid) from public, anon;
grant execute on function public.is_blend_member(uuid) to authenticated;
grant execute on function public.shares_blend_with(uuid) to authenticated;

drop policy if exists "members can read their blend rooms" on public.blend_rooms;
create policy "members can read their blend rooms"
on public.blend_rooms for select to authenticated
using (public.is_blend_member(id));

drop policy if exists "owners can update their blend rooms" on public.blend_rooms;
create policy "owners can update their blend rooms"
on public.blend_rooms for update to authenticated
using (owner_id = (select auth.uid()))
with check (owner_id = (select auth.uid()));

drop policy if exists "owners can delete their blend rooms" on public.blend_rooms;
create policy "owners can delete their blend rooms"
on public.blend_rooms for delete to authenticated
using (owner_id = (select auth.uid()));

drop policy if exists "members can read blend membership" on public.blend_members;
create policy "members can read blend membership"
on public.blend_members for select to authenticated
using (public.is_blend_member(blend_id));

drop policy if exists "users can read their own blend profile" on public.blend_profiles;
create policy "users can read their own blend profile"
on public.blend_profiles for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists "users can read profiles of blend members" on public.blend_profiles;
create policy "users can read profiles of blend members"
on public.blend_profiles for select to authenticated
using (public.shares_blend_with(user_id));

drop policy if exists "users can insert their own blend profile" on public.blend_profiles;
create policy "users can insert their own blend profile"
on public.blend_profiles for insert to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists "users can update their own blend profile" on public.blend_profiles;
create policy "users can update their own blend profile"
on public.blend_profiles for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists "users can delete their own blend profile" on public.blend_profiles;
create policy "users can delete their own blend profile"
on public.blend_profiles for delete to authenticated
using (user_id = (select auth.uid()));

-- Clients cannot forge membership rows or access invite hashes directly.
revoke all on public.blend_rooms from anon, authenticated;
grant select, update, delete on public.blend_rooms to authenticated;
revoke all on public.blend_members from anon, authenticated;
grant select on public.blend_members to authenticated;
revoke all on public.blend_profiles from anon, authenticated;
grant select, insert, update, delete on public.blend_profiles to authenticated;

create or replace function public.create_blend(p_name text, p_invite_code text, p_display_name text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_blend_id uuid;
begin
    if v_user_id is null then raise exception 'Authentication required'; end if;
    if p_name is null or char_length(trim(p_name)) not between 1 and 60 then raise exception 'Invalid Blend name'; end if;
    if p_display_name is null or char_length(trim(p_display_name)) not between 1 and 32 then raise exception 'Invalid display name'; end if;
    if p_invite_code is null or p_invite_code !~ '^[A-Za-z0-9]{8,32}$' then raise exception 'Invalid invite code'; end if;

    insert into public.blend_rooms(name, owner_id, invite_code_hash)
    values (trim(p_name), v_user_id, encode(extensions.digest(p_invite_code, 'sha256'), 'hex'))
    returning id into v_blend_id;

    insert into public.blend_members(blend_id, user_id, display_name)
    values (v_blend_id, v_user_id, trim(p_display_name));

    return v_blend_id;
end;
$$;

create or replace function public.join_blend(p_invite_code text, p_display_name text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_blend_id uuid;
    v_member_count integer;
begin
    if v_user_id is null then raise exception 'Authentication required'; end if;
    if p_display_name is null or char_length(trim(p_display_name)) not between 1 and 32 then raise exception 'Invalid display name'; end if;
    if p_invite_code is null or p_invite_code !~ '^[A-Za-z0-9]{8,32}$' then raise exception 'Invalid invite code'; end if;

    select id into v_blend_id
    from public.blend_rooms
    where invite_code_hash = encode(extensions.digest(p_invite_code, 'sha256'), 'hex')
    for update;

    if v_blend_id is null then raise exception 'Blend invite not found'; end if;
    if exists (select 1 from public.blend_members where blend_id = v_blend_id and user_id = v_user_id) then
        return v_blend_id;
    end if;

    select count(*) into v_member_count from public.blend_members where blend_id = v_blend_id;
    if v_member_count >= 10 then raise exception 'Blend is full'; end if;

    insert into public.blend_members(blend_id, user_id, display_name)
    values (v_blend_id, v_user_id, trim(p_display_name));
    return v_blend_id;
end;
$$;

create or replace function public.leave_blend(p_blend_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $
declare
    v_user_id uuid := auth.uid();
    v_owner_id uuid;
begin
    if v_user_id is null then raise exception 'Authentication required'; end if;
    select owner_id into v_owner_id from public.blend_rooms where id = p_blend_id for update;
    if v_owner_id is null then return; end if;
    if v_owner_id = v_user_id then
        delete from public.blend_rooms where id = p_blend_id;
    else
        delete from public.blend_members where blend_id = p_blend_id and user_id = v_user_id;
    end if;
end;
$;

revoke all on function public.leave_blend(uuid) from public, anon;
grant execute on function public.leave_blend(uuid) to authenticated;

revoke all on function public.create_blend(text, text, text) from public, anon;
revoke all on function public.join_blend(text, text) from public, anon;
grant execute on function public.create_blend(text, text, text) to authenticated;
grant execute on function public.join_blend(text, text) to authenticated;

