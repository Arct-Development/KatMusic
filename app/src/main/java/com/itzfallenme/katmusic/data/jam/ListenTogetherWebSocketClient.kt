package com.itzfallenme.katmusic.data.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class JamRoomMember(val id: String, val displayName: String, val role: String)
data class JamPlayback(val title: String, val artist: String, val videoId: String, val artworkUrl: String, val isPlaying: Boolean, val positionMs: Long, val durationMs: Long)
data class JamTrackSuggestion(val from: String, val displayName: String, val title: String, val artist: String, val videoId: String, val artworkUrl: String)

data class JamJoinRequest(
    val requestId: String,
    val memberId: String,
    val displayName: String,
)

class ListenTogetherWebSocketClient : Closeable {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

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
        connect(endpoint, buildJsonObject {
            put("type", "create_room")
            put("displayName", displayName.trim().take(32))
        })
    }

    fun joinRoom(endpoint: String, code: String, displayName: String) {
        connect(endpoint, buildJsonObject {
            put("type", "join_room")
            put("roomCode", code.trim().uppercase().take(8))
            put("displayName", displayName.trim().take(32))
        })
    }

    fun reconnect(endpoint: String, code: String, token: String, displayName: String) {
        connect(endpoint, buildJsonObject {
            put("type", "reconnect")
            put("roomCode", code)
            put("sessionToken", token)
            put("displayName", displayName.trim().take(32))
        })
    }

    private fun connect(endpoint: String, firstMessage: JsonObject) {
        val normalized = endpoint.trim()
        if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
            _connectionState.value = "Server URL must start with wss:// or ws://"
            return
        }
        socket?.close(1000, "reconnecting")
        _connectionState.value = "Connecting…"
        _events.value = emptyList()
        val request = runCatching { Request.Builder().url(normalized).build() }.getOrElse {
            _connectionState.value = "Invalid server URL"
            return
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = "Connected"
                webSocket.send(json.encodeToString(JsonObject.serializer(), firstMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                val type = message["type"]?.jsonPrimitive?.contentOrNull ?: "message"
                val code = message["roomCode"]?.jsonPrimitive?.contentOrNull
                if (!code.isNullOrBlank()) _roomCode.value = code
                val token = message["sessionToken"]?.jsonPrimitive?.contentOrNull
                if (!token.isNullOrBlank()) _sessionToken.value = token
                if (type == "room_created" || type == "room_joined" || type == "room_reconnected") {
                    message["room"]?.jsonObject?.let(::applyRoomSnapshot)
                }
                when (type) {
                    "room_created", "room_joined", "room_reconnected" -> {
                        message["memberId"]?.jsonPrimitive?.contentOrNull?.let { _memberId.value = it }
                    }
                }
                when (type) {
                    "room_created", "room_reconnected" -> _isHost.value =
                        message["role"]?.jsonPrimitive?.contentOrNull == "host" ||
                            (type == "room_created")
                    "room_joined" -> _isHost.value = false
                    "join_request" -> {
                        val request = JamJoinRequest(
                            requestId = message["requestId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            memberId = message["memberId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            displayName = message["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener",
                        )
                        _pendingRequests.value = _pendingRequests.value + request
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
                                _members.value = _members.value + JamRoomMember(id, member["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener", member["role"]?.jsonPrimitive?.contentOrNull ?: "guest")
                            }
                        }
                    }
                    "member_left" -> {
                        val id = message["memberId"]?.jsonPrimitive?.contentOrNull
                        _members.value = _members.value.filterNot { it.id == id }
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
                    "room_left", "join_denied" -> {
                        _memberId.value = ""
                        _roomCode.value = ""
                        _sessionToken.value = ""
                        _isHost.value = false
                        _members.value = emptyList()
                        _playback.value = null
                        _sharedQueue.value = emptyList()
                        _suggestions.value = emptyList()
                    }
                    "error" -> {
                        val detail = message["message"]?.jsonPrimitive?.contentOrNull
                        if (!detail.isNullOrBlank()) _connectionState.value = detail
                    }
                }
                if (type == "member_joined" || type == "member_left" || type == "host_changed") {
                    // Keep the event stream bounded; a long-running room must not grow memory forever.
                }
                _events.value = (_events.value + JamEvent(type, message)).takeLast(100)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = "Closing"
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = "Disconnected"
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = t.message?.take(120) ?: "Connection failed"
            }
        })
    }

    private fun applyRoomSnapshot(room: JsonObject) {
        _members.value = room["members"]?.jsonArray?.mapNotNull { item ->
            runCatching {
                val member = item.jsonObject
                JamRoomMember(
                    id = member["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    displayName = member["displayName"]?.jsonPrimitive?.contentOrNull ?: "Listener",
                    role = member["role"]?.jsonPrimitive?.contentOrNull ?: "guest",
                )
            }.getOrNull()
        }.orEmpty()
        _playback.value = room["playback"]?.jsonObject?.let { runCatching { it.toJamPlayback() }.getOrNull() }
        _sharedQueue.value = room["queue"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject.toJamPlayback() }.getOrNull() }.orEmpty()
    }

    private fun JsonObject.toJamPlayback(): JamPlayback {
        val track = this["track"]?.jsonObject ?: this
        return JamPlayback(
            title = track["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown track",
            artist = track["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown artist",
            videoId = track["videoId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artworkUrl = track["artworkUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isPlaying = this["isPlaying"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
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
        if (safe.isNotEmpty()) send(buildJsonObject {
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
        send(buildJsonObject {
            put("type", "leave_room")
            put("roomCode", _roomCode.value)
        })
        _roomCode.value = ""
        _sessionToken.value = ""
        _isHost.value = false
        _pendingRequests.value = emptyList()
        _members.value = emptyList()
        _playback.value = null
        _sharedQueue.value = emptyList()
        _suggestions.value = emptyList()
    }

    fun dismissSuggestion(videoId: String) {
        _suggestions.value = _suggestions.value.filterNot { it.videoId == videoId }
    }

    override fun close() {
        socket?.close(1000, "screen closed")
        socket = null
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
        _connectionState.value = "Disconnected"
    }
}
