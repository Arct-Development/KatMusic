import http from "node:http";
import { randomBytes, randomUUID } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";

const PORT = Number(process.env.PORT || 8787);
const MAX_PAYLOAD = 64 * 1024;
const MAX_MEMBERS = Math.min(50, Math.max(2, Number(process.env.MAX_ROOM_MEMBERS || 20)));
const ROOM_TTL_MS = 6 * 60 * 60 * 1000;
const HEARTBEAT_MS = 25_000;
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || "")
  .split(",").map((value) => value.trim()).filter(Boolean);

const rooms = new Map();
const clients = new Map();
const rateWindows = new WeakMap();

function makeCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = randomBytes(6);
  let code = "";
  for (let i = 0; i < 6; i += 1) code += alphabet[bytes[i] % alphabet.length];
  return code;
}

function cleanName(value) {
  if (typeof value !== "string") return "Listener";
  const name = value.trim().replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 32);
  return name || "Listener";
}

function send(ws, type, payload = {}) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;
  ws.send(JSON.stringify({ type, ...payload }));
}

function broadcast(room, type, payload = {}, except = null) {
  for (const member of room.members.values()) {
    if (member.ws !== except) send(member.ws, type, payload);
  }
}

function rateAllowed(ws) {
  const now = Date.now();
  const window = rateWindows.get(ws) || { start: now, count: 0 };
  if (now - window.start > 10_000) {
    window.start = now;
    window.count = 0;
  }
  window.count += 1;
  rateWindows.set(ws, window);
  return window.count <= 80;
}

function roomSnapshot(room) {
  return {
    roomCode: room.code,
    hostId: room.hostId,
    members: [...room.members.values()].map((member) => ({
      id: member.id,
      displayName: member.displayName,
      role: member.id === room.hostId ? "host" : "guest"
    })),
    playback: room.playback,
    queue: room.queue
  };
}

function removeMember(room, memberId, reason = "left") {
  const member = room.members.get(memberId);
  if (!member) return;
  room.members.delete(memberId);
  if (member.sessionToken) room.sessions.delete(member.sessionToken);
  if (member.ws) clients.delete(member.ws);
  send(member.ws, "room_left", { roomCode: room.code, reason });
  if (room.members.size === 0) {
    rooms.delete(room.code);
    return;
  }
  if (room.hostId === memberId) {
    const next = room.members.values().next().value;
    room.hostId = next.id;
    broadcast(room, "host_changed", { hostId: room.hostId });
  }
  broadcast(room, "member_left", { memberId, displayName: member.displayName, reason });
}

function getRoom(ws, message) {
  const state = clients.get(ws);
  if (!state || !state.roomCode) return null;
  const room = rooms.get(state.roomCode);
  if (!room || (message.roomCode && message.roomCode !== room.code)) return null;
  return { state, room, member: room.members.get(state.memberId) };
}

