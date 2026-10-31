# KatMusic Group Blend MVP

Group Blend is independent of Spotify. It uses KatMusic's local play statistics
to build a compact taste profile, then combines member profiles on-device.

## Current MVP foundation

- `BlendTasteProfileBuilder` converts local `SongPlayStatsEntity` rows into
  normalized artist and track affinities.
- `GroupBlendEngine` calculates pairwise compatibility, shared artists,
  shared tracks, and a balanced group recommendation list.
- `supabase/group_blend_schema.sql` creates the cloud tables, row-level
  security policies, and invite-code RPCs for groups of 2-10 members.
- Full listening history remains on each device. The profile is capped at
  40 artists and 100 tracks; each profile row is capped at 32 KB.

## Supabase Free setup

1. Create a Supabase project on the Free plan.
2. In Authentication settings, enable anonymous sign-in if you want users to
   join without creating an email/password account.
3. Run `supabase/group_blend_schema.sql` in the Supabase SQL Editor.
4. Add the Supabase project URL and publishable/anon key to the app's local
   development configuration before adding the Android repository/client.
5. Never place a service-role key in the Android app.

The Android app still needs a Supabase Auth session before calling the RPCs.
Do not use a Last.fm username, YouTube account name, or invite code as a
substitute for an authenticated user ID.

## Capacity-conscious design

- Upload a compact profile snapshot, not play-by-play events.
- Refresh only after a meaningful profile change or an explicit Blend refresh.
- Do not keep Realtime subscriptions open while a group screen is not visible.
- Use normal reads for group details; reserve Realtime for active group screens.
- Remove profile rows when a member leaves; deleting a member cascades to their
  profile for that group.

## Not wired into navigation yet

This commit adds the reusable matching engine and secure database contract.
The live create/join UI and Supabase Android client require project credentials
and an app-level Auth flow, so they should be integrated after the owner creates
the Supabase project and chooses anonymous or account-based sign-in.
