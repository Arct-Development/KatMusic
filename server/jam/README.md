# KatMusic Listen Together server

Node.js WebSocket room relay with Supabase persistence. It relays playback metadata and position only; every listener resolves audio independently through their own authorized provider. It does not relay audio or stream URLs.

## Deploy on Render

- Root Directory: \`server/jam\`
- Runtime: Docker
- Dockerfile: \`Dockerfile\`
- Health Check Path: \`/healthz\`
- Add the environment variables below before deploying.
- Keep exactly one Render instance for now. Supabase persists data, but this server's live WebSocket connections and broadcasts are process-local. Multiple instances need a shared pub/sub layer (for example, Supabase Realtime or Redis) before they can safely host the same rooms.

## Supabase setup

1. Create a Supabase project.
2. Open **SQL Editor** and run \`supabase/migrations/0001_jam_persistence.sql\`.
3. In Render, set:
   - \`SUPABASE_URL\`: Project URL from Supabase project settings.
   - \`SUPABASE_SERVICE_ROLE_KEY\`: service-role key from Supabase API settings. Treat it as a secret.
   - \`REQUIRE_SUPABASE=true\`: makes the server refuse to start if Supabase is not configured.
   - \`MAX_ROOM_MEMBERS=20\`: optional, maximum 50.
   - \`ALLOWED_ORIGINS\`: optional comma-separated browser origins. Native Android clients normally send no Origin header; leave empty unless you know the app's browser origin.
4. Never put the service-role key in the Android app, source control, build config, or public client settings. Rotate it immediately if exposed.

The server uses the service-role key only on Render. RLS is enabled and there are no anon/authenticated table policies.

## Storage model

- \`jam_rooms\`: room code, host, last playback snapshot, queue, expiry.
- \`jam_members\`: member identity, display name, SHA-256 hash of reconnect token, timestamps.
- \`jam_chat\`: recent chat messages (server retains the latest 50 per room).

Room codes and membership survive a Render restart. On reconnect, the client receives the restored room snapshot and recent chat history in the existing \`room_reconnected\` / \`room_joined\` message as an additional \`chatHistory\` field. Older clients can ignore that field.

Playback updates are broadcast immediately and persisted as a recovery snapshot. For high-frequency playback events, database writes are best-effort and do not block the live broadcast. Queue and chat writes are persisted before the server confirms/broadcasts them.

Disconnected member sessions are retained for up to six hours. Rooms expire after 24 hours without activity. A server restart restores rooms and members, but clients must reconnect using their session token.

## Local development

    cd server/jam
    npm install
    SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... npm start

Without Supabase credentials, local development uses temporary in-memory storage. The health endpoint reports this as \`persistence: "memory"\`. In production, set \`REQUIRE_SUPABASE=true\`.

- Health: \`http://localhost:8787/healthz\`
- WebSocket: \`ws://localhost:8787/ws\`

## Existing protocol

- \`create_room\`, \`join_room\`, \`approve_member\`, \`reconnect\`
- \`playback_state\`, \`queue_suggestion\`, \`queue_update\`
- \`chat\`, \`transfer_host\`, \`kick_member\`, \`leave_room\`, \`ping\`

Join requests still require explicit host approval. Host-only actions are checked server-side. Reconnect tokens are random, room-scoped, and stored only as SHA-256 hashes.

## Operational notes

- Render terminates public TLS; use \`wss://YOUR-SERVICE.onrender.com/ws\` from the app.
- Configure UptimeRobot to check \`https://YOUR-SERVICE.onrender.com/healthz\`.
- A successful HTTP health check does not test a complete WebSocket session.
- Supabase Free has project and usage limits; monitor database size and egress.
- Supabase persistence is not a distributed WebSocket broker. Keep one server instance until shared pub/sub and cross-instance membership coordination are implemented.


## Separate Group Blend Supabase project

Group Blend uses a **separate Supabase project** from Listen Together. Do not point both features at the same project. Jam's server-side service-role key and database remain isolated from Blend's user-facing data.

### 1. Create the Blend project

1. Create a new project in the Supabase dashboard, for example `KatMusic Blend`.
2. Choose a region close to most of your users.
3. Save the database password in your password manager. It is not needed in the Android app.
4. Open **Project Settings → API** (or **API Keys**) and copy:
   - Project URL
   - Publishable key (preferred), or the legacy anon key if your project still uses it.
5. Do **not** copy the secret key or service-role key into Android, Gradle, GitHub Actions build variables, or this repository.

### 2. Create the database schema

1. Open the new Blend project → **SQL Editor**.
2. Create a new query.
3. Paste and run `server/blend/supabase/migrations/0001_blend_schema.sql`.
4. In **Table Editor**, confirm these tables exist:
   - `blend_rooms`: Blend name, owner, invite-code hash and timestamps.
   - `blend_members`: user membership and display name.
   - `blend_profiles`: compact taste profile and schema version.
5. In **Authentication → Providers**, enable the sign-in method KatMusic will use. Blend membership is tied to an authenticated Supabase user; a display name or invite code is not an identity.

The migration enables RLS, grants only the required client operations, and creates authenticated RPCs for creating and joining a Blend. Invite codes are stored as SHA-256 hashes. The profile payload is capped at 64 KiB. Membership is limited to 10 people per Blend.

### 3. Configure local Android builds

The root Gradle build reads the root `.env` file (ignored by Git) and `local.properties`. Add these values to your local root `.env`:

```dotenv
KATMUSIC_BLEND_SUPABASE_URL=https://YOUR-BLEND-PROJECT.supabase.co
KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
```

Alternatively, add the same property names to root `local.properties`. Do not commit either file. The checked-in `.env.example` is a template only.

### 4. Configure GitHub Actions

In GitHub → **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret name | Value |
|---|---|
| `KATMUSIC_BLEND_SUPABASE_URL` | Blend project's Project URL |
| `KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY` | Blend project's publishable key (or legacy anon key) |

The Android workflows pass these values to Gradle at build time. They are client configuration, not privileged credentials: because the Android app must connect directly to Supabase, the values can be extracted from an APK. RLS and authenticated access are the security boundary.

### 5. Key handling and security

- Never create a `KATMUSIC_BLEND_SUPABASE_SERVICE_ROLE_KEY` build variable. The service-role/secret key bypasses RLS and must stay server-side.
- Never use a Supabase secret key in the Android app, `BuildConfig`, `.env.example`, or GitHub Actions jobs that build the APK.
- Keep RLS enabled. Do not add broad `anon` or `authenticated` policies such as `using (true)`.
- Do not upload raw `song_play_stats` or full listening history. Upload only the compact taste profile.
- Users should be able to remove their own profile and leave Blends; deleting an auth user cascades their profile and memberships.
- Rotate any privileged key immediately if it is exposed.

### 6. Separation and free-tier usage

The Blend project is independent from the Jam project. Its database size, storage, egress, auth usage and project limits are counted separately. Monitor usage in each Supabase dashboard. Splitting projects isolates quotas; it does not make either project's free tier unlimited.

### Android integration status

The Android app now includes a Supabase Auth client, session persistence/refresh, compact profile upsert, cloud Blend create/join, member/profile retrieval, and leave-group support. The Group Blend screen exposes account sign-in/create, online group creation/join, invitation-code sharing, member listing, refresh, and leave actions. Local profile-code import remains available as a fallback.

Cloud operation requires the dedicated project migration to be applied and the two client configuration values to be supplied at build time. If those values are absent, the screen reports that cloud setup is unavailable and the offline fallback remains usable. Email confirmation behavior follows the Supabase project's Auth settings.

**Build and live backend verification are still required.** A successful Android build does not prove that the SQL was applied or that the Supabase project has the expected Auth provider settings. Do not announce production cloud Blend availability until a real account/create/join/leave round trip has been tested against the configured project.

For the current local scoring and profile format, see [Group Blend MVP](../../docs/group-blend-mvp.md).