function handleMessage(ws, raw) {
  if (!rateAllowed(ws)) {
    send(ws, "error", { code: "rate_limited", message: "Too many messages. Slow down." });
    ws.close(1008, "rate limited");
    return;
  }

  let message;
  try {
    message = JSON.parse(raw.toString());
  } catch {
    send(ws, "error", { code: "invalid_json", message: "Message must be valid JSON." });
    return;
  }
  if (!message || typeof message.type !== "string") {
    send(ws, "error", { code: "invalid_message", message: "A message type is required." });
    return;
  }

  const state = clients.get(ws) || { id: randomUUID(), roomCode: null, memberId: null };
  clients.set(ws, state);

  switch (message.type) {
    case "create_room": {
      if (state.roomCode) return send(ws, "error", { code: "already_in_room", message: "Leave the current room first." });
      let code;
      do { code = makeCode(); } while (rooms.has(code));
      const sessionToken = randomBytes(32).toString("base64url");
      const member = {
        id: state.id,
        ws,
        displayName: cleanName(message.displayName),
        sessionToken,
        joinedAt: Date.now()
      };
      const room = {
        code,
        hostId: member.id,
        members: new Map([[member.id, member]]),
        sessions: new Map([[sessionToken, member.id]]),
        pending: new Map(),
        playback: null,
        queue: [],
        createdAt: Date.now(),
        lastActivityAt: Date.now()
      };
      rooms.set(code, room);
      state.roomCode = code;
      state.memberId = member.id;
      send(ws, "room_created", { roomCode: code, sessionToken, memberId: member.id, role: "host", room: roomSnapshot(room) });
      break;
    }

    case "join_room": {
      if (state.roomCode) return send(ws, "error", { code: "already_in_room", message: "Leave the current room first." });
      const code = String(message.roomCode || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
      const room = rooms.get(code);
      if (!room) return send(ws, "error", { code: "room_not_found", message: "That room does not exist." });
      if (room.members.size + room.pending.size >= MAX_MEMBERS) return send(ws, "error", { code: "room_full", message: "That room is full." });
      const requestId = randomUUID();
      const pending = { requestId, ws, clientId: state.id, displayName: cleanName(message.displayName), createdAt: Date.now() };
      room.pending.set(requestId, pending);
      state.pendingRoomCode = code;
      state.pendingRequestId = requestId;
      send(ws, "join_pending", { roomCode: code, requestId, message: "Waiting for the host to approve your request." });
      const host = room.members.get(room.hostId);
      if (host) send(host.ws, "join_request", { roomCode: code, requestId, memberId: state.id, displayName: pending.displayName });
      break;
    }

    case "approve_member": {
      const context = getRoom(ws, message);
      if (!context || context.member.id !== context.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can approve listeners." });
      const pending = context.room.pending.get(String(message.requestId || ""));
      if (!pending) return send(ws, "error", { code: "request_expired", message: "That join request has expired." });
      context.room.pending.delete(pending.requestId);
      const pendingState = clients.get(pending.ws);
      if (!pendingState || pending.ws.readyState !== WebSocket.OPEN) {
        context.room.pending.delete(pending.requestId);
        return;
      }
      if (message.approved !== true) {
        if (pendingState) {
          pendingState.pendingRoomCode = null;
          pendingState.pendingRequestId = null;
        }
        send(pending.ws, "join_denied", { roomCode: context.room.code });
        return;
      }
      if (context.room.members.size >= MAX_MEMBERS) {
        send(pending.ws, "error", { code: "room_full", message: "That room is full." });
        return;
      }
      const sessionToken = randomBytes(32).toString("base64url");
      const member = {
        id: pending.clientId,
        ws: pending.ws,
        displayName: pending.displayName,
        sessionToken,
        joinedAt: Date.now()
      };
      context.room.members.set(member.id, member);
      context.room.sessions.set(sessionToken, member.id);
      pendingState.roomCode = context.room.code;
      pendingState.memberId = member.id;
      pendingState.pendingRoomCode = null;
      pendingState.pendingRequestId = null;
      send(pending.ws, "room_joined", {
        roomCode: context.room.code,
        sessionToken,
        memberId: member.id,
        role: "guest",
        room: roomSnapshot(context.room)
      });
      broadcast(context.room, "member_joined", {
        member: { id: member.id, displayName: member.displayName, role: "guest" }
      }, pending.ws);
      break;
    }

    case "reconnect": {
      const code = String(message.roomCode || "").toUpperCase();
      const room = rooms.get(code);
      if (!room || typeof message.sessionToken !== "string") return send(ws, "error", { code: "session_expired", message: "The room session is no longer available." });
      const memberId = room.sessions.get(message.sessionToken);
      const member = memberId && room.members.get(memberId);
      if (!member) return send(ws, "error", { code: "session_expired", message: "The room session is no longer available." });
      if (member.ws) {
        if (member.ws !== ws && member.ws.readyState === WebSocket.OPEN) member.ws.close(4001, "session resumed elsewhere");
        clients.delete(member.ws);
      }
      member.ws = ws;
      member.disconnectedAt = null;
      member.displayName = cleanName(message.displayName || member.displayName);
      state.roomCode = room.code;
      state.memberId = member.id;
      send(ws, "room_reconnected", {
        roomCode: room.code,
        memberId: member.id,
        role: member.id === room.hostId ? "host" : "guest",
        room: roomSnapshot(room)
      });
      broadcast(room, "member_reconnected", { memberId: member.id, displayName: member.displayName }, ws);
      break;
    }

    case "playback_state": {
      const context = getRoom(ws, message);
      if (!context) return send(ws, "error", { code: "not_in_room", message: "Join a room first." });
      if (context.member.id !== context.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host controls shared playback." });
      const payload = message.payload && typeof message.payload === "object" ? message.payload : {};
      const positionMs = Number(payload.positionMs);
      const durationMs = Number(payload.durationMs);
      context.room.playback = {
        track: payload.track && typeof payload.track === "object" ? payload.track : null,
        isPlaying: payload.isPlaying === true,
        positionMs: Number.isFinite(positionMs) ? Math.max(0, positionMs) : 0,
        durationMs: Number.isFinite(durationMs) ? Math.max(0, durationMs) : 0,
        updatedAt: Date.now()
      };
      broadcast(context.room, "playback_state", { payload: context.room.playback }, ws);
      break;
    }

    case "queue_suggestion": {
      const context = getRoom(ws, message);
      if (!context) return send(ws, "error", { code: "not_in_room", message: "Join a room first." });
      const track = message.payload && message.payload.track;
      if (!track || typeof track !== "object") return send(ws, "error", { code: "invalid_track", message: "A track is required." });
      const safeTrack = {
        title: String(track.title || "").slice(0, 160),
        artist: String(track.artist || "").slice(0, 120),
        videoId: String(track.videoId || "").slice(0, 32),
        artworkUrl: String(track.artworkUrl || "").slice(0, 1024)
      };
      send(context.room.members.get(context.room.hostId).ws, "queue_suggestion", {
        from: context.member.id,
        displayName: context.member.displayName,
        track: safeTrack
      });
      break;
    }

    case "queue_update": {
      const context = getRoom(ws, message);
      if (!context || context.member.id !== context.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can update the shared queue." });
      const queue = Array.isArray(message.payload?.queue) ? message.payload.queue.slice(0, 100) : [];
      context.room.queue = queue.map((track) => ({
        title: String(track?.title || "").slice(0, 160),
        artist: String(track?.artist || "").slice(0, 120),
        videoId: String(track?.videoId || "").slice(0, 32),
        artworkUrl: String(track?.artworkUrl || "").slice(0, 1024)
      }));
      broadcast(context.room, "queue_update", { payload: { queue: context.room.queue } }, ws);
      break;
    }

    case "chat": {
      const context = getRoom(ws, message);
      if (!context) return send(ws, "error", { code: "not_in_room", message: "Join a room first." });
      const text = String(message.text || "").trim().slice(0, 500);
      if (!text) return;
      broadcast(context.room, "chat", {
        memberId: context.member.id,
        displayName: context.member.displayName,
        text,
        sentAt: Date.now()
      });
      break;
    }

    case "transfer_host": {
      const context = getRoom(ws, message);
      if (!context || context.member.id !== context.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can transfer hosting." });
      const target = context.room.members.get(String(message.memberId || ""));
      if (!target) return send(ws, "error", { code: "member_not_found", message: "That listener is no longer in the room." });
      context.room.hostId = target.id;
      broadcast(context.room, "host_changed", { hostId: target.id });
      break;
    }

    case "kick_member": {
      const context = getRoom(ws, message);
      if (!context || context.member.id !== context.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can remove listeners." });
      const targetId = String(message.memberId || "");
      if (targetId === context.room.hostId) return;
      const target = context.room.members.get(targetId);
      if (target) removeMember(context.room, targetId, "removed_by_host");
      break;
    }

    case "leave_room": {
      const context = getRoom(ws, message);
      if (context) removeMember(context.room, context.member.id, "left");
      break;
    }

    case "ping":
      send(ws, "pong", { at: Date.now() });
      break;

    default:
      send(ws, "error", { code: "unknown_type", message: "Unsupported message type." });
  }

  const activeRoom = clients.get(ws)?.roomCode && rooms.get(clients.get(ws).roomCode);
  if (activeRoom) activeRoom.lastActivityAt = Date.now();
}

const server = http.createServer((req, res) => {
  if (req.url === "/healthz") {
    res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify({ ok: true, service: "katmusic-jam", rooms: rooms.size }));
    return;
  }
  res.writeHead(404, { "content-type": "application/json" });
  res.end(JSON.stringify({ error: "not_found" }));
});

const wss = new WebSocketServer({
  noServer: true,
  maxPayload: MAX_PAYLOAD,
  perMessageDeflate: false
});

server.on("upgrade", (req, socket, head) => {
  const origin = req.headers.origin;
  if (ALLOWED_ORIGINS.length && origin && !ALLOWED_ORIGINS.includes(origin)) {
    socket.write("HTTP/1.1 403 Forbidden\r\n\r\n");
    socket.destroy();
    return;
  }
  if (req.url !== "/ws") {
    socket.write("HTTP/1.1 404 Not Found\r\n\r\n");
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws, req));
});

wss.on("connection", (ws) => {
  const state = { id: randomUUID(), roomCode: null, memberId: null, alive: true };
  clients.set(ws, state);
  ws.on("pong", () => { state.alive = true; });
  ws.on("message", (data) => handleMessage(ws, data));
  ws.on("close", () => {
    const current = clients.get(ws);
    if (!current) return;
    clients.delete(ws);
    if (current.roomCode) {
      const room = rooms.get(current.roomCode);
      const member = room?.members.get(current.memberId);
      if (member && member.ws === ws) {
        member.ws = null;
        member.disconnectedAt = Date.now();
        broadcast(room, "member_disconnected", { memberId: member.id, displayName: member.displayName });
      }
    }
    if (current.pendingRoomCode && current.pendingRequestId) {
      rooms.get(current.pendingRoomCode)?.pending.delete(current.pendingRequestId);
    }
  });
  ws.on("error", () => {});
});

const heartbeat = setInterval(() => {
  const now = Date.now();
  for (const ws of wss.clients) {
    const state = clients.get(ws);
    if (!state || state.alive === false) {
      ws.terminate();
      continue;
    }
    state.alive = false;
    ws.ping();
  }
  for (const [code, room] of rooms) {
    for (const [memberId, member] of room.members) {
      if (!member.ws && member.disconnectedAt && now - member.disconnectedAt > ROOM_TTL_MS) {
        room.members.delete(memberId);
        room.sessions.delete(member.sessionToken);
        if (room.hostId === memberId && room.members.size > 0) {
          room.hostId = room.members.values().next().value.id;
          broadcast(room, "host_changed", { hostId: room.hostId });
        }
      }
    }
    for (const [requestId, pending] of room.pending) {
      if (now - pending.createdAt > 60_000) {
        room.pending.delete(requestId);
        send(pending.ws, "join_request_expired", { roomCode: code });
      }
    }
    if (now - room.lastActivityAt > ROOM_TTL_MS && room.members.size === 0) rooms.delete(code);
  }
}, HEARTBEAT_MS);

server.listen(PORT, "0.0.0.0", () => {
  process.stdout.write("KatMusic Jam server listening on " + PORT + "\n");
});

function shutdown() {
  clearInterval(heartbeat);
  for (const ws of wss.clients) ws.close(1001, "server shutting down");
  server.close(() => process.exit(0));
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
