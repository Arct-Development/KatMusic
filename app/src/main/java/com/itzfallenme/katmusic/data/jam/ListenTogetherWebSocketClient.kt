package com.itzfallenme.katmusic.data.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.util.concurrent.TimeUnit

data class JamEvent(val type: String, val payload: JsonObject)

data class JamRoomMember(
    val id: String,
    val displayName: String,
    val role: String,
    val connected: Boolean = true,
)
data class JamPlayback(val title: String, val artist: String, val videoId: String, val artworkUrl: String, val isPlaying: Boolean, val positionMs: Long, val durationMs: Long)
data class JamTrackSuggestion(val from: String, val displayName: String, val title: String, val artist: String, val videoId: String, val artworkUrl: String)

data class JamJoinRequest(
    val requestId: String,
    val memberId: String,
    val displayName: String,
)

/**
 * One long-lived relay connection for Listen Together.
 *
 * It deliberately outlives the screen: a [ViewModel] that owned this client
 * used to close the socket whenever the user navigated away, dropping them out
 * of the room and losing the host role. The client keeps the joined room and
 * reconnects with the session token after a network drop.
 */
@Singleton
class ListenTogetherWebSocketClient @Inject constructor() : Closeable {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var socket: WebSocket? = null
    @Volatile private var generation = 0
    @Volatile private var roomRestoredGeneration = -1
    @Volatile private var activeEndpoint = ""
    @Volatile private var activeDisplayName = ""
    @Volatile private var manualLeave = true
    @Volatile private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _pendingRoomCode = MutableStateFlow("")
    val pendingRoomCode: StateFlow<String> = _pendingRoomCode.asStateFlow()

    private val _sessionToken = MutableStateFlow("")
    val sessionToken: StateFlow<String> = _sessionToken.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    private val _memberId = MutableStateFlow("")
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _members = MutableStateFlow<List<JamRoomMember>>(emptyList())
    val members: StateFlow<List<JamRoomMember>> = _members.asStateFlow()
    private val _playback = MutableStateFlow<JamPlayback?>(null)
    val playback: StateFlow<JamPlayback?> = _playback.asStateFlow()
    private val _sharedQueue = MutableStateFlow<List<JamPlayback>>(emptyList())
    val sharedQueue: StateFlow<List<JamPlayback>> = _sharedQueue.asStateFlow()
    private val _suggestions = MutableStateFlow<List<JamTrackSuggestion>>(emptyList())
    val suggestions: StateFlow<List<JamTrackSuggestion>> = _suggestions.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<JamJoinRequest>>(emptyList())
    val pendingRequests: StateFlow<List<JamJoinRequest>> = _pendingRequests.asStateFlow()

    private val _events = MutableStateFlow<List<JamEvent>>(emptyList())
    val events: StateFlow<List<JamEvent>> = _events.asStateFlow()

    fun createRoom(endpoint: String, displayName: String) {
        activeEndpoint = endpoint.trim()
        activeDisplayName = displayName.trim().take(32)
        manualLeave = false
        reconnectAttempt = 0
        cancelReconnect()
        clearRoomState(clearToken = true)
        connect(
            endpoint = activeEndpoint,
            firstMessage = buildJsonObject {
                put("type", "create_room")
                put("displayName", activeDisplayName)
            },
            preserveRoom = false,
            isReconnect = false,
        )
    }

    fun joinRoom(endpoint: String, code: String, displayName: String) {
        activeEndpoint = endpoint.trim()
        activeDisplayName = displayName.trim().take(32)
        manualLeave = false
        reconnectAttempt = 0
        cancelReconnect()
        clearRoomState(clearToken = true)
        _pendingRoomCode.value = code.trim().uppercase().take(8)
        connect(
            endpoint = activeEndpoint,
            firstMessage = buildJsonObject {
                put("type", "join_room")
                put("roomCode", code.trim().uppercase().take(8))
                put("displayName", activeDisplayName)
            },
            preserveRoom = false,
            isReconnect = false,
        )
    }

