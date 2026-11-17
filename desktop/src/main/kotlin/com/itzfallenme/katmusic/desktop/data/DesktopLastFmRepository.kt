package com.itzfallenme.katmusic.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.prefs.Preferences
import com.sun.net.httpserver.HttpServer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LastFmFriend(
    val username: String,
    val realName: String,
    val profileUrl: String,
    val avatarUrl: String?,
)

data class LastFmRecentTrack(
    val title: String,
    val artist: String,
    val album: String,
    val profileUrl: String?,
    val nowPlaying: Boolean,
)

data class LastFmConnection(
    val username: String = "",
    val connected: Boolean = false,
)

/**
 * Desktop's optional Last.fm client. Credentials/session and retry queue live
 * only in this OS user's preferences; no KatMusic service receives them.
 */
class DesktopLastFmRepository(
    private val prefs: Preferences = Preferences.userRoot().node("com/itzfallenme/katmusic/lastfm"),
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val writeMutex = Mutex()
    private val _connection = MutableStateFlow(connectionSnapshot())
    val connection: StateFlow<LastFmConnection> = _connection.asStateFlow()

    val username: String get() = prefs.get("username", "")
    val isConnected: Boolean get() = username.isNotBlank() && prefs.get("sessionKey", "").isNotBlank()
    val apiKey: String get() = prefs.get("apiKey", "")
    val hasCredentials: Boolean get() = normalizeKey(apiKey).isNotBlank() &&
        normalizeKey(prefs.get("apiSecret", "")).isNotBlank()

    fun saveApiCredentials(apiKey: String, apiSecret: String) {
        prefs.put("apiKey", normalizeKey(apiKey))
        prefs.put("apiSecret", normalizeKey(apiSecret))
        flush()
    }

    fun disconnect() {
        prefs.remove("username")
        prefs.remove("sessionKey")
        flush()
        _connection.value = connectionSnapshot()
    }

    suspend fun connectWithBrowser(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = normalizeKey(this@DesktopLastFmRepository.apiKey)
            val apiSecret = normalizeKey(prefs.get("apiSecret", ""))
            require(apiKey.length == 32 && apiSecret.length == 32) {
                "Enter your 32-character Last.fm API key and shared secret first."
            }

            val token = unsignedGet("auth.getToken", apiKey, emptyMap())
                .stringField("token")
            val callback = CompletableFuture<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/lastfm/callback") { exchange ->
                val params = parseQuery(exchange.requestURI.rawQuery.orEmpty())
                val returnedToken = params["token"].orEmpty()
                val tokenMatches = returnedToken == token
                val body = if (!tokenMatches) {
                    "<html><body><h2>KatMusic could not read the Last.fm approval.</h2>You can close this tab.</body></html>"
                } else {
                    "<html><body><h2>Last.fm connected to KatMusic.</h2>You can return to the app.</body></html>"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                if (!tokenMatches) callback.completeExceptionally(IllegalStateException("Last.fm returned an invalid auth token"))
                else callback.complete(returnedToken)
            }
            server.executor = null
            server.start()
            try {
                val callbackUri = "http://127.0.0.1:${server.address.port}/lastfm/callback"
                val authorizeUri = "https://www.last.fm/api/auth/?api_key=${enc(apiKey)}" +
                    "&token=${enc(token)}&cb=${enc(callbackUri)}"
                require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    "This system cannot open a browser. Use a desktop environment with browser support."
                }
                Desktop.getDesktop().browse(URI(authorizeUri))
                val authorizedToken = callback.await(TOKEN_TIMEOUT_MS)
                    ?: error("Last.fm approval timed out. Try connecting again.")
                val params = mapOf(
                    "method" to "auth.getSession",
                    "token" to authorizedToken,
                    "api_key" to apiKey,
                )
                val sessionJson = signedPost(params, apiSecret)
                val session = sessionJson["session"]?.jsonObject
                    ?: error("Last.fm did not return a session")
                val sessionKey = session["key"]?.jsonPrimitive?.content.orEmpty()
                val sessionUsername = session["name"]?.jsonPrimitive?.content.orEmpty()
                require(sessionKey.isNotBlank() && sessionUsername.isNotBlank()) {
                    "Last.fm did not return a session key"
                }
                prefs.put("username", sessionUsername)
                prefs.put("sessionKey", sessionKey)
                flush()
                _connection.value = connectionSnapshot()
                sessionUsername
            } finally {
                server.stop(0)
            }
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
    }

    suspend fun updateNowPlaying(artist: String, title: String, album: String?): Result<Unit> =
        authenticatedWrite(
            "track.updateNowPlaying",
            trackParams(artist, title, album),
        )

    suspend fun scrobble(
        artist: String,
        title: String,
        album: String?,
        timestampSeconds: Long,
    ): Result<Unit> = authenticatedWrite(
        "track.scrobble",
        trackParams(artist, title, album) + ("timestamp" to timestampSeconds.toString()),
    )

    suspend fun loadFriends(limit: Int = 50): Result<List<LastFmFriend>> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConnected) { "Connect Last.fm to view your friends." }
            val apiKey = normalizeKey(prefs.get("apiKey", ""))
            val response = unsignedGet(
                "user.getFriends",
                apiKey,
                mapOf("user" to username, "limit" to limit.coerceIn(1, 200).toString(), "recenttracks" to "0"),
            )
            val friends = response["friends"]?.jsonObject
            val users = friends?.get("user")?.let { element ->
                when (element) {
                    is kotlinx.serialization.json.JsonArray -> element
                    is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonArray(listOf(element))
                    else -> kotlinx.serialization.json.JsonArray(emptyList())
                }
            } ?: kotlinx.serialization.json.JsonArray(emptyList())
            users.mapNotNull { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.content.orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val images = item["image"] as? kotlinx.serialization.json.JsonArray
                val avatar = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.content
                    ?.takeIf(String::isNotBlank)
                LastFmFriend(
                    username = name,
                    realName = item["realname"]?.jsonPrimitive?.content.orEmpty(),
                    profileUrl = item["url"]?.jsonPrimitive?.content
                        ?.takeIf(String::isNotBlank) ?: "https://www.last.fm/user/${enc(name)}",
                    avatarUrl = avatar,
                )
            }
        }
    }

    suspend fun loadTopArtists(
        user: String = username,
        limit: Int = 20,
    ): Result<List<Pair<String, Int>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConnected) { "Connect Last.fm to view your listening profile." }
                require(user.isNotBlank()) { "Enter a Last.fm username." }
                val response = unsignedGet(
                    "user.getTopArtists",
                    normalizeKey(prefs.get("apiKey", "")),
                    mapOf("user" to user, "limit" to limit.coerceIn(1, 100).toString(), "period" to "overall"),
                )
                val artists = response["topartists"]?.jsonObject?.get("artist")
                val list = when (artists) {
                    is kotlinx.serialization.json.JsonArray -> artists
                    is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonArray(listOf(artists))
                    else -> kotlinx.serialization.json.JsonArray(emptyList())
                }
                list.mapNotNull { element ->
                    val item = element.jsonObject
                    val name = item["name"]?.jsonPrimitive?.content.orEmpty()
                    val playcount = item["playcount"]?.jsonPrimitive?.intOrNull ?: 0
                    name.takeIf(String::isNotBlank)?.let { it to playcount }
                }
            }
        }

    suspend fun loadRecentTracks(user: String = username, limit: Int = 20): Result<List<LastFmRecentTrack>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConnected) { "Connect Last.fm to view listening activity." }
                require(user.isNotBlank()) { "Enter a Last.fm username." }
                val response = unsignedGet(
                    "user.getRecentTracks",
                    normalizeKey(prefs.get("apiKey", "")),
                    mapOf("user" to user, "limit" to limit.coerceIn(1, 100).toString()),
                )
                val tracks = response["recenttracks"]?.jsonObject?.get("track")
                val list = when (tracks) {
                    is kotlinx.serialization.json.JsonArray -> tracks
                    is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonArray(listOf(tracks))
                    else -> kotlinx.serialization.json.JsonArray(emptyList())
                }
                list.mapNotNull { element ->
                    val item = element.jsonObject
                    val title = item["name"]?.jsonPrimitive?.content.orEmpty()
                    if (title.isBlank()) return@mapNotNull null
                    val artistValue = item["artist"]
                    val artist = when (artistValue) {
                        is JsonObject -> artistValue["#text"]?.jsonPrimitive?.content.orEmpty()
                        else -> artistValue?.jsonPrimitive?.content.orEmpty()
                    }
                    val album = when (val albumValue = item["album"]) {
                        is JsonObject -> albumValue["#text"]?.jsonPrimitive?.content.orEmpty()
                        else -> albumValue?.jsonPrimitive?.content.orEmpty()
                    }
                    val attr = item["@attr"] as? JsonObject
                    LastFmRecentTrack(
                        title = title,
                        artist = artist,
                        album = album,
                        profileUrl = item["url"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
                        nowPlaying = attr?.get("nowplaying")?.jsonPrimitive?.content == "true",
                    )
                }
            }
        }

    private suspend fun authenticatedWrite(method: String, extras: Map<String, String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                runCatching {
                    require(isConnected) { "Connect Last.fm in Settings to enable scrobbling." }
                    val apiKey = normalizeKey(prefs.get("apiKey", ""))
                    val apiSecret = normalizeKey(prefs.get("apiSecret", ""))
                    signedPost(
                        extras + mapOf(
                            "method" to method,
                            "api_key" to apiKey,
                            "sk" to prefs.get("sessionKey", ""),
                        ),
                        apiSecret,
                    )
                    Unit
                }
            }
        }

    private fun unsignedGet(
        method: String,
        apiKey: String,
        extras: Map<String, String>,
    ): JsonObject {
        val params = extras + mapOf("method" to method, "api_key" to apiKey, "format" to "json")
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val request = HttpRequest.newBuilder(URI("https://ws.audioscrobbler.com/2.0/?$query"))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        return send(request)
    }

    private fun signedPost(params: Map<String, String>, apiSecret: String): JsonObject {
        val signature = sign(params, apiSecret)
        val form = (params + mapOf("api_sig" to signature, "format" to "json"))
            .entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val request = HttpRequest.newBuilder(URI("https://ws.audioscrobbler.com/2.0/"))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        return send(request)
    }

    private fun send(request: HttpRequest): JsonObject {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "Last.fm request failed (HTTP ${response.statusCode()})" }
        val root = json.parseToJsonElement(response.body()).jsonObject
        val errorCode = root["error"]?.jsonPrimitive?.intOrNull
        if (errorCode != null) {
            val message = root["message"]?.jsonPrimitive?.content.orEmpty()
            error("Last.fm error $errorCode${message.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}")
        }
        return root
    }

    private fun JsonObject.stringField(name: String): String =
        this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Last.fm response did not include $name")

    private fun trackParams(artist: String, title: String, album: String?): Map<String, String> =
        buildMap {
            put("artist", artist)
            put("track", title)
            album?.takeIf(String::isNotBlank)?.let { put("album", it) }
        }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }.toMap()

    private suspend fun CompletableFuture<String>.await(timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                whenComplete { value, error ->
                    if (!continuation.isActive) return@whenComplete
                    if (error != null) continuation.resumeWithException(error)
                    else continuation.resume(value)
                }
                continuation.invokeOnCancellation { cancel(true) }
            }
        }

    private fun normalizeKey(value: String): String = value
        .filter { it.digitToIntOrNull(16) != null }
        .lowercase()

    private fun sign(params: Map<String, String>, secret: String): String {
        val source = params.toSortedMap()
            .entries.joinToString(separator = "") { (key, value) -> key + value } + secret
        return MessageDigest.getInstance("MD5")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun flush() = runCatching { prefs.flush() }

    private fun connectionSnapshot() = LastFmConnection(
        username = prefs.get("username", ""),
        connected = prefs.get("username", "").isNotBlank() && prefs.get("sessionKey", "").isNotBlank(),
    )

    private companion object {
        const val USER_AGENT = "KatMusic Desktop"
        const val TOKEN_TIMEOUT_MS = 180_000L
    }
}
