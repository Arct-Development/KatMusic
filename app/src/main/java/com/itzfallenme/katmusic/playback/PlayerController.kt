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
    val radioLoading: Boolean = false
)

/** Resolves katmusic://watch?v=ID URIs into fresh googlevideo audio URLs at load time. */
private class StreamResolver(private val highQuality: Boolean) : ResolvingDataSource.Resolver {
    private val cache = HashMap<String, Pair<String, Long>>()

    override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val videoId = dataSpec.uri.getQueryParameter("v") ?: return dataSpec
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[videoId]?.let { (url, expires) ->
                if (now < expires) return dataSpec.withUri(url.toUri())
            }
        }
        val info = runBlocking {
            runCatching { InnerTube.streamUrl(videoId, highQuality) }.getOrNull()
        } ?: throw IOException("Could not resolve audio stream")
        synchronized(cache) { cache[videoId] = info.url to now + 3 * 60 * 60 * 1000 }
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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val player: ExoPlayer by lazy {
        buildPlayer()
    }

    private fun buildPlayer(): ExoPlayer {
        val okHttpFactory = OkHttpDataSource.Factory(InnerTube.http)
            .setUserAgent(InnerTube.VR_UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://music.youtube.com/"))
        val dataSourceFactory = ResolvingDataSource.Factory(
            okHttpFactory,
            StreamResolver(settings.state.value.highQualityStream)
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
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
                _state.value = _state.value.copy(
                    track = track,
                    index = index,
                    durationMs = track?.durationMs ?: exo.duration.coerceAtLeast(0),
                    positionMs = 0
                )
                track?.let { onTrackStarted(it) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(playing = isPlaying)
                if (isPlaying) ensureService()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = if (exo.duration > 0) exo.duration else _state.value.durationMs
                )
                if (playbackState == Player.STATE_ENDED) handleEnded()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.value = _state.value.copy(shuffle = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.value = _state.value.copy(repeat = repeatMode.toRepeatMode())
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    error = "Playback failed: ${error.errorCodeName}",
                    buffering = false
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

    fun ensureService() {
        val intent = Intent(context, PlaybackService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun mediaItemFor(track: Track): MediaItem {
        val extras = Bundle().apply {
            putString("track", json.encodeToString(Track.serializer(), track))
        }
        return MediaItem.Builder()
            .setUri("https://katmusic.local/watch?v=${track.id}")
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

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        val playable = tracks.filter { it.isPlayable }
        if (playable.isEmpty()) return
        val index = startIndex.coerceIn(0, playable.size - 1)
        val items = playable.map { mediaItemFor(it) }
        player.setMediaItems(items, index, 0L)
        player.prepare()
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
        ensureService()
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
            if (player.isPlaying) {
                _state.value = _state.value.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
                )
            }
            delay(500)
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
