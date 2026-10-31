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
