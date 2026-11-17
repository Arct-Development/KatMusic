package com.itzfallenme.katmusic.desktop.data

import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.downloader.Request
import dev.maxrave.pipepipe.extractor.downloader.Response
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.downloader.Downloader as BraveDownloader
import org.schabi.newpipe.extractor.downloader.Request as BraveRequest
import org.schabi.newpipe.extractor.downloader.Response as BraveResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException as BraveReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * SimpMusic's desktop streaming method: NewPipe-fork extractors that resolve
 * real googlevideo URLs from a videoId, handling YouTube's signature and
 * `n`-throttling challenges — the part a plain InnerTube `/player` call
 * cannot do without BotGuard tokens.
 *
 * Two tiers, run in order until one produces usable audio:
 *  1. **PipePipe extractor** — signature/n challenges solved remotely at
 *     `api.pipepipe.dev` (SimpMusic's hosted-decode tier; its local QuickJS
 *     decoder is not ported, so the hosted tier is the primary here).
 *  2. **BravePipe extractor** — a second NewPipe fork with its own cipher
 *     decoding, entirely on-device.
 *
 * Streams must contain a playable audio itag and the chosen URL must pass a
 * live HEAD check before a tier's result is accepted — the same validation
 * SimpMusic applies, because a well-formed but stale signature yields URLs
 * that look fine and play nothing. Provider-side blocks (bot checks) are not
 * bypassed by extraction; the provider's own reason is returned as the error.
 */
class NewPipeStreamResolver(
    /**
     * The signed-in YouTube `Cookie` header, when the desktop has been paired
     * with the phone. PipePipe consumes it through
     * `ServiceList.YouTube.tokens` (the exact hook SimpMusic uses); the
     * downloader adds it to BravePipe requests.
     */
    private val cookieHeader: () -> String? = { null },
) {
    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val pipePipeDownloader = PipePipeDownloaderImpl(cookieHeader)
    private val braveDownloader = BraveDownloaderImpl(cookieHeader)

    @Volatile
    private var initialized = false

    /** Idempotent: NewPipe.init must run before the first extraction. */
    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(pipePipeDownloader)
            BraveNewPipe.init(braveDownloader)
            initialized = true
        }
    }

    /**
     * Resolves [videoId] to one playable audio URL, trying every tier.
     * Fails with every tier's reason when none produces audio.
     */
    fun resolveAudioUrl(videoId: String): Result<String> =
        resolveAudio(videoId).map { it.first }

    /** Same as [resolveAudioUrl], keeping the chosen itag for downloads. */
    fun resolveAudio(videoId: String): Result<Pair<String, Int>> {
        init()
        // Signed-in extraction: PipePipe reads this global exactly like
        // SimpMusic's desktop Extractor.logIn(cookie). Clearing it on sign-out
        // is handled by the provider returning null.
        ServiceList.YouTube.tokens = cookieHeader().orEmpty()
        val errors = mutableListOf<String>()
        for ((tier, extract) in listOf(
            "PipePipe" to { pipePipeStreams(videoId) },
            "BravePipe" to { braveStreams(videoId) },
        )) {
            try {
                val streams = extract()
                val picked = pickAudio(streams) ?: run {
                    errors += "$tier: no audio itag"
                    null
                }
                if (picked == null) continue
                if (!probePlayable(picked.second)) {
                    errors += "$tier: URL failed the media probe (stale signature)"
                    continue
                }
                return Result.success(picked.second to picked.first)
            } catch (error: Throwable) {
                errors += "$tier: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        return Result.failure(StreamResolveException(errors.joinToString(" · ")))
    }

    /** One PipePipe extraction: audio+video+videoOnly itags of the video. */
    private fun pipePipeStreams(videoId: String): List<Pair<Int, String>> {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
        val streams = info.audioStreams + info.videoStreams + info.videoOnlyStreams
        val pairs = streams.mapNotNull { stream ->
            val itag = stream.itagItem?.id ?: return@mapNotNull null
            itag to stream.content
        }
        if (pairs.none { it.first in AUDIO_ITAGS }) {
            throw IllegalStateException("no audio itag (got ${pairs.map { it.first }})")
        }
        return pairs
    }

    /** One BravePipe extraction, same shape as the PipePipe one. */
    private fun braveStreams(videoId: String): List<Pair<Int, String>> {
        val info = BraveStreamInfo.getInfo(BraveServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val streams = info.audioStreams + info.videoStreams + info.videoOnlyStreams
        val pairs = streams.mapNotNull { stream ->
            val itag = stream.itagItem?.id ?: return@mapNotNull null
            itag to stream.content
        }
        if (pairs.none { it.first in AUDIO_ITAGS }) {
            throw IllegalStateException("no audio itag (got ${pairs.map { it.first }})")
        }
        return pairs
    }

    /**
     * Opens the media URL the way a player does: ranged GET, reading past the
     * first MiB when the size allows it. HEAD (and 0-1 byte ranges) can answer
     * 200 while the body is refused, which is how a track starts and dies a
     * minute in.
     */
    private fun probePlayable(url: String): Boolean = runCatching {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "audio/*,*/*;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Range", probeRange(url))
            .header("User-Agent", EXTRACTOR_USER_AGENT)
            .build()
        probeClient.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 206) return@use false
            val type = response.header("Content-Type").orEmpty().lowercase()
            if (type.contains("text/html") || type.contains("application/json") ||
                type.contains("text/plain")
            ) {
                return@use false
            }
            val source = response.body?.source() ?: return@use false
            source.request(1L) && run {
                source.request(PROBE_READ_BYTES)
                true
            }
        }
    }.getOrDefault(false)

    class StreamResolveException(override val message: String) : Exception(message)
}

