package com.itzfallenme.katmusic.desktop.data

import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.downloader.Request
import dev.maxrave.pipepipe.extractor.downloader.Response
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
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
class NewPipeStreamResolver {
    private val headClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val pipePipeDownloader = PipePipeDownloaderImpl()
    private val braveDownloader = BraveDownloaderImpl()

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
    fun resolveAudioUrl(videoId: String): Result<String> {
        init()
        val errors = mutableListOf<String>()
        for ((tier, extract) in listOf(
            "PipePipe" to { pipePipeStreams(videoId) },
            "BravePipe" to { braveStreams(videoId) },
        )) {
            try {
                val streams = extract()
                val url = pickAudioUrl(streams) ?: run {
                    errors += "$tier: no audio itag"
                    null
                }
                if (url == null) continue
                if (!headOk(url)) {
                    errors += "$tier: URLs rejected by CDN (stale signature)"
                    continue
                }
                return Result.success(url)
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

    /** HEAD probe; a URL is only trusted when the CDN answers 2xx/3xx. */
    private fun headOk(url: String): Boolean = runCatching {
        headClient.newCall(okhttp3.Request.Builder().head().url(url).build())
            .execute().use { response -> response.code in 200..399 }
    }.getOrDefault(false)

    class StreamResolveException(override val message: String) : Exception(message)
}

/**
 * Best audio URL from an extraction: the highest-quality audio itag the
 * player can stream (Opus 774/251 preferred, AAC 141/140 next). Video and
 * muxed itags are never chosen, and blank URLs are dropped.
 */
internal fun pickAudioUrl(streams: List<Pair<Int, String>>): String? =
    streams.filter { it.first in AUDIO_ITAGS }
        .maxByOrNull { it.first }
        ?.second
        ?.takeUnless { it.isBlank() }

/** Itags carrying playable audio (SimpMusic's ITAG.AUDIO set). */
internal val AUDIO_ITAGS = setOf(140, 141, 250, 251, 774)

/** OkHttp-backed downloader for the PipePipe extractor. */
private class PipePipeDownloaderImpl : Downloader() {
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
        return builder.build()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) KatMusic/2.6"
    }
}

/** OkHttp-backed downloader for the BravePipe extractor. */
private class BraveDownloaderImpl : BraveDownloader() {
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
        return builder.build()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) KatMusic/2.6"
    }
}
