package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resolves playable audio URLs.
 *
 * Raw InnerTube player responses increasingly hand back signed or throttled URLs that
 * ExoPlayer cannot open, so the primary path is NewPipeExtractor - the same approach
 * the LastWave client uses. It handles the signature/n-parameter work that a plain
 * `player` request skips, and every candidate URL is probed before playback starts.
 * The InnerTube client remains as a fallback for the cases where NewPipe is slow or
 * the extraction service is rate-limited.
 */
object StreamResolver {

    private const val PROBE_TIMEOUT_MS = 2_000L
    private const val MAX_PROBED = 3
    private const val CACHE_TTL_MS = 40 * 60 * 1000L

    private val http: OkHttpClient by lazy {
        InnerTube.http.newBuilder()
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val downloader = OkHttpNewPipeDownloader(http)

    @Volatile
    private var initialized = false

    private data class Cached(
        val info: com.itzfallenme.katmusic.data.StreamInfo,
        val storedAt: Long
    )

    private val cache = HashMap<String, Cached>()

    private fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(downloader)
            initialized = true
        }
    }

    /** Fresh, probe-checked audio URL for [videoId], preferring Opus for quality. */
    suspend fun resolve(
        videoId: String,
        quality: StreamQuality = StreamQuality.HIGH
    ): com.itzfallenme.katmusic.data.StreamInfo =
        withContext(Dispatchers.IO) {
            val key = "$videoId|${quality.id}"
            cache[key]?.let { cached ->
                if (System.currentTimeMillis() - cached.storedAt < CACHE_TTL_MS) {
                    return@withContext cached.info
                }
            }
            val extracted = runCatching { fromNewPipe(videoId, quality, key) }
                .getOrElse { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    runCatching { InnerTube.streamUrl(videoId, quality) }
                        .getOrElse { throw IOException("Could not resolve audio for $videoId", error) }
                }
            extracted
        }

    private fun fromNewPipe(
        videoId: String,
        quality: StreamQuality,
        key: String
    ): StreamInfo {
        initialize()
        val info = try {
            NewPipeStreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )
        } catch (error: ExtractionException) {
            throw IOException("NewPipe extraction failed", error)
        }
        // Keep only renditions inside the tier's bitrate ceiling; if the tier is
        // stricter than anything YouTube returned, fall back to the full list
        // rather than refusing to play.
        val withinTier = info.audioStreams.filter {
            quality.allows(maxOf(it.averageBitrate, it.bitrate))
        }.ifEmpty { info.audioStreams }
        val ordered = withinTier.sortedWith(
            compareBy<AudioStream> { preference(it, quality) }
                .thenByDescending { maxOf(it.averageBitrate, it.bitrate) }
        )
        if (ordered.isEmpty()) throw IOException("No audio streams returned")
        val candidate = ordered.firstOrNull { probe(it.content) }
            ?: throw IOException("Every audio URL was rejected")
        val resolved = com.itzfallenme.katmusic.data.StreamInfo(
            url = candidate.content,
            mimeType = candidate.format?.mimeType?.takeIf { it.contains("audio/") }
                ?: candidate.format?.mimeType
                ?: "audio/webm",
            bitrate = maxOf(candidate.averageBitrate, candidate.bitrate),
            lengthMs = info.duration * 1000L
        )
        synchronized(cache) {
            if (cache.size > 24) cache.clear()
            cache[key] = Cached(resolved, System.currentTimeMillis())
        }
        return resolved
    }

    private fun preference(stream: AudioStream, quality: StreamQuality): Int {
        val mime = stream.format?.mimeType.orEmpty().lowercase()
        val opus = mime.contains("webm") || stream.codec?.contains("opus", true) == true
        val m4a = mime.contains("mp4") || mime.contains("m4a")
        // At low bitrates AAC holds up better; Opus wins when there is headroom.
        val preferOpus = quality != StreamQuality.SAVER
        return when {
            preferOpus && opus -> 0
            !preferOpus && m4a -> 0
            preferOpus && m4a -> 1
            !preferOpus && opus -> 1
            else -> 2
        }
    }

    /**
     * Opens the URL the way the player will: no Range header, and only a successful,
     * non-HTML response counts. A 403 here means the URL is signed for a different
     * client and would fail during playback too.
     */
    private fun probe(url: String): Boolean {
        if (url.isBlank()) return false
        val request = Request.Builder()
            .url(url)
            .header("Accept", "audio/*,*/*;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", InnerTube.VR_UA)
            .header("Referer", "https://music.youtube.com/")
            .build()
        val call = http.newCall(request)
        call.timeout().timeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return try {
            call.execute().use { response ->
                val type = response.header("Content-Type").orEmpty().lowercase()
                val playable = !type.contains("text/html") &&
                    !type.contains("application/json") &&
                    !type.contains("text/plain")
                (response.code == 200 || response.code == 206) && playable
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    /** Clears cached URLs so a rejected stream can recover without restarting the app. */
    fun invalidate(videoId: String) {
        synchronized(cache) {
            cache.keys.filter { it.startsWith("$videoId|") }.forEach { cache.remove(it) }
        }
    }
}

/** NewPipe downloader backed by the client the rest of the app already uses. */
private class OkHttpNewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request):
        org.schabi.newpipe.extractor.downloader.Response {
        val builder = Request.Builder()
            .url(request.url())
            .method(
                request.httpMethod(),
                request.dataToSend()?.let { body ->
                    okhttp3.RequestBody.create(null, body)
                }
            )
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers().keys.none { it.equals("User-Agent", true) }) {
            builder.header("User-Agent", InnerTube.WEB_UA)
        }
        if (request.headers().keys.none { it.equals("Accept-Language", true) }) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }
        val origin = if (request.url().contains("music.youtube.com", true)) {
            "https://music.youtube.com"
        } else {
            "https://www.youtube.com"
        }
        if (request.headers().keys.none { it.equals("Origin", true) }) {
            builder.header("Origin", origin)
        }
        if (request.headers().keys.none { it.equals("Referer", true) }) {
            builder.header("Referer", "$origin/")
        }
        return client.newCall(builder.build()).execute().use { response ->
            org.schabi.newpipe.extractor.downloader.Response(
                response.code,
                response.message,
                response.headers.names().associateWith { response.headers.values(it) },
                response.body?.string().orEmpty(),
                response.request.url.toString()
            )
        }
    }
}