/**
 * Best audio URL from an extraction: the highest-quality audio itag the
 * player can stream (Opus 774/251 preferred, AAC 141/140 next). Video and
 * muxed itags are never chosen, and blank URLs are dropped.
 */
internal fun pickAudioUrl(streams: List<Pair<Int, String>>): String? = pickAudio(streams)?.second

/** The chosen audio itag and URL; blank URLs are rejected. */
internal fun pickAudio(streams: List<Pair<Int, String>>): Pair<Int, String>? =
    streams.filter { it.first in AUDIO_ITAGS }
        .maxByOrNull { it.first }
        ?.takeIf { it.second.isNotBlank() }

/** Itags carrying playable audio (SimpMusic's ITAG.AUDIO set). */
internal val AUDIO_ITAGS = setOf(140, 141, 250, 251, 774)

/** Opus-in-WebM itags; every other supported itag is AAC-in-MP4. */
internal val OPUS_ITAGS = setOf(250, 251, 774)

private const val PROBE_AUTH_BOUNDARY_BYTES = 1L * 1024 * 1024
private const val PROBE_WINDOW_BYTES = 1L * 1024 * 1024
private const val PROBE_READ_BYTES = 16L * 1024
internal const val EXTRACTOR_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) KatMusic/2.6"

/**
 * Range for the playback probe: the window just past the first MiB when
 * `clen` says the file reaches it, otherwise the opening. Some clients serve
 * only the first MiB and 403 the rest.
 */
internal fun probeRange(url: String): String {
    val length = url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0L }
    val start = if (length != null && length > PROBE_AUTH_BOUNDARY_BYTES + PROBE_READ_BYTES) {
        PROBE_AUTH_BOUNDARY_BYTES
    } else {
        0L
    }
    val end = (start + PROBE_WINDOW_BYTES - 1).let { if (length != null) minOf(it, length - 1) else it }
    return "bytes=$start-$end"
}

