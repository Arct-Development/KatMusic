# KatMusic Listen Together WebSocket server

This is an original room relay for KatMusic. It is separate from the Android process and can be deployed on any Node.js 20+ host that supports WebSockets.

## Run locally

    cd server/jam
    npm install
    npm start

The health endpoint is http://localhost:8787/healthz and the WebSocket endpoint is ws://localhost:8787/ws.

## Deploy with Docker

    docker build -t katmusic-jam .
    docker run --rm -p 8787:8787 -e PORT=8787 katmusic-jam

Put it behind a TLS reverse proxy and use wss:// in production. Do not expose an unencrypted WebSocket endpoint to the public internet.

## Environment

- PORT: listening port (default 8787)
- MAX_ROOM_MEMBERS: room capacity from 2 to 50 (default 20)
- ALLOWED_ORIGINS: optional comma-separated browser origins; native Android clients generally omit Origin

## Protocol

Messages are JSON objects with a type field.

- create_room: { "type": "create_room", "displayName": "Host" }
- join_room: { "type": "join_room", "roomCode": "ABC234", "displayName": "Guest" }
- approve_member: { "type": "approve_member", "roomCode": "ABC234", "requestId": "...", "approved": true }
- reconnect: { "type": "reconnect", "roomCode": "ABC234", "sessionToken": "..." }
- playback_state: host-only; payload includes track, isPlaying, positionMs and durationMs
- queue_suggestion: guests send payload.track; the host receives queue_suggestion
- queue_update: host broadcasts payload.queue
- chat: { "type": "chat", "text": "..." }
- transfer_host: host-only; memberId identifies the next host
- kick_member: host-only; memberId identifies the listener to remove
- leave_room and ping

Join requests require explicit host approval. Session tokens are random, scoped to a room, and can be used to reconnect after a transient disconnect. Room state is in memory: restarting the service ends active rooms. This is intentional for the first deployable version; use a shared store before running multiple server replicas.

## Security notes

The server enforces payload and message-rate limits, sanitizes display names and chat, bounds queue sizes, uses TLS at the deployment edge, and never relays stream URLs or protected audio. It synchronizes metadata and playback position only; each listener resolves and plays audio independently from their own authorized provider.
