# KatMusic Group Blend

Group Blend is a Spotify-independent taste-matching feature built from
KatMusic's own local listening statistics.

## How it works

1. KatMusic reads the local `song_play_stats` table.
2. `BlendTasteProfileBuilder` converts play time, completed plays, skips,
   and recency into normalized artist and track affinities.
3. Each person copies their own compact `KATBLEND1:` share code and sends it
   to a friend.
4. The friend pastes that code into Group Blend. Up to 9 other profiles can be
   added (10 people total); imported profiles are saved locally between app launches.
5. `GroupBlendEngine` calculates pairwise taste compatibility, shared
   artists, tracks shared by everyone, and group picks.

## Privacy and limitations

- No Spotify account, Spotify API, Last.fm login, or YouTube Music login is
  required for the matching algorithm.
- Full listening history remains on the device.
- The share code contains a compact snapshot of up to 40 artists and 100
  tracks, including track titles and artist names. Only share it with people
  you trust.
- The Android UI now has both paths: manual profile-code exchange with locally saved members, and optional cloud Blends through Supabase Auth, profile sync, create/join/leave and member retrieval. Cloud access is only active when `KATMUSIC_BLEND_SUPABASE_URL` and `KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY` are configured; the cloud flows are implemented but have not yet been verified against a live project in this environment.
- Recommendations show track metadata and group affinity. Tracks with a saved
  YouTube Music ID can be played directly; queue-wide radio continuation remains
  a separate integration.

## Scoring

Compatibility is the average pairwise cosine similarity between each pair of
members' normalized artist and track vectors (75% artist, 25% track). Group
recommendations reward both average affinity and the proportion of members
whose profiles contain a track. This is KatMusic's own transparent heuristic,
not a reproduction of Spotify's proprietary Blend algorithm.

## Tests

Run the normal Android unit test task:

```bash
./gradlew :app:testDebugUnitTest
```


## Dedicated Supabase project setup

Group Blend has its own Supabase project, separate from Listen Together. Follow [the Blend database setup guide](../server/blend/README.md) and run `server/blend/supabase/migrations/0001_blend_schema.sql` in that project. Local builds read `KATMUSIC_BLEND_SUPABASE_URL` and `KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY` from the ignored root `.env` or `local.properties`; CI reads the same names from GitHub Actions repository secrets.

The URL and publishable/anon key are public client configuration, not privileged secrets. Never place the Supabase service-role/secret key in the Android app. Database access must rely on Supabase Auth and RLS.
