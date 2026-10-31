import http from "node:http";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { createClient } from "@supabase/supabase-js";
import { WebSocketServer, WebSocket } from "ws";

const PORT = Number(process.env.PORT || 8787);
const MAX_PAYLOAD = 32 * 1024;
const MAX_MEMBERS = Math.min(50, Math.max(2, Number(process.env.MAX_ROOM_MEMBERS || 20)));
const ROOM_TTL_MS = 24 * 60 * 60 * 1000;
const DISCONNECTED_TTL_MS = 6 * 60 * 60 * 1000;
const PENDING_TTL_MS = 60_000;
const HEARTBEAT_MS = 25_000;
const CHAT_HISTORY_LIMIT = 50;
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || "").split(",").map(v => v.trim()).filter(Boolean);

const supabaseUrl = process.env.SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (Boolean(supabaseUrl) !== Boolean(serviceKey)) {
  throw new Error("Set both SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY, or neither.");
}
if (process.env.REQUIRE_SUPABASE === "true" && !supabaseUrl) {
  throw new Error("Supabase is required. Configure SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.");
}
const db = supabaseUrl ? createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false }
}) : null;

const rooms = new Map();
const clients = new Map();
const rateWindows = new WeakMap();
let dbHealthy = Boolean(db);
let shuttingDown = false;

const nowIso = () => new Date().toISOString();
const hashToken = token => createHash("sha256").update(token).digest("hex");
const token = () => randomBytes(32).toString("base64url");

function makeCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = randomBytes(6);
  return [...bytes].map(b => alphabet[b % alphabet.length]).join("");
}
function cleanName(value) {
  if (typeof value !== "string") return "Listener";
  return value.trim().replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 32) || "Listener";
}
function cleanText(value, max = 500) {
  if (typeof value !== "string") return "";
  return value.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "").trim().slice(0, max);
}
function safeTrack(track) {
  if (!track || typeof track !== "object" || Array.isArray(track)) return null;
  const videoId = cleanText(track.videoId, 32);
  const artworkUrl = cleanText(track.artworkUrl, 1024);
  return {
    title: cleanText(track.title, 160),
    artist: cleanText(track.artist, 120),
    videoId: /^[A-Za-z0-9_-]{1,32}$/.test(videoId) ? videoId : "",
    artworkUrl: /^https:\/\//i.test(artworkUrl) ? artworkUrl : ""
  };
}
function send(ws, type, payload = {}) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;
  try { ws.send(JSON.stringify({ type, ...payload })); } catch {}
}
function broadcast(room, type, payload = {}, except = null) {
  for (const member of room.members.values()) if (member.ws !== except) send(member.ws, type, payload);
}
function rateAllowed(ws) {
  const now = Date.now();
  const window = rateWindows.get(ws) || { start: now, count: 0 };
  if (now - window.start >= 10_000) { window.start = now; window.count = 0; }
  window.count += 1;
  rateWindows.set(ws, window);
  return window.count <= 70;
}
function roomSnapshot(room) {
  return {
    roomCode: room.code,
    hostId: room.hostId,
    members: [...room.members.values()].map(m => ({
      id: m.id, displayName: m.displayName, role: m.id === room.hostId ? "host" : "guest"
    })),
    playback: room.playback,
    queue: room.queue
  };
}
function getContext(ws, message) {
  const state = clients.get(ws);
  if (!state?.roomCode) return null;
  const room = rooms.get(state.roomCode);
  if (!room || (message.roomCode && message.roomCode !== room.code)) return null;
  const member = room.members.get(state.memberId);
  return member ? { state, room, member } : null;
}
async function dbCall(query) {
  if (!db) return null;
  const { data, error } = await query;
  if (error) { dbHealthy = false; throw error; }
  dbHealthy = true;
  return data;
}
async function persistRoom(room, force = false) {
  if (!db) return;
  const now = Date.now();
  if (!force && room.lastPersistAt && now - room.lastPersistAt < 10_000) return;
  await dbCall(db.from("jam_rooms").upsert({
    code: room.code,
    host_member_id: room.hostId,
    playback: room.playback,
    queue: room.queue,
    created_at: new Date(room.createdAt).toISOString(),
    updated_at: nowIso(),
    expires_at: new Date(Date.now() + ROOM_TTL_MS).toISOString()
  }, { onConflict: "code" }));
  room.lastPersistAt = now;
}
async function persistMember(room, member) {
  if (!db) return;
  await dbCall(db.from("jam_members").upsert({
    id: member.id,
    room_code: room.code,
    display_name: member.displayName,
    session_token_hash: member.sessionHash,
    joined_at: new Date(member.joinedAt).toISOString(),
    last_seen_at: nowIso()
  }, { onConflict: "id" }));
}
async function deleteMember(memberId) {
  if (db) await dbCall(db.from("jam_members").delete().eq("id", memberId));
}
async function loadChat(roomCode) {
  if (!db) return [];
  const rows = await dbCall(db.from("jam_chat").select("id,member_id,display_name,message,created_at")
    .eq("room_code", roomCode).order("id", { ascending: false }).limit(CHAT_HISTORY_LIMIT));
  return (rows || []).reverse().map(row => ({
    memberId: row.member_id, displayName: row.display_name, text: row.message,
    sentAt: new Date(row.created_at).getTime()
  }));
}
async function persistChat(room, member, text) {
  if (!db) return;
  await dbCall(db.from("jam_chat").insert({
    room_code: room.code, member_id: member.id, display_name: member.displayName, message: text
  }));
  const oldRows = await dbCall(db.from("jam_chat").select("id").eq("room_code", room.code)
    .order("id", { ascending: false }).range(CHAT_HISTORY_LIMIT, CHAT_HISTORY_LIMIT + 250));
  if (oldRows?.length) await dbCall(db.from("jam_chat").delete().in("id", oldRows.map(row => row.id)));
}
async function restoreRooms() {
  if (!db) {
    process.stdout.write("Supabase is not configured; using temporary in-memory storage.\\n");
    return;
  }
  const roomRows = await dbCall(db.from("jam_rooms").select("*").gt("expires_at", nowIso()));
  if (!roomRows?.length) return;
  const codes = roomRows.map(row => row.code);
  const memberRows = await dbCall(db.from("jam_members").select("*").in("room_code", codes));
  const byRoom = new Map(codes.map(code => [code, []]));
  for (const row of memberRows || []) byRoom.get(row.room_code)?.push(row);
  for (const row of roomRows) {
    const members = new Map();
    const sessions = new Map();
    for (const saved of byRoom.get(row.code) || []) {
      const member = {
        id: saved.id, ws: null, displayName: cleanName(saved.display_name),
        sessionHash: saved.session_token_hash, joinedAt: new Date(saved.joined_at).getTime(),
        disconnectedAt: Date.now()
      };
      members.set(member.id, member);
      sessions.set(member.sessionHash, member.id);
    }
    if (!members.size) continue;
    let hostId = members.has(row.host_member_id) ? row.host_member_id : members.keys().next().value;
    const room = {
      code: row.code, hostId, members, sessions, pending: new Map(),
      playback: row.playback || null, queue: Array.isArray(row.queue) ? row.queue : [],
      createdAt: new Date(row.created_at).getTime(), lastActivityAt: Date.now()
    };
    rooms.set(room.code, room);
    if (hostId !== row.host_member_id) await persistRoom(room, true);
  }
  process.stdout.write("Restored " + rooms.size + " persistent Jam rooms from Supabase.\\n");
}
function detachPending(ws, state) {
  if (state?.pendingRoomCode && state.pendingRequestId) {
    rooms.get(state.pendingRoomCode)?.pending.delete(state.pendingRequestId);
  }
}
async function removeMember(room, memberId, reason = "left") {
  const member = room.members.get(memberId);
  if (!member) return;
  room.members.delete(memberId);
  room.sessions.delete(member.sessionHash);
  if (member.ws) clients.delete(member.ws);
  send(member.ws, "room_left", { roomCode: room.code, reason });
  await deleteMember(memberId);
  if (!room.members.size) {
    rooms.delete(room.code);
    if (db) await dbCall(db.from("jam_rooms").delete().eq("code", room.code));
    return;
  }
  if (room.hostId === memberId) {
    room.hostId = room.members.values().next().value.id;
    await persistRoom(room, true);
    broadcast(room, "host_changed", { hostId: room.hostId });
  } else {
    await persistRoom(room, true);
  }
  broadcast(room, "member_left", { memberId, displayName: member.displayName, reason });
}
async function handleMessage(ws, raw) {
  if (!rateAllowed(ws)) {
    send(ws, "error", { code: "rate_limited", message: "Too many messages. Slow down." });
    ws.close(1008, "rate limited");
    return;
  }
  let message;
  try { message = JSON.parse(raw.toString()); }
  catch { send(ws, "error", { code: "invalid_json", message: "Message must be valid JSON." }); return; }
  if (!message || typeof message.type !== "string") {
    send(ws, "error", { code: "invalid_message", message: "A message type is required." }); return;
  }
  const state = clients.get(ws);
  if (!state) return;

  switch (message.type) {
    case "create_room": {
      if (state.roomCode || state.pendingRoomCode) return send(ws, "error", { code: "already_in_room", message: "Leave the current room first." });
      let code;
      do { code = makeCode(); } while (rooms.has(code));
      const sessionToken = token();
      const member = { id: state.id, ws, displayName: cleanName(message.displayName), sessionHash: hashToken(sessionToken), joinedAt: Date.now(), disconnectedAt: null };
      const room = { code, hostId: member.id, members: new Map([[member.id, member]]), sessions: new Map([[member.sessionHash, member.id]]), pending: new Map(), playback: null, queue: [], createdAt: Date.now(), lastActivityAt: Date.now() };
      try {
        await persistRoom(room, true);
        await persistMember(room, member);
      } catch (error) {
        if (db) await db.from("jam_rooms").delete().eq("code", code);
        send(ws, "error", { code: "storage_unavailable", message: "Room storage is temporarily unavailable. Try again." });
        process.stderr.write("create_room persistence failed: " + error.message + "\\n");
        return;
      }
      rooms.set(code, room);
      state.roomCode = code; state.memberId = member.id;
      send(ws, "room_created", { roomCode: code, sessionToken, memberId: member.id, role: "host", room: roomSnapshot(room) });
      break;
    }
    case "join_room": {
      if (state.roomCode || state.pendingRoomCode) return send(ws, "error", { code: "already_in_room", message: "Leave the current room first." });
      const code = String(message.roomCode || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);
      const room = rooms.get(code);
      if (!room) return send(ws, "error", { code: "room_not_found", message: "That room does not exist." });
      if (room.members.size + room.pending.size >= MAX_MEMBERS) return send(ws, "error", { code: "room_full", message: "That room is full." });
      const requestId = randomUUID();
      const pending = { requestId, ws, clientId: state.id, displayName: cleanName(message.displayName), createdAt: Date.now() };
      room.pending.set(requestId, pending);
      state.pendingRoomCode = code; state.pendingRequestId = requestId;
      send(ws, "join_pending", { roomCode: code, requestId, message: "Waiting for the host to approve your request." });
      send(room.members.get(room.hostId)?.ws, "join_request", { roomCode: code, requestId, memberId: state.id, displayName: pending.displayName });
      break;
    }
    case "approve_member": {
      const ctx = getContext(ws, message);
      if (!ctx || ctx.member.id !== ctx.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can approve listeners." });
      const pending = ctx.room.pending.get(String(message.requestId || ""));
      if (!pending) return send(ws, "error", { code: "request_expired", message: "That join request has expired." });
      ctx.room.pending.delete(pending.requestId);
      const pendingState = clients.get(pending.ws);
      if (!pendingState || pending.ws.readyState !== WebSocket.OPEN) return;
      pendingState.pendingRoomCode = null; pendingState.pendingRequestId = null;
      if (message.approved !== true) { send(pending.ws, "join_denied", { roomCode: ctx.room.code }); return; }
      if (ctx.room.members.size >= MAX_MEMBERS) return send(pending.ws, "error", { code: "room_full", message: "That room is full." });
      const sessionToken = token();
      const member = { id: pending.clientId, ws: pending.ws, displayName: pending.displayName, sessionHash: hashToken(sessionToken), joinedAt: Date.now(), disconnectedAt: null };
      try { await persistMember(ctx.room, member); await persistRoom(ctx.room, true); }
      catch (error) { send(pending.ws, "error", { code: "storage_unavailable", message: "Could not save room membership. Try again." }); return; }
      ctx.room.members.set(member.id, member); ctx.room.sessions.set(member.sessionHash, member.id);
      pendingState.roomCode = ctx.room.code; pendingState.memberId = member.id;
      const chatHistory = await loadChat(ctx.room.code).catch(() => []);
      send(pending.ws, "room_joined", { roomCode: ctx.room.code, sessionToken, memberId: member.id, role: "guest", room: roomSnapshot(ctx.room), chatHistory });
      broadcast(ctx.room, "member_joined", { member: { id: member.id, displayName: member.displayName, role: "guest" } }, pending.ws);
      break;
    }
    case "reconnect": {
      const code = String(message.roomCode || "").toUpperCase();
      const room = rooms.get(code);
      if (!room || typeof message.sessionToken !== "string" || message.sessionToken.length > 128) return send(ws, "error", { code: "session_expired", message: "The room session is no longer available." });
      const memberId = room.sessions.get(hashToken(message.sessionToken));
      const member = memberId && room.members.get(memberId);
      if (!member) return send(ws, "error", { code: "session_expired", message: "The room session is no longer available." });
      if (member.ws && member.ws !== ws && member.ws.readyState === WebSocket.OPEN) member.ws.close(4001, "session resumed elsewhere");
      if (member.ws) clients.delete(member.ws);
      member.ws = ws; member.disconnectedAt = null;
      member.displayName = cleanName(message.displayName || member.displayName);
      state.roomCode = room.code; state.memberId = member.id;
      await persistMember(room, member);
      const chatHistory = await loadChat(room.code).catch(() => []);
      send(ws, "room_reconnected", { roomCode: room.code, memberId: member.id, role: member.id === room.hostId ? "host" : "guest", room: roomSnapshot(room), chatHistory });
      broadcast(room, "member_reconnected", { memberId: member.id, displayName: member.displayName }, ws);
      break;
    }
    case "playback_state": {
      const ctx = getContext(ws, message);
      if (!ctx) return send(ws, "error", { code: "not_in_room", message: "Join a room first." });
      if (ctx.member.id !== ctx.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host controls shared playback." });
      const payload = message.payload && typeof message.payload === "object" ? message.payload : {};
      const positionMs = Number(payload.positionMs), durationMs = Number(payload.durationMs);
      ctx.room.playback = {
        track: safeTrack(payload.track),
        isPlaying: payload.isPlaying === true,
        positionMs: Number.isFinite(positionMs) ? Math.max(0, Math.min(positionMs, 7 * 24 * 60 * 60 * 1000)) : 0,
        durationMs: Number.isFinite(durationMs) ? Math.max(0, Math.min(durationMs, 7 * 24 * 60 * 60 * 1000)) : 0,
        updatedAt: Date.now()
      };
      broadcast(ctx.room, "playback_state", { payload: ctx.room.playback }, ws);
      if (db) persistRoom(ctx.room).catch(error => process.stderr.write("playback persistence failed: " + error.message + "\\n"));
      break;
    }
    case "queue_suggestion": {
      const ctx = getContext(ws, message);
      if (!ctx) return send(ws, "error", { code: "not_in_room", message: "Join a room first." });
      const track = safeTrack(message.payload?.track);
      if (!track?.videoId) return send(ws, "error", { code: "invalid_track", message: "A valid track is required." });
      send(ctx.room.members.get(ctx.room.hostId)?.ws, "queue_suggestion", { from: ctx.member.id, displayName: ctx.member.displayName, track });
      break;
    }
    case "queue_update": {
      const ctx = getContext(ws, message);
      if (!ctx || ctx.member.id !== ctx.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can update the shared queue." });
      const queue = Array.isArray(message.payload?.queue) ? message.payload.queue.slice(0, 100).map(safeTrack).filter(track => track?.videoId) : [];
      ctx.room.queue = queue;
      try { await persistRoom(ctx.room, true); } catch (error) { return send(ws, "error", { code: "storage_unavailable", message: "Queue could not be saved." }); }
      broadcast(ctx.room, "queue_update", { payload: { queue } }, ws);
      break;
    }
    case "chat": {
      const ctx = getContext(ws, message);
      if (!ctx) return send(ws, "error", { code: "not_in_room", message: "Join a room first." });
      const text = cleanText(message.text, 500);
      if (!text) return;
      try { await persistChat(ctx.room, ctx.member, text); }
      catch (error) { return send(ws, "error", { code: "storage_unavailable", message: "Chat could not be saved. Try again." }); }
      broadcast(ctx.room, "chat", { memberId: ctx.member.id, displayName: ctx.member.displayName, text, sentAt: Date.now() });
      break;
    }
    case "transfer_host": {
      const ctx = getContext(ws, message);
      if (!ctx || ctx.member.id !== ctx.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can transfer hosting." });
      const target = ctx.room.members.get(String(message.memberId || ""));
      if (!target) return send(ws, "error", { code: "member_not_found", message: "That listener is no longer in the room." });
      ctx.room.hostId = target.id;
      try { await persistRoom(ctx.room); } catch { ctx.room.hostId = ctx.member.id; return send(ws, "error", { code: "storage_unavailable", message: "Host transfer could not be saved." }); }
      broadcast(ctx.room, "host_changed", { hostId: target.id });
      break;
    }
    case "kick_member": {
      const ctx = getContext(ws, message);
      if (!ctx || ctx.member.id !== ctx.room.hostId) return send(ws, "error", { code: "host_only", message: "Only the host can remove listeners." });
      const targetId = String(message.memberId || "");
      if (targetId !== ctx.room.hostId && ctx.room.members.has(targetId)) await removeMember(ctx.room, targetId, "removed_by_host");
      break;
    }
    case "leave_room": {
      const ctx = getContext(ws, message);
      if (ctx) await removeMember(ctx.room, ctx.member.id, "left");
      break;
    }
    case "ping": send(ws, "pong", { at: Date.now() }); break;
    default: send(ws, "error", { code: "unknown_type", message: "Unsupported message type." });
  }
  const current = clients.get(ws);
  const activeRoom = current?.roomCode && rooms.get(current.roomCode);
  if (activeRoom) activeRoom.lastActivityAt = Date.now();
}

const server = http.createServer((req, res) => {
  if (req.url === "/healthz") {
    const status = !db || dbHealthy;
    res.writeHead(status ? 200 : 503, { "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify({ ok: status, service: "katmusic-jam", persistence: db ? "supabase" : "memory", database: db ? (dbHealthy ? "ok" : "degraded") : "not_configured", rooms: rooms.size, connections: clients.size }));
    return;
  }
  res.writeHead(404, { "content-type": "application/json", "cache-control": "no-store" });
  res.end(JSON.stringify({ error: "not_found" }));
});
const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_PAYLOAD, perMessageDeflate: false, clientTracking: true });
server.on("upgrade", (req, socket, head) => {
  const origin = req.headers.origin;
  if (ALLOWED_ORIGINS.length && (!origin || !ALLOWED_ORIGINS.includes(origin))) {
    socket.write("HTTP/1.1 403 Forbidden\r\n\r\n"); socket.destroy(); return;
  }
  if (req.url !== "/ws") { socket.write("HTTP/1.1 404 Not Found\r\n\r\n"); socket.destroy(); return; }
  if (wss.clients.size >= 1000) { socket.write("HTTP/1.1 503 Service Unavailable\r\n\r\n"); socket.destroy(); return; }
  wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws, req));
});
wss.on("connection", ws => {
  const state = { id: randomUUID(), roomCode: null, memberId: null, pendingRoomCode: null, pendingRequestId: null, alive: true };
  clients.set(ws, state);
  ws.on("pong", () => { state.alive = true; });
  ws.on("message", data => { handleMessage(ws, data).catch(error => {
    process.stderr.write("message handler failed: " + error.message + "\\n");
    send(ws, "error", { code: "internal_error", message: "The server could not process that message." });
  }); });
  ws.on("close", () => {
    const current = clients.get(ws);
    if (!current) return;
    clients.delete(ws);
    detachPending(ws, current);
    if (current.roomCode) {
      const room = rooms.get(current.roomCode);
      const member = room?.members.get(current.memberId);
      if (member && member.ws === ws) {
        member.ws = null; member.disconnectedAt = Date.now();
        if (db) dbCall(db.from("jam_members").update({ last_seen_at: nowIso() }).eq("id", member.id)).catch(() => {});
        broadcast(room, "member_disconnected", { memberId: member.id, displayName: member.displayName });
      }
    }
  });
  ws.on("error", () => {});
});
const heartbeat = setInterval(() => {
  const now = Date.now();
  for (const ws of wss.clients) {
    const state = clients.get(ws);
    if (!state || !state.alive) { ws.terminate(); continue; }
    state.alive = false;
    try { ws.ping(); } catch { ws.terminate(); }
  }
  for (const [code, room] of rooms) {
    for (const [requestId, pending] of room.pending) {
      if (now - pending.createdAt > PENDING_TTL_MS) {
        room.pending.delete(requestId);
        const pendingState = clients.get(pending.ws);
        if (pendingState) { pendingState.pendingRoomCode = null; pendingState.pendingRequestId = null; }
        send(pending.ws, "join_request_expired", { roomCode: code });
      }
    }
    for (const [memberId, member] of room.members) {
      if (!member.ws && member.disconnectedAt && now - member.disconnectedAt > DISCONNECTED_TTL_MS) {
        removeMember(room, memberId, "session_expired").catch(error => process.stderr.write("member cleanup failed: " + error.message + "\\n"));
      }
    }
    if (room.members.size === 0 || now - room.lastActivityAt > ROOM_TTL_MS) {
      rooms.delete(code);
      if (db) dbCall(db.from("jam_rooms").delete().eq("code", code)).catch(() => {});
    }
  }
}, HEARTBEAT_MS);
heartbeat.unref();

async function start() {
  await restoreRooms();
  server.listen(PORT, "0.0.0.0", () => process.stdout.write("KatMusic Jam server listening on " + PORT + "\\n"));
}
function shutdown() {
  if (shuttingDown) return;
  shuttingDown = true;
  clearInterval(heartbeat);
  for (const ws of wss.clients) ws.close(1001, "server shutting down");
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10_000).unref();
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
start().catch(error => { process.stderr.write("Jam server startup failed: " + error.message + "\\n"); process.exit(1); });