    private fun reconnectNow() {
        val code = _roomCode.value
        val token = _sessionToken.value
        if (manualLeave || code.isBlank() || token.isBlank()) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            val delayMs = (1_000L shl (reconnectAttempt - 1).coerceIn(0, 4)).coerceAtMost(15_000L)
            _connectionState.value = "Reconnecting…"
            delay(delayMs)
            if (!isActive || manualLeave) return@launch
            if (_roomCode.value != code || _sessionToken.value != token) return@launch
            connect(
                endpoint = activeEndpoint,
                firstMessage = buildJsonObject {
                    put("type", "reconnect")
                    put("roomCode", code)
                    put("sessionToken", token)
                    put("displayName", activeDisplayName)
                },
                preserveRoom = true,
                isReconnect = true,
            )
        }
    }

    private fun connect(
        endpoint: String,
        firstMessage: JsonObject,
        preserveRoom: Boolean,
        isReconnect: Boolean,
    ) {
        val normalized = endpoint.trim()
        if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
            _connectionState.value = "Server URL must start with wss:// or ws://"
            return
        }
        val request = runCatching { Request.Builder().url(normalized).build() }.getOrElse {
            _connectionState.value = "Invalid server URL"
            return
        }
        val thisGeneration = ++generation
        socket?.close(1000, "reconnecting")
        _connectionState.value = if (isReconnect) "Reconnecting…" else "Connecting…"
        if (!preserveRoom) {
            _members.value = emptyList()
            _playback.value = null
            _sharedQueue.value = emptyList()
            _suggestions.value = emptyList()
            _pendingRequests.value = emptyList()
            _events.value = emptyList()
        }
        val newSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (thisGeneration != generation) return
                _connectionState.value = if (isReconnect) "Restoring room…" else "Connected"
                webSocket.send(json.encodeToString(JsonObject.serializer(), firstMessage))
                if (isReconnect) {
                    // A half-open socket would otherwise sit in "Reconnecting…"
                    // forever; cancel it so the backoff loop gets another turn.
                    scope.launch {
                        delay(15_000L)
                        if (thisGeneration == generation && roomRestoredGeneration != thisGeneration && !manualLeave) {
                            webSocket.cancel()
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (thisGeneration != generation) return
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (thisGeneration != generation) return
                _connectionState.value = "Closing"
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (thisGeneration != generation) return
                _connectionState.value = "Disconnected"
                reconnectNow()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (thisGeneration != generation) return
                _connectionState.value = t.message?.take(120) ?: "Connection failed"
                reconnectNow()
            }
        })
        if (thisGeneration == generation) socket = newSocket
    }

    private fun handleMessage(text: String) {
        val message = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        val type = message["type"]?.jsonPrimitive?.contentOrNull ?: "message"
        val code = message["roomCode"]?.jsonPrimitive?.contentOrNull
        val token = message["sessionToken"]?.jsonPrimitive?.contentOrNull
        if (!token.isNullOrBlank()) _sessionToken.value = token

        when (type) {
            "room_created", "room_joined", "room_reconnected" -> {
                roomRestoredGeneration = generation
                reconnectAttempt = 0
                manualLeave = false
                if (!code.isNullOrBlank()) _roomCode.value = code
                _pendingRoomCode.value = ""
                message["memberId"]?.jsonPrimitive?.contentOrNull?.let { _memberId.value = it }
                message["room"]?.jsonObject?.let(::applyRoomSnapshot)
                _isHost.value = message["role"]?.jsonPrimitive?.contentOrNull == "host" ||
                    type == "room_created"
                _connectionState.value = "Connected"
                applyChatHistory(message)
                _pendingRequests.value = emptyList()
            }
            "join_pending" -> {
                if (!code.isNullOrBlank()) _pendingRoomCode.value = code
                _connectionState.value = "Waiting for host approval"
            }
            "join_request" -> {
                val request = JamJoinRequest(
                    requestId = message["requestId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    memberId = message["memberId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    displayName = message["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener",
                )
                if (_pendingRequests.value.none { it.requestId == request.requestId }) {
                    _pendingRequests.value = _pendingRequests.value + request
                }
            }
            "host_changed" -> {
                val hostId = message["hostId"]?.jsonPrimitive?.contentOrNull
                _isHost.value = !hostId.isNullOrBlank() && hostId == _memberId.value
                _members.value = _members.value.map { it.copy(role = if (it.id == hostId) "host" else "guest") }
            }
            "member_joined" -> {
                val member = message["member"]?.jsonObject
                if (member != null) {
                    val id = member["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (id.isNotBlank() && _members.value.none { it.id == id }) {
                        _members.value = _members.value + JamRoomMember(
                            id = id,
                            displayName = member["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener",
                            role = member["role"]?.jsonPrimitive?.contentOrNull ?: "guest",
                        )
                    }
                }
            }
            "member_left" -> {
                val id = message["memberId"]?.jsonPrimitive?.contentOrNull
                _members.value = _members.value.filterNot { it.id == id }
            }
            "member_disconnected" -> {
                val id = message["memberId"]?.jsonPrimitive?.contentOrNull
                _members.value = _members.value.map {
                    if (it.id == id) it.copy(connected = false) else it
                }
            }
            "member_reconnected" -> {
                val id = message["memberId"]?.jsonPrimitive?.contentOrNull
                _members.value = _members.value.map {
                    if (it.id == id) {
                        it.copy(connected = true, displayName = message["displayName"]?.jsonPrimitive?.contentOrNull ?: it.displayName)
                    } else {
                        it
                    }
                }
            }
            "playback_state" -> message["payload"]?.jsonObject?.let { _playback.value = it.toJamPlayback() }
            "queue_update" -> message["payload"]?.jsonObject?.get("queue")?.jsonArray?.let { queue ->
                _sharedQueue.value = queue.mapNotNull { runCatching { it.jsonObject.toJamPlayback() }.getOrNull() }
            }
            "queue_suggestion" -> {
                val track = message["track"]?.jsonObject
                if (track != null) _suggestions.value = (_suggestions.value + JamTrackSuggestion(
                    from = message["from"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    displayName = message["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener",
                    title = track["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown track",
                    artist = track["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown artist",
                    videoId = track["videoId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    artworkUrl = track["artworkUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )).takeLast(30)
            }
            "chat" -> Unit // already appended below
            "room_left", "join_denied" -> {
                manualLeave = true
                cancelReconnect()
                clearRoomState(clearToken = true)
                _connectionState.value = when (message["reason"]?.jsonPrimitive?.contentOrNull) {
                    "removed_by_host" -> "Removed from the room"
                    "session_expired" -> "Room session expired"
                    else -> "Disconnected"
                }
            }
            "join_request_expired" -> {
                _pendingRoomCode.value = ""
                _connectionState.value = "Join request expired"
            }
            "error" -> {
                val detail = message["message"]?.jsonPrimitive?.contentOrNull
                val errorCode = message["code"]?.jsonPrimitive?.contentOrNull
                if (errorCode == "session_expired") {
                    manualLeave = true
                    cancelReconnect()
                    clearRoomState(clearToken = true)
                    _connectionState.value = detail ?: "Room session expired"
                } else if (_roomCode.value.isBlank() &&
                    errorCode in setOf(
                        "already_in_room", "room_not_found", "room_full",
                        "storage_unavailable", "rate_limited",
                    )
                ) {
                    _pendingRoomCode.value = ""
                    _connectionState.value = detail ?: "Could not join the room"
                }
            }
        }
        if (type != "chat") {
            appendEvent(JamEvent(type, message))
        }
        // Chat events are part of the activity feed too.
        if (type == "chat") appendEvent(JamEvent(type, message))
    }

    private fun applyChatHistory(message: JsonObject) {
        val history = message["chatHistory"]?.jsonArray ?: return
        history.forEach { element ->
            val payload = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val key = payload["sentAt"]?.jsonPrimitive?.contentOrNull.orEmpty() + "|" +
                payload["memberId"]?.jsonPrimitive?.contentOrNull.orEmpty() + "|" +
                payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val duplicate = _events.value.any { event ->
                if (event.type != "chat") return@any false
                val existingKey = event.payload["sentAt"]?.jsonPrimitive?.contentOrNull.orEmpty() + "|" +
                    event.payload["memberId"]?.jsonPrimitive?.contentOrNull.orEmpty() + "|" +
                    event.payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                existingKey == key
            }
            if (!duplicate) appendEvent(JamEvent("chat", payload))
        }
    }

    private fun appendEvent(event: JamEvent) {
        _events.value = (_events.value + event).takeLast(100)
    }

    private fun clearRoomState(clearToken: Boolean) {
        _roomCode.value = ""
        _pendingRoomCode.value = ""
        if (clearToken) _sessionToken.value = ""
        _memberId.value = ""
        _isHost.value = false
        _members.value = emptyList()
        _playback.value = null
        _sharedQueue.value = emptyList()
        _suggestions.value = emptyList()
        _pendingRequests.value = emptyList()
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun applyRoomSnapshot(room: JsonObject) {
        _members.value = room["members"]?.jsonArray?.mapNotNull { item ->
            runCatching {
                val member = item.jsonObject
                JamRoomMember(
                    id = member["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    displayName = member["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener",
                    role = member["role"]?.jsonPrimitive?.contentOrNull ?: "guest",
                    connected = member["connected"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                )
            }.getOrNull()
        }.orEmpty()
        _playback.value = room["playback"]?.let { runCatching { it.jsonObject.toJamPlayback() }.getOrNull() }
        _sharedQueue.value = room["queue"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject.toJamPlayback() }.getOrNull() }.orEmpty()
    }

    private fun JsonObject.toJamPlayback(): JamPlayback {
        val track = this["track"]?.jsonObject ?: this
        return JamPlayback(
            title = track["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown track",
            artist = track["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown artist",
            videoId = track["videoId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artworkUrl = track["artworkUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isPlaying = this["isPlaying"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: (this["isPlaying"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
                ?: false,
            positionMs = this["positionMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            durationMs = this["durationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }

    private fun send(message: JsonObject): Boolean =
        socket?.send(json.encodeToString(JsonObject.serializer(), message)) == true

    fun approve(requestId: String, approved: Boolean) {
        if (send(buildJsonObject {
                put("type", "approve_member")
                put("roomCode", _roomCode.value)
                put("requestId", requestId)
                put("approved", approved)
            })) {
            _pendingRequests.value = _pendingRequests.value.filterNot { it.requestId == requestId }
        }
    }

    fun sendChat(text: String) {
        val safe = text.trim().take(500)
        if (safe.isEmpty() || _roomCode.value.isBlank()) return
        send(buildJsonObject {
            put("type", "chat")
            put("roomCode", _roomCode.value)
            put("text", safe)
        })
    }

    fun publishPlayback(
        title: String,
        artist: String,
        videoId: String,
        artworkUrl: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (_roomCode.value.isBlank() || !_isHost.value) return
        send(buildJsonObject {
            put("type", "playback_state")
            put("roomCode", _roomCode.value)
            put("payload", buildJsonObject {
                put("track", buildJsonObject {
                    put("title", title.take(160))
                    put("artist", artist.take(120))
                    put("videoId", videoId.take(32))
                    put("artworkUrl", artworkUrl.take(1024))
                })
                put("isPlaying", isPlaying)
                put("positionMs", positionMs.coerceAtLeast(0))
                put("durationMs", durationMs.coerceAtLeast(0))
            })
        })
    }

    fun suggestTrack(title: String, artist: String, videoId: String, artworkUrl: String) {
        if (_roomCode.value.isBlank()) return
        send(buildJsonObject {
            put("type", "queue_suggestion")
            put("roomCode", _roomCode.value)
            put("payload", buildJsonObject {
                put("track", buildJsonObject {
                    put("title", title.take(160))
                    put("artist", artist.take(120))
                    put("videoId", videoId.take(32))
                    put("artworkUrl", artworkUrl.take(1024))
                })
            })
        })
    }

    fun transferHost(memberId: String) {
        send(buildJsonObject {
            put("type", "transfer_host")
            put("roomCode", _roomCode.value)
            put("memberId", memberId)
        })
    }

    fun kickMember(memberId: String) {
        send(buildJsonObject {
            put("type", "kick_member")
            put("roomCode", _roomCode.value)
            put("memberId", memberId)
        })
    }

    fun publishQueue(tracks: List<com.itzfallenme.katmusic.playback.PlayableTrack>) {
        if (_roomCode.value.isBlank() || !_isHost.value) return
        send(buildJsonObject {
            put("type", "queue_update")
            put("roomCode", _roomCode.value)
            put("payload", buildJsonObject {
                put("queue", kotlinx.serialization.json.buildJsonArray {
                    tracks.take(100).forEach { track ->
                        add(buildJsonObject {
                            put("title", track.title.take(160))
                            put("artist", track.artist.take(120))
                            put("videoId", track.videoId.orEmpty().take(32))
                            put("artworkUrl", track.artworkUrl.orEmpty().take(1024))
                        })
                    }
                })
            })
        })
    }

    fun leaveRoom() {
        if (_roomCode.value.isNotBlank()) {
            send(buildJsonObject {
                put("type", "leave_room")
                put("roomCode", _roomCode.value)
            })
        }
        manualLeave = true
        cancelReconnect()
        _connectionState.value = "Disconnected"
        clearRoomState(clearToken = true)
        // Keep the WebSocket open so the same connection can create or join
        // another room; the relay resets its per-socket room state on leave.
    }

    fun dismissSuggestion(videoId: String) {
        _suggestions.value = _suggestions.value.filterNot { it.videoId == videoId }
    }

    override fun close() {
        manualLeave = true
        cancelReconnect()
        generation++
        socket?.close(1000, "client closed")
        socket = null
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
        _connectionState.value = "Disconnected"
    }
}
