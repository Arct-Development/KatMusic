package com.itzfallenme.katmusic.desktop.lyrics

import com.itzfallenme.katmusic.shared.lyrics.LrcParser
import com.itzfallenme.katmusic.shared.lyrics.Lyrics
import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.lyrics.LyricsResult
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Desktop lyrics from LRCLIB (https://lrclib.net), the same public,
 * community-run catalogue the Android app already uses as a provider.
 *
 * No key and no account: one lookup by artist/title/album/duration, falling
 * back to a search when the exact query misses (remasters, live versions and
 * punctuation differences are the normal case). Synced LRC is parsed by the
 * shared [LrcParser], so the highlighted line on desktop and Android comes from
 * the same code.
 */
class LrclibLyricsProvider(
    private val baseUrl: String = "https://lrclib.net/api",
) : LyricsProvider {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lyricsFor(track: Track): LyricsResult = withContext(Dispatchers.IO) {
        val durationSeconds = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        val exact = request(
            path = "/get",
            params = buildMap {
                put("artist_name", track.artist)
                put("track_name", track.title)
                track.album.takeIf { it.isNotBlank() }?.let { put("album_name", it) }
                durationSeconds?.let { put("duration", it.toString()) }
            },
        )?.toLyricsOrNull()
        if (exact != null) return@withContext LyricsResult.Found(withSource(exact))

        val byTitleAndArtist = request(
            path = "/get",
            params = mapOf("artist_name" to track.artist, "track_name" to track.title),
        )?.toLyricsOrNull()
        if (byTitleAndArtist != null) return@withContext LyricsResult.Found(withSource(byTitleAndArtist))

        val search = request(
            path = "/search",
            params = mapOf("q" to "${track.title} ${track.artist}".trim()),
        )
        val best = (search as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { it.toLyricsOrNull() }
            ?.firstOrNull()
        if (best != null) LyricsResult.Found(withSource(best)) else LyricsResult.NotFound
    }

    private fun withSource(lyrics: Lyrics): Lyrics = lyrics.copy(source = "LRCLIB")

    private fun request(path: String, params: Map<String, String>): kotlinx.serialization.json.JsonElement? {
        val query = params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
            }
        val request = HttpRequest.newBuilder(URI.create("$baseUrl$path?$query"))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", "KatMusic/2.6.4 (https://github.com/Arct-Development/KatMusic)")
            .header("Accept", "application/json")
            .GET()
            .build()
        return runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return null
            json.parseToJsonElement(response.body())
        }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonElement.toLyricsOrNull(): Lyrics? {
        val obj = runCatching { jsonObject }.getOrNull() ?: return null
        if (obj["instrumental"]?.jsonPrimitive?.contentOrNull == "true") {
            return Lyrics(lines = emptyList(), isInstrumental = true)
        }
        val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
        val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
        return when {
            !synced.isNullOrBlank() -> {
                val lines = LrcParser.parse(synced)
                if (lines.isEmpty()) null else Lyrics(lines = lines, plain = plain)
            }
            !plain.isNullOrBlank() -> Lyrics(
                lines = LrcParser.fromPlainText(plain),
                plain = plain,
            )
            else -> null
        }
    }
}
