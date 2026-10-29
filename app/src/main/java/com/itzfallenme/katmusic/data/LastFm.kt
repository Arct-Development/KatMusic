package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Minimal Last.fm client: auth, now-playing and scrobbling.
 * https://www.last.fm/api/show/track.scrobble
 */
class LastFm(private val apiKey: String, private val apiSecret: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val endpoint = "https://ws.audioscrobbler.com/2.0/"

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun signature(params: Map<String, String>): String {
        val payload = params.entries
            .filter { it.key != "format" && it.key != "callback" && it.key != "api_sig" }
            .sortedBy { it.key }
            .joinToString("") { it.key + it.value }
        return md5(payload + apiSecret)
    }

    private suspend fun call(params: Map<String, String>): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val all = params + ("api_key" to apiKey) + ("format" to "json")
            val signed = all + ("api_sig" to signature(all))
            val form = FormBody.Builder().apply { signed.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder().url(endpoint).post(form).build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) null else json.parseToJsonElement(body).jsonObject
            }
        }.getOrNull()
    }

    suspend fun requestToken(): String? {
        val result = call(mapOf("method" to "auth.getToken"))
        return result?.get("token")?.jsonPrimitive?.content
    }

    fun authUrl(token: String) =
        "https://www.last.fm/api/auth/?api_key=$apiKey&token=$token"

    suspend fun createSession(token: String): Pair<String, String>? {
        val result = call(mapOf("method" to "auth.getSession", "token" to token)) ?: return null
        val session = result["session"]?.jsonObject ?: return null
        val key = session["key"]?.jsonPrimitive?.content ?: return null
        val name = session["name"]?.jsonPrimitive?.content.orEmpty()
        return name to key
    }

    /** Top tags for an artist, or for one of its tracks, used as genre labels. */
    suspend fun topTags(artist: String, track: String? = null): List<String> {
        val params = if (track.isNullOrBlank()) {
            mapOf("method" to "artist.getTopTags", "artist" to artist, "autocorrect" to "1")
        } else {
            mapOf(
                "method" to "track.getInfo",
                "artist" to artist,
                "track" to track,
                "autocorrect" to "1"
            )
        }
        val root = call(params) ?: return emptyList()
        val container = if (track.isNullOrBlank()) {
            root["toptags"]?.jsonObject
        } else {
            root["track"]?.jsonObject?.get("toptags")?.jsonObject
        } ?: return emptyList()
        val tags = container["tag"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return tags.mapNotNull { entry ->
            (entry as? JsonObject)?.get("name")?.jsonPrimitive?.content
        }.filter { it.isNotBlank() }
    }

    suspend fun updateNowPlaying(track: Track, sessionKey: String): Boolean {
        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist.ifBlank { "Unknown" },
            "track" to track.title,
            "sk" to sessionKey
        )
        if (!track.album.isNullOrBlank()) params["album"] = track.album
        if (track.durationMs > 0) params["duration"] = (track.durationMs / 1000).toString()
        val result = call(params)
        return result != null && result.containsKey("nowplaying")
    }

    suspend fun scrobble(track: Track, timestampMs: Long, sessionKey: String): Boolean {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "artist" to track.artist.ifBlank { "Unknown" },
            "track" to track.title,
            "timestamp" to (timestampMs / 1000).toString(),
            "sk" to sessionKey
        )
        if (!track.album.isNullOrBlank()) params["album"] = track.album
        if (track.durationMs > 0) params["duration"] = (track.durationMs / 1000).toString()
        val result = call(params)
        return result?.containsKey("scrobbles") == true
    }
}
