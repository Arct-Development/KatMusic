package com.itzfallenme.katmusic.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.LibraryRepository
import com.itzfallenme.katmusic.data.Scrobble
import com.itzfallenme.katmusic.data.SettingsRepository
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.YtJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.IOException

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sleepAtMs: Long = 0L,
    val error: String? = null,
    val radioLoading: Boolean = false,
    /** Non-zero when a listening-break reminder is due, in minutes. */
    val listeningReminderMinutes: Int = 0,
    /** Sample MIME of the audio decoder actually in use, once known. */
    val audioCodec: String? = null,
    /** Bitrate the decoder reported for the current stream; 0 when unknown. */
    val audioBitrateKbps: Int = 0,
    /** Output sample rate the decoder reported; 0 when unknown. */
    val audioSampleRateHz: Int = 0
)

/**
 * Renderers factory whose audio sink carries [processors] - currently the mono
 * downmixer. ExoPlayer has no public audio-sink setter, but the default
 * renderers factory builds the sink itself, so this hook is the supported way
 * to install one.
 */
private class MonoRenderersFactory(
    context: Context,
    private val processors: Array<androidx.media3.common.audio.AudioProcessor>
) : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): androidx.media3.exoplayer.audio.AudioSink =
        androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setAudioProcessors(processors)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
}

/** Resolves katmusic://watch?v=ID URIs into fresh, probe-checked audio URLs at load time. */
private class PlaybackUrlResolver(
    private val quality: () -> com.itzfallenme.katmusic.data.StreamQuality
) : ResolvingDataSource.Resolver {
    private val cache = HashMap<String, Triple<String, Long, String>>()

    override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val videoId = dataSpec.uri.getQueryParameter("v") ?: return dataSpec
        val now = System.currentTimeMillis()
        val tier = quality().id
        synchronized(cache) {
            cache[videoId]?.let { (url, expires, cachedTier) ->
                if (now < expires && cachedTier == tier) return dataSpec.withUri(url.toUri())
            }
        }
        val info = runBlocking {
            runCatching { com.itzfallenme.katmusic.data.StreamResolver.resolve(videoId, quality()) }
                .getOrNull()
        } ?: throw IOException("Could not resolve audio stream")
        synchronized(cache) { cache[videoId] = Triple(info.url, now + 40 * 60 * 1000, tier) }
        return dataSpec.withUri(info.url.toUri())
    }

    private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)
}

