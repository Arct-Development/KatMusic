package com.itzfallenme.katmusic.data.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.Closeable
import java.util.concurrent.TimeUnit
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class JamEvent(val type: String, val payload: JsonObject)

data class JamRoomMember(
    val id: String,
    val displayName: String,
    val role: String,
    val connected: Boolean = true,
)

data class JamPlayback(
    val title: String,
    val artist: String,
    val videoId: String,
    val artworkUrl: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val serverTimeMs: Long = 0L,
    val revision: Long = 0L,
    val fromUserId: String = "",
) {
    /** Position the room expects now, including network transit since the server frame. */
    fun positionAt(nowMs: Long): Long =
        if (isPlaying && serverTimeMs > 0) {
            positionMs + (nowMs - serverTimeMs).coerceIn(0L, 8_000L)
        } else {
            positionMs
        }
}

data class JamTrackSuggestion(
    val suggestionId: String,
    val from: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val videoId: String,
    val artworkUrl: String,
)

/**
 * One long-lived WebSocket for Listen Together.
 *
 * The app owns this for the whole process so leaving the screen does not drop
 * the room. A network drop re-sends join_room with the saved code and name;
 * Jam has no resume token, and the first reconnecting listener becomes host.
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
    @Volatile private var activeIntent: JsonObject? = null
    @Volatile private var manualLeave = true
    @Volatile private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _memberId = MutableStateFlow("")
    val memberId: StateFlow<String> = _memberId.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()
    private val _isCoHost = MutableStateFlow(false)
    val isCoHost: StateFlow<Boolean> = _isCoHost.asStateFlow()
    private val _hostId = MutableStateFlow("")
    val hostId: StateFlow<String> = _hostId.asStateFlow()
    private val _coHostId = MutableStateFlow("")
    val coHostId: StateFlow<String> = _coHostId.asStateFlow()
    private val _roomMode = MutableStateFlow("host_only")
    val roomMode: StateFlow<String> = _roomMode.asStateFlow()

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName.asStateFlow()
    private val _permanent = MutableStateFlow(false)
    val permanent: StateFlow<Boolean> = _permanent.asStateFlow()
    private val _radio = MutableStateFlow(false)
    val radio: StateFlow<Boolean> = _radio.asStateFlow()
    private val _stationAuto = MutableStateFlow(false)
    val stationAuto: StateFlow<Boolean> = _stationAuto.asStateFlow()

    private val _members = MutableStateFlow<List<JamRoomMember>>(emptyList())
    val members: StateFlow<List<JamRoomMember>> = _members.asStateFlow()
    private val _playback = MutableStateFlow<JamPlayback?>(null)
    val playback: StateFlow<JamPlayback?> = _playback.asStateFlow()
    private val _sharedQueue = MutableStateFlow<List<JamPlayback>>(emptyList())
    val sharedQueue: StateFlow<List<JamPlayback>> = _sharedQueue.asStateFlow()
    private val _suggestions = MutableStateFlow<List<JamTrackSuggestion>>(emptyList())
    val suggestions: StateFlow<List<JamTrackSuggestion>> = _suggestions.asStateFlow()
    private val _events = MutableStateFlow<List<JamEvent>>(emptyList())
    val events: StateFlow<List<JamEvent>> = _events.asStateFlow()

    fun createRoom(endpoint: String, displayName: String, collaborative: Boolean = false) {
        activeEndpoint = endpoint.trim()
        activeDisplayName = displayName.trim().take(32)
        manualLeave = false
        reconnectAttempt = 0
        cancelReconnect()
        clearRoomState()
        val payload = buildJsonObject {
            put("name", activeDisplayName)
            put("room_mode", if (collaborative) "collaborative" else "host_only")
        }
        activeIntent = envelope("create_room", payload)
        connect(activeIntent!!, preserveRoom = false, isReconnect = false)
    }

    fun joinRoom(endpoint: String, code: String, displayName: String) {
        activeEndpoint = endpoint.trim()
        activeDisplayName = displayName.trim().take(32)
        manualLeave = false
        reconnectAttempt = 0
        cancelReconnect()
        clearRoomState()
        val joinCode = code.trim().uppercase().take(8)
        activeIntent = joinEnvelope(joinCode)
        connect(activeIntent!!, preserveRoom = false, isReconnect = false)
    }

    private fun joinEnvelope(code: String) = envelope("join_room", buildJsonObject {
        put("room_code", code)
        put("name", activeDisplayName)
    })

    private fun reconnectNow() {
        if (manualLeave) return
        if (_roomCode.value.isBlank() && activeIntent == null) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            val delayMs = (1_000L shl (reconnectAttempt - 1).coerceIn(0, 4)).coerceAtMost(15_000L)
            _connectionState.value = "Reconnecting…"
            delay(delayMs)
            if (!isActive || manualLeave) return@launch
            val first = if (_roomCode.value.isNotBlank()) joinEnvelope(_roomCode.value) else activeIntent
            if (first == null) return@launch
            connect(first, preserveRoom = true, isReconnect = true)
        }
    }

    private fun connect(firstMessage: JsonObject, preserveRoom: Boolean, isReconnect: Boolean) {
        val normalized = activeEndpoint
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
        if (!preserveRoom) clearRoomState()

        val newSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (thisGeneration != generation) return
                _connectionState.value = if (isReconnect) "Restoring room…" else "Connected"
                webSocket.send(firstMessage.toString())
                scope.launch {
                    delay(15_000L)
                    if (thisGeneration == generation && roomRestoredGeneration != thisGeneration && !manualLeave) {
                        webSocket.cancel()
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
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val type = message["type"]?.jsonPrimitive?.contentOrNull ?: return
        val payload = message["payload"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: JsonObject(emptyMap())

        when (type) {
            "room_created" -> {
                roomRestoredGeneration = generation
                reconnectAttempt = 0
                manualLeave = false
                activeIntent = null
                payload.str("room_code")?.takeIf(String::isNotBlank)?.let { _roomCode.value = it }
                payload.str("user_id")?.takeIf(String::isNotBlank)?.let { _memberId.value = it }
                _isHost.value = payload.bool("is_host") ?: true
                _hostId.value = _memberId.value
                _roomMode.value = payload.str("room_mode") ?: "host_only"
                _connectionState.value = "Connected"
            }

            "sync_state" -> {
                roomRestoredGeneration = generation
                reconnectAttempt = 0
                manualLeave = false
                activeIntent = null
                payload.str("room_code")?.takeIf(String::isNotBlank)?.let { _roomCode.value = it }
                val me = payload.str("user_id").orEmpty()
                if (me.isNotBlank()) _memberId.value = me
                _hostId.value = payload.str("host_id").orEmpty()
                _coHostId.value = payload.str("cohost_id").orEmpty()
                _isCoHost.value = _coHostId.value.isNotBlank() && _coHostId.value == _memberId.value
                _isHost.value = (payload.bool("is_host") ?: false) ||
                    _hostId.value == _memberId.value || _isCoHost.value
                _roomMode.value = payload.str("room_mode") ?: "host_only"
                _roomName.value = payload.str("room_name").orEmpty()
                _permanent.value = payload.bool("permanent") ?: false
                _radio.value = payload.bool("radio") ?: false
                _stationAuto.value = payload.bool("station_auto") ?: false

                _members.value = payload["participants"]?.jsonArray?.mapNotNull { item ->
                    runCatching {
                        val member = item.jsonObject
                        val id = member.str("user_id").orEmpty()
                        if (id.isBlank()) return@runCatching null
                        JamRoomMember(
                            id = id,
                            displayName = member.str("name") ?: "Listener",
                            role = when {
                                member.bool("is_cohost") == true -> "cohost"
                                member.bool("is_host") == true -> "host"
                                else -> "guest"
                            },
                        )
                    }.getOrNull()
                }.orEmpty()

                payload["playback"]?.jsonObject?.let { playback ->
                    playback["current_track"]?.let { track ->
                        runCatching { parsePlayback(track.jsonObject, playback) }.getOrNull()?.let { _playback.value = it }
                    }
                    playback["queue"]?.jsonArray?.let { queue -> _sharedQueue.value = parseQueue(queue) }
                }
                appendChatHistory(payload["chat_history"]?.jsonArray)
                _connectionState.value = "Connected"
            }

            "sync_playback" -> {
                payload["track"]?.let { track ->
                    runCatching { parsePlayback(track.jsonObject, payload) }.getOrNull()?.let { _playback.value = it }
                }
                payload["queue"]?.jsonArray?.let { queue -> _sharedQueue.value = parseQueue(queue) }
            }

            "queue_updated" -> payload["queue"]?.jsonArray?.let { queue -> _sharedQueue.value = parseQueue(queue) }
            "user_joined" -> {
                val id = payload.str("user_id").orEmpty()
                if (id.isNotBlank() && _members.value.none { it.id == id }) {
                    _members.value = _members.value + JamRoomMember(
                        id = id,
                        displayName = payload.str("name") ?: "Listener",
                        role = if (payload.bool("is_host") == true) "host" else "guest",
                    )
                }
            }
            "user_left" -> {
                val id = payload.str("user_id").orEmpty()
                _members.value = _members.value.filterNot { it.id == id }
            }
            "host_changed" -> {
                val newHost = payload.str("host_id") ?: payload.str("new_host_id") ?: _hostId.value
                if (newHost.isNotBlank()) _hostId.value = newHost
                payload.str("cohost_id")?.let { _coHostId.value = it }
                refreshRoles()
            }
            "cohost_changed" -> {
                payload.str("host_id")?.takeIf(String::isNotBlank)?.let { _hostId.value = it }
                payload.str("cohost_id")?.let { _coHostId.value = it }
                refreshRoles()
            }
            "room_mode_changed" -> _roomMode.value = payload.str("room_mode") ?: "host_only"
            "station_mode" -> {
                _stationAuto.value = payload.bool("auto") ?: true
                _isHost.value = (_hostId.value == _memberId.value) || _isCoHost.value
            }
            "suggestion_received" -> {
                val track = payload["track"]?.jsonObject
                val id = payload.str("suggestion_id").orEmpty()
                if (track != null && id.isNotBlank()) {
                    val suggestion = JamTrackSuggestion(
                        suggestionId = id,
                        from = payload.str("from_user_id").orEmpty(),
                        displayName = payload.str("from_user_name") ?: "Listener",
                        title = track.str("title") ?: "Unknown track",
                        artist = track.str("artist") ?: "Unknown artist",
                        videoId = track.str("id").orEmpty(),
                        artworkUrl = track.str("artwork_url").orEmpty(),
                    )
                    if (_suggestions.value.none { it.suggestionId == id }) {
                        _suggestions.value = (_suggestions.value + suggestion).takeLast(30)
                    }
                }
            }
            "chat_message" -> appendChat(payload)
            "error" -> {
                val detail = payload.str("message") ?: "Jam server error"
                val code = payload.str("code").orEmpty()
                _connectionState.value = detail
                if (code in setOf(
                        "room_not_found", "room_full", "invalid_name", "default_name",
                        "missing_room_code", "rate_limited", "storage_unavailable",
                    )
                ) {
                    manualLeave = true
                    cancelReconnect()
                    activeIntent = null
                    clearRoomState()
                    _connectionState.value = detail
                }
            }
            "pong" -> Unit
        }

        if (type != "sync_state" && type != "sync_playback" && type != "pong") {
            appendEvent(JamEvent(type, payload))
        }
    }

    private fun refreshRoles() {
        _isCoHost.value = _coHostId.value.isNotBlank() && _coHostId.value == _memberId.value
        _isHost.value = _hostId.value == _memberId.value || _isCoHost.value
        _members.value = _members.value.map { member ->
            member.copy(
                role = when {
                    member.id == _coHostId.value -> "cohost"
                    member.id == _hostId.value -> "host"
                    else -> "guest"
                },
            )
        }
    }

    private fun parsePlayback(track: JsonObject, wrapper: JsonObject): JamPlayback {
        val duration = track.long("duration_ms") ?: 0L
        return JamPlayback(
            title = track.str("title") ?: "Unknown track",
            artist = track.str("artist") ?: "Unknown artist",
            videoId = track.str("id").orEmpty(),
            artworkUrl = track.str("artwork_url").orEmpty(),
            isPlaying = wrapper.bool("is_playing") ?: false,
            positionMs = wrapper.long("position_ms") ?: 0L,
            durationMs = duration,
            serverTimeMs = wrapper.long("server_time_ms") ?: 0L,
            revision = wrapper.long("revision") ?: 0L,
            fromUserId = wrapper.str("from_user_id").orEmpty(),
        )
    }

    private fun parseQueue(queue: kotlinx.serialization.json.JsonArray): List<JamPlayback> =
        queue.mapNotNull { item ->
            runCatching {
                val track = item.jsonObject
                JamPlayback(
                    title = track.str("title") ?: "Unknown track",
                    artist = track.str("artist") ?: "Unknown artist",
                    videoId = track.str("id").orEmpty(),
                    artworkUrl = track.str("artwork_url").orEmpty(),
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = track.long("duration_ms") ?: 0L,
                )
            }.getOrNull()
        }

    private fun appendChatHistory(history: kotlinx.serialization.json.JsonArray?) {
        history?.forEach { item ->
            runCatching { item.jsonObject }.getOrNull()?.let(::appendChat)
        }
    }

    private fun appendChat(payload: JsonObject) {
        val id = payload.str("id").orEmpty()
        if (id.isNotBlank() && _events.value.any { it.type == "chat_message" && it.payload.str("id") == id }) {
            return
        }
        appendEvent(JamEvent("chat_message", payload))
    }

    private fun appendEvent(event: JamEvent) {
        _events.value = (_events.value + event).takeLast(100)
    }

    private fun clearRoomState() {
        _roomCode.value = ""
        _memberId.value = ""
        _isHost.value = false
        _isCoHost.value = false
        _hostId.value = ""
        _coHostId.value = ""
        _roomMode.value = "host_only"
        _roomName.value = ""
        _permanent.value = false
        _radio.value = false
        _stationAuto.value = false
        _members.value = emptyList()
        _playback.value = null
        _sharedQueue.value = emptyList()
        _suggestions.value = emptyList()
        _events.value = emptyList()
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun envelope(type: String, payload: JsonObject) = buildJsonObject {
        put("type", type)
        put("payload", payload)
    }

    private fun send(type: String, payload: JsonObject): Boolean =
        socket?.send(envelope(type, payload).toString()) == true

    fun selectPlaybackAction(
        action: String,
        title: String,
        artist: String,
        videoId: String,
        artworkUrl: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (_roomCode.value.isBlank() || videoId.isBlank()) return
        send("playback_action", buildJsonObject {
            put("action", action)
            put("client_time_ms", System.currentTimeMillis())
            put("position_ms", positionMs.coerceAtLeast(0))
            put("is_playing", isPlaying)
            put("track", buildJsonObject {
                put("id", videoId.take(32))
                put("title", title.take(160))
                put("artist", artist.take(120))
                put("artwork_url", artworkUrl.take(1024))
                put("duration_ms", durationMs.coerceAtLeast(0))
            })
        })
    }

    fun publishQueue(tracks: List<com.itzfallenme.katmusic.playback.PlayableTrack>) {
        if (_roomCode.value.isBlank() || !_isHost.value || _isCoHost.value) return
        send("queue_update", buildJsonObject {
            put("queue", buildJsonArray {
                tracks.take(5).forEach { track ->
                    val id = track.videoId.orEmpty()
                    if (id.isNotBlank()) {
                        add(buildJsonObject {
                            put("id", id.take(32))
                            put("title", track.title.take(160))
                            put("artist", track.artist.take(120))
                            put("artwork_url", track.artworkUrl.orEmpty().take(1024))
                            put("duration_ms", track.durationMs ?: 0L)
                        })
                    }
                }
            })
        })
    }

    fun sendChat(text: String) {
        val safe = text.trim().take(500)
        if (safe.isEmpty() || _roomCode.value.isBlank()) return
        send("send_chat", buildJsonObject { put("text", safe) })
    }

    fun suggestTrack(title: String, artist: String, videoId: String, artworkUrl: String) {
        if (_roomCode.value.isBlank() || videoId.isBlank()) return
        send("suggest_track", buildJsonObject {
            put("track", buildJsonObject {
                put("id", videoId.take(32))
                put("title", title.take(160))
                put("artist", artist.take(120))
                put("artwork_url", artworkUrl.take(1024))
            })
        })
    }

    fun approveSuggestion(suggestionId: String) {
        if (suggestionId.isBlank()) return
        if (send("approve_suggestion", buildJsonObject { put("suggestion_id", suggestionId) })) {
            _suggestions.value = _suggestions.value.filterNot { it.suggestionId == suggestionId }
        }
    }

    fun rejectSuggestion(suggestionId: String) {
        if (suggestionId.isBlank()) return
        if (send("reject_suggestion", buildJsonObject { put("suggestion_id", suggestionId) })) {
            _suggestions.value = _suggestions.value.filterNot { it.suggestionId == suggestionId }
        }
    }

    fun setCollaborative(enabled: Boolean) {
        if (_roomCode.value.isBlank()) return
        send("change_room_mode", buildJsonObject {
            put("room_mode", if (enabled) "collaborative" else "host_only")
        })
    }

    fun grantCoHost(userId: String) {
        if (userId.isBlank()) return
        send("grant_host", buildJsonObject { put("user_id", userId) })
    }

    fun revokeCoHost(userId: String) {
        if (userId.isBlank()) return
        send("revoke_host", buildJsonObject { put("user_id", userId) })
    }

    fun leaveRoom() {
        if (_roomCode.value.isNotBlank()) {
            send("leave_room", buildJsonObject { })
        }
        manualLeave = true
        cancelReconnect()
        activeIntent = null
        clearRoomState()
        _connectionState.value = "Disconnected"
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

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
