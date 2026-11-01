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
- The current Android UI still exchanges profiles manually using copy/paste and stores imported profiles locally. A separate Supabase project schema and build-time client configuration have now been added under `server/blend/`, but the Android Supabase Auth/profile-sync/room repository is not connected yet. Do not treat cloud membership or automatic sync as active until that client integration ships.
- Recommendations show track metadata and group affinity. Connecting those
  recommendations to the existing playback queue is a separate integration.

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

Group Blend has its own Supabase project, separate from Listen Together. Follow [the Blend database setup guide](../server/jam/README.md#separate-group-blend-supabase-project) and run `server/blend/supabase/migrations/0001_blend_schema.sql` in that project. Local builds read `KATMUSIC_BLEND_SUPABASE_URL` and `KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY` from the ignored root `.env` or `local.properties`; CI reads the same names from GitHub Actions repository secrets.

The URL and publishable/anon key are public client configuration, not privileged secrets. Never place the Supabase service-role/secret key in the Android app. Database access must rely on Supabase Auth and RLS.
