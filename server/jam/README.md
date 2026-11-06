# KatMusic Jam server

Go WebSocket service for **Listen Together**. It synchronizes playback position, queue, host roles and chat. Audio is never relayed: every listener resolves the track through their own authorized provider.

## Layout

```
server/jam
├── cmd/jam              # process entrypoint
├── internal/wire        # envelopes, payloads, validation
├── internal/rooms       # rooms, connections, playback clock, chat, stations
└── internal/api         # HTTP/WebSocket routes, admin panel, share images
```

## Endpoints

| Path | Purpose |
|---|---|
| `GET /ws` | WebSocket session used by the Android app |
| `GET /healthz` | Liveness/readiness probe |
| `GET /stats` | Active room and listener counts |
| `GET /rooms` | Always-on rooms with live listener counts |
| `GET /og/{code}.png` | Share preview image |
| `GET /admin` | Password-protected room and chat history panel |

## Deploy on Render

- Root Directory: `server/jam`
- Runtime: Docker
- Health Check Path: `/healthz`
- Environment:
  - `PORT` (Render sets this automatically)
  - `SUPABASE_URL` — the dedicated **Jam** Supabase project URL
  - `SUPABASE_SERVICE_ROLE_KEY` — service-role key for that Jam project (server only)
  - `RADIO_SOURCE_URL` — optional station feed base URL; set to `off` to disable the always-on radio loops
  - `ADMIN_PASSWORD` — enables `/admin` when set
- Run `supabase/migrations/0001_jam_persistence.sql` in the Jam project before deploying.
- Run **one instance**. Rooms and chat are snapshotted to Supabase every 15 seconds and on shutdown, then restored when the service wakes. Live sockets cannot be restored, so the first listener to rejoin becomes host and playback resumes paused at the saved position.

Without a station feed the server still creates the `INDIA` and `WORLD` rooms, but they stay idle until someone joins. With `RADIO_SOURCE_URL` pointing at a compatible station API, those rooms play continuously and cannot be controlled by listeners.

## Local development

```bash
cd server/jam
go run ./cmd/jam
```

Use `ws://localhost:8080/ws` in the app. Run the tests with:

```bash
go test ./...
```

## Protocol

Every frame is an envelope:

```json
{ "type": "join_room", "payload": { "room_code": "ABC123", "name": "Alex" } }
```

Client to server:

| Type | Payload |
|---|---|
| `create_room` | `name`, optional `room_mode` (`host_only` / `collaborative`) |
| `join_room` | `room_code`, `name` |
| `leave_room` | — |
| `send_chat` | `text`, optional `reply_to_id` |
| `playback_action` | `action` (`play`, `pause`, `seek`, `change_track`, `skip_next`, `skip_prev`, `queue_add`, `queue_remove`, `queue_clear`), `track`, `position_ms`, `client_time_ms`, `is_playing` |
| `suggest_track` | `track` |
| `approve_suggestion` / `reject_suggestion` | `suggestion_id` |
| `change_room_mode` | `room_mode` |
| `grant_host` / `revoke_host` | `user_id` (co-host management) |
| `transfer_host` | `user_id` (live-station admin transfer) |
| `queue_update` | `queue` (main host only, at most five upcoming tracks) |
| `ping` | client timestamp |

Server to client:

| Type | Purpose |
|---|---|
| `room_created` | Room code, user id and host flag for a new room |
| `sync_state` | Full snapshot: room mode, participants, playback, chat history |
| `sync_playback` | Authoritative playback action and timeline |
| `queue_updated` | Shared upcoming queue |
| `user_joined`, `user_left` | Membership changes |
| `host_changed`, `cohost_changed` | Role changes |
| `suggestion_received` | A guest proposed a track to the main host |
| `chat_message` | Chat entry (24-hour room buffer, latest 50 sent to joiners) |
| `room_mode_changed`, `station_mode`, `cooldown` | Room policy changes |
| `pong`, `error` | Health and error responses |

Rooms support one main host plus one co-host. In `collaborative` mode any participant may drive playback; in `host_only` mode only the two hosts may. Host-only rooms promote the longest-tenured listener when the host leaves. Live stations have no co-hosts and return to automatic playback when their admin leaves.

## Operational limits

- Maximum 1,000 participants per room.
- Chat is retained for 24 hours, capped at 5,000 messages per room.
- Ended rooms are kept in memory for 24 hours for the admin panel.
- Room state survives a restart through Supabase. Live connections and host roles do not; the first listener to rejoin takes over.
- Persistence is optional. Without `SUPABASE_URL` and the service-role key the server runs memory-only, which is fine for local development.
- Inbound frames are rate-limited per connection; oversized or malformed frames are rejected.