class PlayerController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val library: LibraryRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var sleepJob: Job? = null
    private var lastPositionMs = 0L
    private var skipSegments: List<Pair<Long, Long>> = emptyList()
    private var lastSkippedSegment = -1L
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val monoProcessor = MonoDownmixProcessor().apply {
        enabled = settings.state.value.monoAudio
    }
    private val volumeGuard = VolumeGuard(context)
    /** Playback milliseconds since the last listening-break acknowledgement. */
    private var listenedMs = 0L

    val player: ExoPlayer by lazy {
        buildPlayer()
    }

    /** Resolves a downloaded file for a track id so offline copies play from disk. */
    var localFileResolver: ((String) -> java.io.File?)? = null

    private fun buildPlayer(): ExoPlayer {
        val okHttpFactory = OkHttpDataSource.Factory(InnerTube.streamHttp)
            .setUserAgent(InnerTube.VR_UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://music.youtube.com/"))
        val dataSourceFactory = ResolvingDataSource.Factory(
            okHttpFactory,
            PlaybackUrlResolver {
                com.itzfallenme.katmusic.data.StreamQuality.fromId(settings.state.value.streamQuality)
            }
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(MonoRenderersFactory(context, arrayOf(monoProcessor)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exo.currentMediaItemIndex
                val track = _state.value.queue.getOrNull(index)
                val previous = _state.value.track
                if (previous != null && previous.id != track?.id) {
                    val threshold = if (previous.durationMs > 0) {
                        minOf(previous.durationMs / 2, 240_000L)
                    } else 240_000L
                    if (lastPositionMs >= threshold && lastPositionMs > 30_000L) {
                        scrobbleIfNeeded(previous)
                    }
                }
                lastPositionMs = 0L
                _state.value = _state.value.copy(
                    track = track,
                    index = index,
                    durationMs = track?.durationMs ?: exo.duration.coerceAtLeast(0),
                    positionMs = 0,
                    // The next stream has not been decoded yet, so its format is
                    // unknown until the decoder reports it.
                    audioCodec = null,
                    audioBitrateKbps = 0,
                    audioSampleRateHz = 0
                )
                track?.let { onTrackStarted(it) }
                refreshWidget()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(playing = isPlaying)
                // Deliberately no ensureService() here. Starting a foreground
                // service from this callback races the 5s startForeground
                // deadline: the session is created only now, after playback has
                // already begun, so Media3 never sees the play transition that
                // makes it post its notification - and the system kills the app
                // with ForegroundServiceDidNotStartInTimeException. The service
                // is requested before playback starts instead.
                refreshWidget()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = if (exo.duration > 0) exo.duration else _state.value.durationMs
                )
                if (playbackState == Player.STATE_ENDED) handleEnded()
                refreshWidget()
            }

            private fun refreshWidget() {
                runCatching {
                    com.itzfallenme.katmusic.widget.WidgetUpdater.refresh(context, _state.value)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.value = _state.value.copy(shuffle = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.value = _state.value.copy(repeat = repeatMode.toRepeatMode())
            }

            override fun onPlayerError(error: PlaybackException) {
                // Unavailable tracks are common (region locks, removed videos). Skip on
                // instead of dead-ending the queue.
                _state.value.track?.let { current ->
                    runCatching { com.itzfallenme.katmusic.data.StreamResolver.invalidate(current.id) }
                }
                if (exo.hasNextMediaItem()) {
                    _state.value = _state.value.copy(
                        error = "Skipped an unavailable track",
                        buffering = false
                    )
                    exo.seekToNextMediaItem()
                    exo.prepare()
                    exo.play()
                } else {
                    _state.value = _state.value.copy(
                        error = "Playback failed: ${error.errorCodeName}",
                        buffering = false
                    )
                }
            }
        })
        // The decoder is the only honest source of the stream's format: the tier
        // the user picked is a ceiling, not what was served. Publishing this lets
        // Discord show a real codec/bitrate line instead of a guessed one.
        exo.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                _state.value = _state.value.copy(
                    audioCodec = format.sampleMimeType,
                    audioBitrateKbps = (format.averageBitrate.takeIf { it > 0 } ?: format.bitrate) / 1000,
                    audioSampleRateHz = format.sampleRate
                )
            }
        })
        exo.repeatMode = Player.REPEAT_MODE_OFF
        return exo
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    /** True once the media service has been asked to start in this process. */
    @Volatile
    private var serviceRequested = false

    /**
     * Makes sure the media service is alive. Must be called *before* playback
     * begins: the service promotes itself to foreground when it observes the
     * player start, which is what satisfies the startForegroundService contract.
     */
    fun ensureService() {
        if (serviceRequested) return
        serviceRequested = true
        val intent = Intent(context, PlaybackService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure {
            serviceRequested = false
            com.itzfallenme.katmusic.util.AppLog.e("Playback", "could not start the media service", it)
        }
    }

    /** Called when the service is torn down so a later play can start it again. */
    fun onServiceDestroyed() {
        serviceRequested = false
    }

    private fun mediaItemFor(track: Track): MediaItem {
        val extras = Bundle().apply {
            putString("track", json.encodeToString(Track.serializer(), track))
        }
        val local = track.localPath
            ?.let { java.io.File(it) }
            ?.takeIf { it.exists() }
            ?: localFileResolver?.invoke(track.id)
        val uri = if (local != null) {
            android.net.Uri.fromFile(local)
        } else {
            android.net.Uri.parse("https://katmusic.local/watch?v=${track.id}")
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    /**
     * Mirrors a queue the media session built for an external controller (Android Auto),
     * so the phone UI shows the same track and list instead of a stale queue.
     */
    fun setExternalQueue(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(0, tracks.lastIndex)
        _state.value = _state.value.copy(
            queue = tracks,
            index = safeIndex,
            track = tracks[safeIndex],
            durationMs = tracks[safeIndex].durationMs,
            positionMs = 0,
            error = null
        )
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        val playable = tracks.filter { it.isPlayable }
        if (playable.isEmpty()) return
        val index = startIndex.coerceIn(0, playable.size - 1)
        val items = playable.map { mediaItemFor(it) }
        player.setMediaItems(items, index, 0L)
        player.prepare()
        // Request the service first so it observes the play transition and
        // posts its foreground notification well inside the 5s deadline.
        ensureService()
        if (autoPlay) player.play()
        _state.value = _state.value.copy(
            queue = playable,
            index = index,
            track = playable[index],
            durationMs = playable[index].durationMs,
            positionMs = 0,
            error = null
        )
        playable.getOrNull(index)?.let { onTrackStarted(it) }
    }

    fun playQueueStarting(track: Track, context_: List<Track>) {
        val index = context_.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playTracks(context_, index)
    }

    /** Starts a "song radio": the seed track followed by up-next recommendations. */
    fun playRadio(seed: Track) {
        scope.launch {
            _state.value = _state.value.copy(radioLoading = true)
            val related = runCatching { InnerTube.upNext(seed.id) }.getOrDefault(emptyList())
                .filter { it.id != seed.id }
            _state.value = _state.value.copy(radioLoading = false)
            playTracks(listOf(seed) + related, 0)
        }
    }

    fun playNext(track: Track) {
        val index = player.currentMediaItemIndex
        if (index < 0) {
            playTracks(listOf(track))
            return
        }
        player.addMediaItem(index + 1, mediaItemFor(track))
        val queue = _state.value.queue.toMutableList()
        queue.add((index + 1).coerceAtMost(queue.size), track)
        _state.value = _state.value.copy(queue = queue)
    }

    fun addToQueue(track: Track) {
        player.addMediaItem(mediaItemFor(track))
        _state.value = _state.value.copy(queue = _state.value.queue + track)
    }

    fun addAllToQueue(tracks: List<Track>) {
        tracks.forEach { addToQueue(it) }
    }

    fun removeFromQueue(index: Int) {
        if (index !in _state.value.queue.indices) return
        player.removeMediaItem(index)
        val queue = _state.value.queue.toMutableList().apply { removeAt(index) }
        _state.value = _state.value.copy(queue = queue)
    }

    /**
     * Drops everything after the current track. The playing song keeps playing;
     * only the upcoming part of the queue is cleared, so the queue sheet's "Clear"
     * action never stops playback by accident.
     */
    fun clearUpNext() {
        val current = player.currentMediaItemIndex
        if (current < 0) return
        val keep = current + 1
        while (player.mediaItemCount > keep) {
            player.removeMediaItem(player.mediaItemCount - 1)
        }
        _state.value = _state.value.copy(
            queue = _state.value.queue.take(keep),
            index = player.currentMediaItemIndex
        )
    }

    fun moveInQueue(from: Int, to: Int) {
        val queue = _state.value.queue
        if (from !in queue.indices || to !in queue.indices) return
        player.moveMediaItem(from, to)
        val updated = queue.toMutableList()
        val item = updated.removeAt(from)
        updated.add(to, item)
        _state.value = _state.value.copy(queue = updated, index = player.currentMediaItemIndex)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else skipWithRadio()
    }

    fun previous() {
        if (player.currentPosition > 5000) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
        if (duration > 0) seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
        settings.update { it.copy(playbackSpeed = speed) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepAtMs = 0L)
            return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepAtMs = end)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _state.value = _state.value.copy(sleepAtMs = 0L)
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.value = _state.value.copy(sleepAtMs = 0L)
    }

    private fun skipWithRadio() {
        val current = _state.value.track ?: return
        scope.launch {
            _state.value = _state.value.copy(radioLoading = true)
            val related = runCatching { InnerTube.upNext(current.id) }.getOrDefault(emptyList())
                .filter { it.id != current.id && _state.value.queue.none { q -> q.id == it.id } }
            if (related.isNotEmpty()) {
                related.forEach {
                    player.addMediaItem(mediaItemFor(it))
                }
                _state.value = _state.value.copy(queue = _state.value.queue + related)
                player.seekToNextMediaItem()
            }
            _state.value = _state.value.copy(radioLoading = false)
        }
    }

    private fun handleEnded() {
        if (settings.state.value.autoplayRadio) skipWithRadio()
    }

    private fun onTrackStarted(track: Track) {
        library.recordPlay(track)
        skipSegments = emptyList()
        lastSkippedSegment = -1L
        if (settings.state.value.sponsorBlockEnabled) loadSponsorSegments(track.id)
        if (settings.state.value.skipSilence) applySkipSilence(true)
        val config = settings.state.value
        if (config.scrobblingEnabled && config.lastFmSessionKey.isNotBlank()) {
            val client = ServiceLocator.lastFm()
            if (client != null) {
                scope.launch {
                    val ok = client.updateNowPlaying(track, config.lastFmSessionKey)
                    if (!ok) {
                        library.addScrobble(
                            Scrobble(
                                title = track.title,
                                artist = track.artist,
                                album = track.album.orEmpty(),
                                timeMs = System.currentTimeMillis() / 1000
                            )
                        )
                    }
                }
            }
        }
    }

    /** Called by the UI when a track has played far enough to count as a scrobble. */
    fun scrobbleIfNeeded(track: Track) {
        val config = settings.state.value
        if (!config.scrobblingEnabled || config.lastFmSessionKey.isBlank()) return
        val client = ServiceLocator.lastFm() ?: return
        scope.launch {
            runCatching { client.scrobble(track, System.currentTimeMillis(), config.lastFmSessionKey) }
        }
    }

    fun startPositionUpdates(): Job = scope.launch {
        while (isActive) {
            val config = settings.state.value
            if (player.isPlaying) {
                lastPositionMs = player.currentPosition.coerceAtLeast(0)
                applySponsorSkip()
                _state.value = _state.value.copy(
                    positionMs = lastPositionMs,
                    durationMs = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
                )
                applyCrossfade()
                if (config.volumeLimitEnabled) volumeGuard.enforce(config.volumeLimitPercent)
                trackListeningTime(config.listeningReminderMinutes)
            } else if (config.listeningReminderMinutes == 0 && _state.value.listeningReminderMinutes != 0) {
                _state.value = _state.value.copy(listeningReminderMinutes = 0)
            }
            delay(500)
        }
    }

    /** Counts continuous playback and raises a break reminder once the threshold is crossed. */
    private fun trackListeningTime(thresholdMinutes: Int) {
        if (thresholdMinutes <= 0) return
        listenedMs += 500
        if (listenedMs >= thresholdMinutes * 60_000L && _state.value.listeningReminderMinutes == 0) {
            _state.value = _state.value.copy(listeningReminderMinutes = thresholdMinutes)
        }
    }

    /** Called when the user dismisses the break reminder; the clock starts over. */
    fun acknowledgeListeningReminder() {
        listenedMs = 0L
        _state.value = _state.value.copy(listeningReminderMinutes = 0)
    }

    /**
     * Mono takes effect from the next track: the audio pipeline picks its output
     * channel count once per format, so switching mid-track would need a stop and
     * re-buffer that is worse than waiting one song.
     */
    fun applyMono(enabled: Boolean) {
        settings.update { it.copy(monoAudio = enabled) }
        monoProcessor.enabled = enabled
    }

    /** Applies the volume ceiling right away after the settings toggle changes. */
    fun applyVolumeGuard() {
        val config = settings.state.value
        if (config.volumeLimitEnabled) volumeGuard.enforce(config.volumeLimitPercent)
    }

    /** Ramps the volume down over the tail of a track and back up at the start. */
    private fun loadSponsorSegments(videoId: String) = scope.launch(Dispatchers.IO) {
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId" +
            "&categories=%5B%22sponsor%22%2C%22selfpromo%22%2C%22intro%22%5D"
        val body = runCatching {
            InnerTube.http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            }
        }.getOrNull() ?: return@launch
        val parsed = runCatching {
            YtJson.json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonArray
        }.getOrNull() ?: return@launch
        val segments = parsed.mapNotNull { entry ->
            val obj = entry as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val segment = obj["segment"] as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
            val start = (segment.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val end = (segment.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toDoubleOrNull() ?: return@mapNotNull null
            (start * 1000).toLong() to (end * 1000).toLong()
        }
        withContext(Dispatchers.Main) { skipSegments = segments }
    }

    private fun applySponsorSkip() {
        if (skipSegments.isEmpty()) return
        val position = player.currentPosition
        val hit = skipSegments.firstOrNull { (start, end) ->
            position >= start && position < end
        } ?: return
        if (hit.first == lastSkippedSegment) return
        lastSkippedSegment = hit.first
        player.seekTo(hit.second)
    }

    private fun applyCrossfade() {
        val fadeMs = settings.state.value.crossfadeSeconds * 1000L
        if (fadeMs <= 0) {
            if (player.volume != 1f) player.volume = 1f
            return
        }
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0 || position < 0) {
            player.volume = 1f
            return
        }
        val remaining = duration - position
        player.volume = when {
            remaining in 1..fadeMs -> (remaining.toFloat() / fadeMs).coerceIn(0f, 1f)
            position < fadeMs / 2 -> (position.toFloat() / (fadeMs / 2f)).coerceIn(0.15f, 1f)
            else -> 1f
        }
    }

    fun release() {
        sleepJob?.cancel()
        player.release()
    }

    fun trackFromMediaItem(item: MediaItem?): Track? {
        val raw = item?.mediaMetadata?.extras?.getString("track") ?: return null
        return runCatching { json.decodeFromString(Track.serializer(), raw) }.getOrNull()
    }

    fun currentQueue(): List<Track> = _state.value.queue

    fun applySkipSilence(enabled: Boolean) {
        player.skipSilenceEnabled = enabled
    }

    fun artworkOfCurrent(): String = _state.value.track?.artwork.orEmpty()
}
