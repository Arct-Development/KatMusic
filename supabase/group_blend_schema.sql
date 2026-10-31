-- KatMusic Group Blend: Supabase SQL setup
-- Run this once in Supabase SQL Editor.
-- Requires Supabase Auth. Anonymous sign-in can be enabled for guest-friendly use.
-- The app should upload only BlendTasteProfile snapshots, never raw play history.

create table if not exists public.blend_groups (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 1 and 48),
    invite_code text not null unique,
    owner_id uuid not null references auth.users(id) on delete cascade,
    max_members smallint not null default 10 check (max_members between 2 and 10),
    created_at timestamptz not null default now()
);

create table if not exists public.blend_members (
    group_id uuid not null references public.blend_groups(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    display_name text not null check (char_length(display_name) between 1 and 32),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create table if not exists public.blend_profiles (
    group_id uuid not null,
    user_id uuid not null,
    profile jsonb not null default '{}'::jsonb
        check (octet_length(profile::text) <= 32768),
    updated_at timestamptz not null default now(),
    primary key (group_id, user_id),
    foreign key (group_id, user_id)
        references public.blend_members(group_id, user_id) on delete cascade
);

create index if not exists blend_members_user_id_idx
    on public.blend_members(user_id);
create index if not exists blend_profiles_group_id_idx
    on public.blend_profiles(group_id);

alter table public.blend_groups enable row level security;
alter table public.blend_members enable row level security;
alter table public.blend_profiles enable row level security;

-- Security-definer helper avoids recursive RLS when a policy needs to
-- check membership in blend_members itself.
create or replace function public.is_blend_member(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.blend_members m
        where m.group_id = p_group_id and m.user_id = (select auth.uid())
    );
$$;

revoke all on function public.is_blend_member(uuid) from public;
grant execute on function public.is_blend_member(uuid) to authenticated;

-- A member can read their groups and the members/profiles in those groups.
create policy "members read own groups"
on public.blend_groups for select to authenticated
using (public.is_blend_member(id));

create policy "members read group members"
on public.blend_members for select to authenticated
using (public.is_blend_member(blend_members.group_id));

create policy "members read group profiles"
on public.blend_profiles for select to authenticated
using (public.is_blend_member(blend_profiles.group_id));

create policy "members insert own profile"
on public.blend_profiles for insert to authenticated
with check (
    user_id = (select auth.uid())
    and public.is_blend_member(blend_profiles.group_id)
);

create policy "members update own profile"
on public.blend_profiles for update to authenticated
using (
    user_id = (select auth.uid())
    and public.is_blend_member(blend_profiles.group_id)
)
with check (
    user_id = (select auth.uid())
    and public.is_blend_member(blend_profiles.group_id)
);

create policy "members leave own groups"
on public.blend_members for delete to authenticated
using (user_id = (select auth.uid()));

-- Group creation and joining happen through security-definer RPCs so clients
-- cannot bypass invite-code checks or forge another member's user ID.
create or replace function public.create_blend_group(
    p_name text,
    p_display_name text,
    p_max_members smallint default 10
)
returns table(group_id uuid, invite_code text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_group_id uuid;
    v_code text;
    v_max smallint := least(greatest(coalesce(p_max_members, 10), 2), 10);
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if length(trim(coalesce(p_name, ''))) not between 1 and 48 then
        raise exception 'Group name must be 1-48 characters';
    end if;
    if length(trim(coalesce(p_display_name, ''))) not between 1 and 32 then
        raise exception 'Display name must be 1-32 characters';
    end if;

    loop
        v_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
        exit when not exists (
            select 1 from public.blend_groups g where g.invite_code = v_code
        );
    end loop;

    insert into public.blend_groups(name, invite_code, owner_id, max_members)
    values (trim(p_name), v_code, auth.uid(), v_max)
    returning id into v_group_id;

    insert into public.blend_members(group_id, user_id, display_name)
    values (v_group_id, auth.uid(), trim(p_display_name));

    return query select v_group_id, v_code;
end;
$$;

create or replace function public.join_blend_group(
    p_invite_code text,
    p_display_name text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_group public.blend_groups%rowtype;
    v_member_count integer;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if length(trim(coalesce(p_display_name, ''))) not between 1 and 32 then
        raise exception 'Display name must be 1-32 characters';
    end if;

    select * into v_group
    from public.blend_groups g
    where g.invite_code = upper(trim(coalesce(p_invite_code, '')))
    for update;

    if not found then
        raise exception 'Invalid invite code';
    end if;

    select count(*) into v_member_count
    from public.blend_members m where m.group_id = v_group.id;

    if not exists (
        select 1 from public.blend_members m
        where m.group_id = v_group.id and m.user_id = auth.uid()
    ) and v_member_count >= v_group.max_members then
        raise exception 'This Blend group is full';
    end if;

    insert into public.blend_members(group_id, user_id, display_name)
    values (v_group.id, auth.uid(), trim(p_display_name))
    on conflict (group_id, user_id)
    do update set display_name = excluded.display_name;

    return v_group.id;
end;
$$;

revoke all on function public.create_blend_group(text, text, smallint) from public;
revoke all on function public.join_blend_group(text, text) from public;
grant execute on function public.create_blend_group(text, text, smallint) to authenticated;
grant execute on function public.join_blend_group(text, text) to authenticated;

-- No direct group inserts/updates are granted. Membership is managed by RPCs
-- and self-removal; profile rows are scoped by RLS to the authenticated user.
grant select on public.blend_groups, public.blend_members, public.blend_profiles to authenticated;
grant insert, update on public.blend_profiles to authenticated;
grant delete on public.blend_members to authenticated;
