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
        val relatedStreams: List<PlaylistItem> = emptyList(),
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

    /**
     * Trending videos for [region] (ISO 3166-1 alpha-2), first instance that
     * answers. Falls back to the region configured at construction time.
     */
    suspend fun trending(region: String = this.region): Result<List<FeedTrack>> = withContext(Dispatchers.IO) {
        val code = region.trim().uppercase().takeIf { REGION.matches(it) } ?: this@PipedClient.region
        getFirst { instance ->
            val body = fetchBody("https://$instance/trending?region=${URLEncoder.encode(code, Charsets.UTF_8)}")
            json.decodeFromString<List<TrendingItem>>(body)
        }.map { items ->
            items.filter { it.type == "stream" }.mapNotNull { it.toFeedTrack() }
        }
    }

    /**
     * Builds an artist-style radio station from a seed video: Piped returns
     * related streams with the stream metadata, so the result is the same
     * "more like this" set the provider itself recommends. The seed video is
     * removed so the station never repeats the track that started it.
     */
    suspend fun radio(seedVideoId: String, limit: Int = 30): Result<List<FeedTrack>> = withContext(Dispatchers.IO) {
        val id = seedVideoId.takeIf { YOUTUBE_ID.matches(it) }
            ?: return@withContext Result.failure(PipedError("Invalid YouTube video id"))
        getFirst { instance ->
            val body = fetchBody("https://$instance/streams/$id", timeoutSeconds = 20)
            json.decodeFromString<StreamsResponse>(body).relatedStreams
        }.map { related ->
            related.asSequence()
                .mapNotNull { it.toFeedTrack() }
                .filter { it.videoId != id }
                .distinctBy { it.videoId }
                .take(limit)
                .toList()
        }
    }

    @Serializable
    data class PlaylistResponse(
        val name: String? = null,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val uploader: String? = null,
        val relatedStreams: List<PlaylistItem> = emptyList(),
    )

    @Serializable
    data class PlaylistItem(
        val url: String? = null,
        val type: String? = null,
        val title: String? = null,
        val thumbnail: String? = null,
        val uploaderName: String? = null,
        val duration: Long = -1L,
    )

    data class PlaylistInfo(
        val id: String,
        val title: String,
        val owner: String,
        val artwork: String?,
        val tracks: List<FeedTrack>,
    )

    data class PlaylistSummary(
        val id: String,
        val title: String,
        val owner: String,
        val artwork: String?,
    )

    @Serializable
    data class ChannelResponse(
        val id: String? = null,
        val name: String? = null,
        val description: String? = null,
        val avatarUrl: String? = null,
        val bannerUrl: String? = null,
        val subscriberCount: Long = 0L,
        val relatedStreams: List<PlaylistItem> = emptyList(),
    )

    data class ArtistSummary(
        val id: String,
        val name: String,
        val artwork: String?,
        val subscribers: Long = 0L,
    )

    data class ArtistInfo(
        val id: String,
        val name: String,
        val description: String,
        val artwork: String?,
        val banner: String?,
        val subscribers: Long,
        val tracks: List<FeedTrack>,
    )

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

    /** YouTube Music album search, represented by its underlying playlist ID. */
    suspend fun searchAlbums(query: String): Result<List<PlaylistSummary>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8)
        getFirst { instance ->
            val body = fetchBody("https://$instance/search?q=$q&filter=music_albums")
            json.decodeFromString<SearchResponse>(body).items
        }.map { items ->
            items.mapNotNull { item ->
                val id = playlistIdFromSearchUrl(item.url ?: return@mapNotNull null) ?: return@mapNotNull null
                PlaylistSummary(
                    id = id,
                    title = item.title.orEmpty().ifBlank { "Untitled album" },
                    owner = item.uploaderName.orEmpty(),
                    artwork = item.thumbnail,
                )
            }.distinctBy { it.id }
        }
    }

    /** Searches YouTube Music's artist/channel category. */
    suspend fun searchArtists(query: String): Result<List<ArtistSummary>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8)
        getFirst { instance ->
            val body = fetchBody("https://$instance/search?q=$q&filter=music_artists")
            json.decodeFromString<SearchResponse>(body).items
        }.map { items ->
            items.mapNotNull { item ->
                val channelId = channelIdFromUrl(item.url ?: return@mapNotNull null) ?: return@mapNotNull null
                ArtistSummary(
                    id = channelId,
                    name = item.title.orEmpty().ifBlank { item.uploaderName.orEmpty() }.ifBlank { "Unknown artist" },
                    artwork = item.thumbnail,
                )
            }.distinctBy { it.id }
        }
    }

    /** Artist/channel details and a first page of recent tracks from Piped. */
    suspend fun artist(channelId: String): Result<ArtistInfo> = withContext(Dispatchers.IO) {
        val id = channelId.takeIf { CHANNEL_ID.matches(it) }
            ?: return@withContext Result.failure(PipedError("Invalid YouTube Music artist id"))
        getFirst { instance ->
            val body = fetchBody("https://$instance/channel/${URLEncoder.encode(id, Charsets.UTF_8)}")
            json.decodeFromString<ChannelResponse>(body)
        }.map { response ->
            ArtistInfo(
                id = response.id.orEmpty().ifBlank { id },
                name = response.name.orEmpty().ifBlank { "Artist" },
                description = response.description.orEmpty(),
                artwork = response.avatarUrl,
                banner = response.bannerUrl,
                subscribers = response.subscriberCount.coerceAtLeast(0L),
                tracks = response.relatedStreams.mapNotNull { it.toFeedTrack() },
            )
        }
    }

    internal fun channelIdFromUrl(value: String): String? {
        val source = when {
            value.startsWith('/') -> "https://www.youtube.com$value"
            "://" !in value -> "https://www.youtube.com/${value.trimStart('/')}"
            else -> value
        }
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val id = parts.getOrNull(parts.indexOfLast { it == "channel" } + 1) ?: return null
        return id.takeIf(CHANNEL_ID::matches)
    }

    /** Fetches a public YouTube playlist through a configured Piped instance. */
    suspend fun playlist(input: String): Result<PlaylistInfo> = withContext(Dispatchers.IO) {
        val playlistId = playlistIdFromInput(input)
            ?: return@withContext Result.failure(PipedError("Enter a YouTube playlist URL or playlist ID"))
        getFirst { instance ->
            val body = fetchBody("https://$instance/playlists/${URLEncoder.encode(playlistId, Charsets.UTF_8)}")
            json.decodeFromString<PlaylistResponse>(body)
        }.map { response ->
            PlaylistInfo(
                id = playlistId,
                title = response.name.orEmpty().ifBlank { response.title.orEmpty() }.ifBlank { "YouTube playlist" },
                owner = response.uploader.orEmpty(),
                artwork = response.thumbnailUrl,
                tracks = response.relatedStreams.mapNotNull { it.toFeedTrack() },
            )
        }
    }

    internal fun playlistIdFromInput(input: String): String? {
        val value = input.trim()
        if (value.matches(Regex("[A-Za-z0-9_-]{10,100}"))) return value
        val uri = runCatching {
            URI(if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value")
        }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val youtubeHost = host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        if (host != "youtu.be" && !youtubeHost) {
            return null
        }
        return uri.rawQuery.orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("list=") }
            ?.substringAfter("list=")
            ?.substringBefore('&')
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,100}")) }
    }

    private fun playlistIdFromSearchUrl(value: String): String? {
        val url = if (value.startsWith('/')) "https://www.youtube.com$value" else value
        return playlistIdFromInput(url)
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

    private fun PlaylistItem.toFeedTrack(): FeedTrack? {
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
        val source = when {
            url.startsWith('/') -> "https://www.youtube.com$url"
            "://" !in url -> "https://www.youtube.com/${url.trimStart('/')}"
            else -> url
        }
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val id = when {
            host == "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
            host == "youtube.com" || host.endsWith(".youtube.com") ->
                uri.rawQuery.orEmpty().split('&')
                    .firstOrNull { it.startsWith("v=") }
                    ?.substringAfter("v=")
            else -> null
        }
        return id?.takeIf { YOUTUBE_ID.matches(it) }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) KatMusic/2.6"
        private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
        private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
        private val REGION = Regex("[A-Z]{2}")

        /** Instances verified live in development; first that answers wins. */
        val DEFAULT_INSTANCES = listOf(
            "api.piped.private.coffee",
            "pipedapi.ducks.party",
            "pipedapi.adminforge.de",
            "pipedapi.kavin.rocks",
        )
    }
}