/** OkHttp-backed downloader for the PipePipe extractor. */
private class PipePipeDownloaderImpl(
    private val cookieHeader: () -> String?,
) : Downloader() {
    private val client = OkHttpClient.Builder().build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val response = client.newCall(buildOkHttpRequest(request)).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }
        val bytes = response.body?.bytes() ?: ByteArray(0)
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            bytes.toString(Charsets.UTF_8),
            bytes,
            response.request.url.toString(),
        )
    }

    override fun executeAsync(
        request: Request,
        callback: AsyncCallback?,
    ): CancellableCall {
        val call = client.newCall(buildOkHttpRequest(request))
        val cancellable = CancellableCall(call)
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cancellable.setFinished()
                    callback?.onError(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        if (response.code == 429) {
                            response.close()
                            callback?.onError(ReCaptchaException("reCaptcha Challenge requested", request.url()))
                            return
                        }
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        callback?.onSuccess(
                            Response(
                                response.code,
                                response.message,
                                response.headers.toMultimap(),
                                bytes.toString(Charsets.UTF_8),
                                bytes,
                                response.request.url.toString(),
                            ),
                        )
                    } catch (e: Exception) {
                        callback?.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            },
        )
        return cancellable
    }

    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            if (values.size > 1) {
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            } else if (values.size == 1) {
                builder.header(name, values[0])
            }
        }
        attachSessionCookies(builder, request.url(), request.headers()["Cookie"])
        return builder.build()
    }

    private fun attachSessionCookies(
        builder: okhttp3.Request.Builder,
        url: String,
        existing: List<String>?,
    ) {
        val session = cookieHeader()?.takeIf { it.isNotBlank() } ?: return
        if (!sendsYoutubeCookies(url)) return
        // Keep the extractor's consent cookie, prepend the account session.
        mergeCookieHeaders(session, existing)?.let { builder.header("Cookie", it) }
    }

    private companion object {
        const val USER_AGENT = EXTRACTOR_USER_AGENT
    }
}

/** OkHttp-backed downloader for the BravePipe extractor. */
private class BraveDownloaderImpl(
    private val cookieHeader: () -> String?,
) : BraveDownloader() {
    private val client = OkHttpClient.Builder().build()

    @Throws(IOException::class, BraveReCaptchaException::class)
    override fun execute(request: BraveRequest): BraveResponse {
        val response = client.newCall(buildOkHttpRequest(request)).execute()
        if (response.code == 429) {
            response.close()
            throw BraveReCaptchaException("reCaptcha Challenge requested", request.url())
        }
        val body = response.body?.string()
        return BraveResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            body,
            response.request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: BraveRequest): okhttp3.Request {
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            if (values.size > 1) {
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            } else if (values.size == 1) {
                builder.header(name, values[0])
            }
        }
        val sessionCookies = cookieHeader()?.takeIf { it.isNotBlank() }
        if (sessionCookies != null && sendsYoutubeCookies(request.url())) {
            val extractorCookies = request.headers()["Cookie"].orEmpty().filter { it.isNotBlank() }
            mergeCookieHeaders(sessionCookies, extractorCookies)?.let {
                builder.header("Cookie", it)
            }
        }
        return builder.build()
    }

    private companion object {
        const val USER_AGENT = EXTRACTOR_USER_AGENT
    }
}

/**
 * Account cookies belong to YouTube/Google hosts only — never to the hosted
 * cipher decoder or any other extractor endpoint.
 */
internal fun sendsYoutubeCookies(url: String): Boolean = runCatching {
    val host = java.net.URI(url).host?.lowercase() ?: return@runCatching false
    host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "google.com" || host.endsWith(".google.com") ||
        host == "googleapis.com" || host.endsWith(".googleapis.com")
}.getOrDefault(false)

/**
 * Merges the session header with whatever the extractor already attached,
 * keeping the first value for each cookie name. The result is one `Cookie`
 * header instead of two overlapping ones.
 */
internal fun mergeCookieHeaders(session: String?, extractor: List<String>?): String? {
    val pairs = LinkedHashMap<String, String>()
    (listOfNotNull(session) + extractor.orEmpty()).forEach { header ->
        header.split(';').forEach { part ->
            val pair = part.trim()
            if (pair.isEmpty()) return@forEach
            val name = pair.substringBefore('=')
            if (name.isNotEmpty() && !pairs.containsKey(name)) pairs[name] = pair
        }
    }
    return pairs.values.joinToString("; ").ifBlank { null }
}
