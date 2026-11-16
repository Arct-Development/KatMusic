package com.itzfallenme.katmusic.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Desktop feed/streaming fallback over the Piped public API.
 *
 * Verified live (2026-11-16): `/trending` and `/search?filter=music_songs`
 * return real data on api.piped.private.coffee and pipedapi.ducks.party.
 * `/streams/{id}` currently answers 500 with YouTube's `LOGIN_REQUIRED`
 * bot-check on those instances; desktop playback resolution is primarily
 * [NewPipeStreamResolver] (SimpMusic's extractor method), and this client's
 * [resolveStream] walks every instance and fails honestly when none returns
 * playable audio.
 */
class PipedClient(
    private val instances: List<String> = DEFAULT_INSTANCES,
    private val region: String = "US",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // region response models

    @Serializable
    data class TrendingItem(
        val url: String? = null,
        val title: String? = null,
        val thumbnail: String? = null,
        val uploaderName: String? = null,
        val duration: Long = -1L,
        val type: String? = null,
    )

    @Serializable
    data class SearchResponse(val items: List<SearchItem> = emptyList())

    @Serializable
    data class SearchItem(
        val url: String? = null,
        val type: String? = null,
        val title: String? = null,
        val thumbnail: String? = null,
        val uploaderName: String? = null,
        val duration: Long = -1L,
    )

    @Serializable
    data class StreamsResponse(
        val title: String? = null,
        val description: String? = null,
        val uploader: String? = null,
        val thumbnailUrl: String? = null,
        val duration: Long = -1L,
        val audioStreams: List<AudioStream> = emptyList(),
    ) {
        @Serializable
        data class AudioStream(
            val url: String? = null,
            val format: String? = null,
            val quality: String? = null,
            val mimeType: String? = null,
            val bitrate: Long = 0L,
            val contentLength: Long = 0L,
        )
    }

    data class FeedTrack(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnail: String?,
        val durationMs: Long,
    )

    class PipedError(override val message: String) : Exception(message)

    // endregion

    /** Trending videos for the configured region, first instance that answers. */
    suspend fun trending(): Result<List<FeedTrack>> = withContext(Dispatchers.IO) {
        getFirst { instance ->
            val body = fetchBody("https://$instance/trending?region=$region")
            json.decodeFromString<List<TrendingItem>>(body)
        }.map { items ->
            items.filter { it.type == "stream" }.mapNotNull { it.toFeedTrack() }
        }
    }

    /** Song-filtered YouTube Music search (Piped `music_songs` filter). */
    suspend fun searchMusic(query: String): Result<List<FeedTrack>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8)
        getFirst { instance ->
            val body = fetchBody("https://$instance/search?q=$q&filter=music_songs")
            json.decodeFromString<SearchResponse>(body).items
        }.map { items ->
            items.mapNotNull { it.toFeedTrack() }
        }
    }

    /**
     * Resolves one playable audio URL for [videoId] (highest-bitrate stream
     * that Java Sound or mpv can open). Every instance is tried; the error
     * reports the provider block when all refuse.
     */
    suspend fun resolveStream(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        var lastError = "No Piped instance reachable"
        for (instance in instances) {
            try {
                val body = fetchBody("https://$instance/streams/$videoId", timeoutSeconds = 15)
                val streams = json.decodeFromString<StreamsResponse>(body)
                val best = streams.audioStreams
                    .filter { !it.url.isNullOrBlank() }
                    .filter { it.mimeType?.contains("opus") == true || it.mimeType?.contains("mp4") == true }
                    .maxByOrNull { it.bitrate }
                if (best?.url != null) {
                    return@withContext Result.success(deproxify(best.url))
                }
                lastError = "Instance $instance returned no audio streams"
            } catch (error: Exception) {
                lastError = describeError(instance, error)
            }
        }
        Result.failure(PipedError(lastError))
    }

    /** Forces any proxy-prefixed URL back to its direct googlevideo origin. */
    internal fun deproxify(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url
        if (!(host.startsWith("pipedproxy") || host.contains("proxy"))) return url
        val query = uri.rawQuery ?: return url
        val realHost = query.split("&")
            .firstOrNull { it.startsWith("host=") }
            ?.substringAfter("host=")
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?: return url
        val cleanQuery = query.split("&").filterNot { it.startsWith("host=") }.joinToString("&")
        return buildString {
            append("https://").append(realHost).append(uri.path ?: "")
            if (cleanQuery.isNotEmpty()) append('?').append(cleanQuery)
        }
    }

    private suspend fun <T> getFirst(request: suspend (String) -> T): Result<T> {
        var lastError = "No Piped instance reachable"
        for (instance in instances) {
            try {
                return Result.success(request(instance))
            } catch (error: Exception) {
                lastError = describeError(instance, error)
            }
        }
        return Result.failure(PipedError(lastError))
    }

    private fun describeError(instance: String, error: Exception): String {
        val message = error.message ?: ""
        return when {
            message.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                message.contains("Sign in to confirm", ignoreCase = true) ->
                "$instance: YouTube requires sign-in (bot check) — anonymous playback blocked"
            else -> "$instance: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun fetchBody(url: String, timeoutSeconds: Long = 10): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw PipedError("HTTP ${response.statusCode()}: ${response.body().take(240)}")
        }
        return response.body()
    }

    private fun TrendingItem.toFeedTrack(): FeedTrack? {
        val id = videoIdFromUrl(url ?: return null) ?: return null
        return FeedTrack(
            videoId = id,
            title = title.orEmpty().ifBlank { "Untitled" },
            artist = uploaderName.orEmpty().ifBlank { "Unknown" },
            thumbnail = thumbnail,
            durationMs = duration.coerceAtLeast(0L) * 1000L,
        )
    }

    private fun SearchItem.toFeedTrack(): FeedTrack? {
        if (type != null && type != "stream") return null
        val id = videoIdFromUrl(url ?: return null) ?: return null
        return FeedTrack(
            videoId = id,
            title = title.orEmpty().ifBlank { "Untitled" },
            artist = uploaderName.orEmpty().ifBlank { "Unknown" },
            thumbnail = thumbnail,
            durationMs = duration.coerceAtLeast(0L) * 1000L,
        )
    }

    internal fun videoIdFromUrl(url: String): String? {
        val id = url.substringAfter("watch?v=", "")
        return id.takeIf { it.length == 11 && it.none { c -> c == '&' } }?.substringBefore('&')
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) KatMusic/2.6"

        /** Instances verified live in development; first that answers wins. */
        val DEFAULT_INSTANCES = listOf(
            "api.piped.private.coffee",
            "pipedapi.ducks.party",
            "pipedapi.adminforge.de",
            "pipedapi.kavin.rocks",
        )
    }
}
