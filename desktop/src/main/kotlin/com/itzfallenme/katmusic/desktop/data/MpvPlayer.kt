package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.player.QueueNavigator
import com.itzfallenme.katmusic.shared.player.RepeatMode
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop streaming playback through libmpv (the SimpMusic media-jvm
 * approach): mpv decodes every YouTube/remote audio container (Opus in WebM,
 * AAC in MP4, HLS) plus all local formats, through a JNA binding of the small
 * C API surface we need.
 *
 * Loading is probed once: when `libmpv.so`/`libmpv.dylib`/`mpv-2.dll` is not
 * installed, [isAvailable] is false and the app keeps the Java Sound engine
 * for local files instead of shipping a broken control.
 */
class MpvPlayer(
    /** Mints a fresh googlevideo URL when a track ends with a media error. */
    private val freshUrl: ((String) -> String?)? = null,
) : PlayerController {

    interface MpvLibrary : Library {
        fun mpv_create(): Pointer?
        fun mpv_initialize(handle: Pointer): Int
        fun mpv_terminate_destroy(handle: Pointer?)
        fun mpv_command(handle: Pointer, args: Array<String?>): Int
        fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
        fun mpv_set_property(handle: Pointer, name: String, format: Int, data: Pointer): Int
        fun mpv_get_property(handle: Pointer, name: String, format: Int, data: Pointer): Int
        fun mpv_observe_property(handle: Pointer, replyUserData: Long, name: String, format: Int): Int
        fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
        fun mpv_request_log_messages(handle: Pointer, level: String): Int
    }

    companion object {
        /** mpv_format ids from client.h. */
        private const val FORMAT_FLAG = 3
        private const val FORMAT_STRING = 1
        private const val FORMAT_DOUBLE = 5

        // mpv_event_id values from client.h.
        private const val EVENT_NONE = 0
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_END_FILE = 7
        private const val EVENT_PROPERTY_CHANGE = 22
        private const val MAX_STREAM_REFRESH_ATTEMPTS = 2

        private val NAMES = listOf(
            "mpv" to listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so"),
            "mac" to listOf("libmpv.dylib", "libmpv.2.dylib"),
            "win" to listOf("mpv-2.dll", "libmpv-2.dll", "mpv.dll"),
        )

        fun probe(): MpvLibrary? {
            val os = System.getProperty("os.name", "").lowercase()
            val candidates = when {
                os.contains("win") -> NAMES.first { it.first == "win" }.second
                os.contains("mac") || os.contains("darwin") -> NAMES.first { it.first == "mac" }.second
                else -> NAMES.first { it.first == "mpv" }.second
            }
            for (name in candidates) {
                runCatching { return Native.load(name, MpvLibrary::class.java) }
            }
            return null
        }
    }

    private val lib: MpvLibrary? = runCatching { probe() }.getOrNull()

    /** False when libmpv is not installed; the app falls back to Java Sound. */
    val isAvailable: Boolean get() = lib != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var handle: Pointer? = null
    private val eventThreadRunning = AtomicBoolean(false)
    private val shutdown = AtomicBoolean(false)
    private var generation = 0
    private var refreshAttempts = 0

    init {
        if (lib != null) {
            val created = lib.mpv_create()
            if (created != null) {
                lib.mpv_set_property_string(created, "vo", "null")
                lib.mpv_set_property_string(created, "audio-display", "no")
                lib.mpv_set_property_string(created, "terminal", "no")
                // Same identity the extractor used to mint the media URL.
                lib.mpv_set_property_string(created, "user-agent", EXTRACTOR_USER_AGENT)
                if (lib.mpv_initialize(created) == 0) {
                    handle = created
                    lib.mpv_observe_property(created, 1, "time-pos", FORMAT_DOUBLE)
                    lib.mpv_observe_property(created, 2, "duration", FORMAT_DOUBLE)
                    lib.mpv_observe_property(created, 3, "eof-reached", FORMAT_FLAG)
                    lib.mpv_observe_property(created, 4, "pause", FORMAT_FLAG)
                    startEventLoop(created)
                } else {
                    lib.mpv_terminate_destroy(created)
                }
            }
        }
    }

    override fun play(tracks: List<Track>, startIndex: Int) {
        if (!isAvailable || tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        generation++
        refreshAttempts = 0
        _state.update {
            it.copy(
                queue = tracks,
                index = index,
                isBuffering = true,
                isPlaying = true,
                positionMs = 0L,
                durationMs = tracks[index].durationMs,
                error = null,
            )
        }
        loadCurrent()
    }

    override fun togglePlayPause() {
        if (_state.value.current == null) return
        val next = !_state.value.isPlaying
        _state.update { it.copy(isPlaying = next) }
        handle?.let { lib?.mpv_set_property(it, "pause", FORMAT_FLAG, intPointer(if (next) 1 else 0)) }
    }

    override fun next() {
        val snapshot = _state.value
        val next = QueueNavigator.nextIndex(snapshot.queue.size, snapshot.index, snapshot.repeatMode, snapshot.shuffle, autoAdvance = false)
        if (next < 0) stop() else switchTo(next)
    }

    override fun previous() {
        val snapshot = _state.value
        val prev = QueueNavigator.previousIndex(
            size = snapshot.queue.size,
            currentIndex = snapshot.index,
            positionMs = snapshot.positionMs,
            repeatMode = snapshot.repeatMode,
            shuffle = snapshot.shuffle,
        )
        if (prev < 0) stop() else switchTo(prev)
    }

    override fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
        handle?.let { lib?.mpv_command(it, arrayOf("seek", (positionMs / 1000.0).toString(), "absolute")) }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        handle?.let { lib?.mpv_set_property(it, "volume", FORMAT_DOUBLE, doublePointer(clamped * 100.0)) }
    }

    override fun setRepeatMode(mode: RepeatMode) = _state.update { it.copy(repeatMode = mode) }

    override fun setShuffle(enabled: Boolean) = _state.update { it.copy(shuffle = enabled) }

    override fun cycleRepeatMode() = _state.update { it.copy(repeatMode = QueueNavigator.cycle(it.repeatMode)) }

    override fun stop() {
        generation++
        handle?.let { lib?.mpv_command(it, arrayOf("stop")) }
        _state.update { it.copy(isPlaying = false, isBuffering = false, positionMs = 0) }
    }

    /** Releases the mpv handle; safe to call when libmpv was never loaded. */
    fun release() {
        if (shutdown.getAndSet(true)) return
        stop()
        scope.cancel()
        handle?.let { lib?.mpv_terminate_destroy(it) }
        handle = null
    }

    private fun switchTo(index: Int) {
        val snapshot = _state.value
        if (snapshot.queue.getOrNull(index) == null) return
        generation++
        refreshAttempts = 0
        _state.update { it.copy(index = index, positionMs = 0, isPlaying = true, isBuffering = true, error = null) }
        loadCurrent()
    }

    private fun loadCurrent() {
        val track = _state.value.current ?: return
        val h = handle ?: return
        val source = track.source
        val fileArg = when {
            source.startsWith("file:") -> java.net.URI(source).path ?: source
            else -> source
        }
        val pauseFlag = if (!_state.value.isPlaying) "yes" else "no"
        lib?.mpv_command(h, arrayOf("loadfile", fileArg, "replace"))
        lib?.mpv_set_property_string(h, "pause", pauseFlag)
    }

    private fun startEventLoop(h: Pointer) {
        if (!eventThreadRunning.compareAndSet(false, true)) return
        scope.launch {
            var eofSeen = false
            while (isActive && !shutdown.get() && handle === h) {
                val event = lib?.mpv_wait_event(h, 0.25) ?: break
                val eventId = event.getInt(0) // mpv_event.event_id
                when (eventId) {
                    EVENT_NONE -> break // NONE ends the pointer validity window
                    EVENT_PROPERTY_CHANGE -> {
                        // mpv_event.data points at mpv_event_property.
                        val prop = event.getPointer(16)
                        if (prop != null) {
                            // mpv_event_property: name (0), format (8), data (16).
                            val name = prop.getString(0)
                            val format = prop.getInt(8)
                            val data = prop.getPointer(16)
                            when (name) {
                                "time-pos" -> if (format == FORMAT_DOUBLE && data != null) {
                                    _state.update { it.copy(positionMs = (data.getDouble(0) * 1000).toLong()) }
                                }
                                "duration" -> if (format == FORMAT_DOUBLE && data != null) {
                                    _state.update { it.copy(durationMs = (data.getDouble(0) * 1000).toLong()) }
                                }
                                "eof-reached" -> if (format == FORMAT_FLAG && data != null && data.getInt(0) == 1) {
                                    eofSeen = true
                                }
                                "pause" -> Unit // pause tracked through our own state
                            }
                        }
                    }
                    EVENT_END_FILE -> {
                        // mpv_event_end_file.reason: 0 EOF, 2 STOP, 4 ERROR.
                        val reason = event.getPointer(16)?.getInt(0) ?: 0
                        if (reason == 0 || eofSeen) {
                            eofSeen = false
                            if (_state.value.isPlaying && generation > 0) {
                                advanceAfterTrackEnd()
                            }
                        } else if (reason == 4) {
                            recoverExpiredStream()
                        }
                    }
                    EVENT_SHUTDOWN -> break
                }
                delay(1)
            }
            eventThreadRunning.set(false)
        }
    }

    /**
     * A signed googlevideo URL can expire or be refused mid-song. Mint a fresh
     * one for the same video and reload at the position playback reached,
     * instead of leaving the player stopped with no error.
     */
    private fun recoverExpiredStream() {
        val snapshot = _state.value
        val track = snapshot.current ?: return
        if (track.isLocal || track.source.startsWith("file:", ignoreCase = true)) return
        val resolver = freshUrl ?: return
        if (refreshAttempts >= MAX_STREAM_REFRESH_ATTEMPTS) {
            _state.update { it.copy(isPlaying = false, isBuffering = false, error = "Stream URL expired") }
            return
        }
        refreshAttempts++
        val resumeAt = snapshot.positionMs.coerceAtLeast(0L)
        val expectedGeneration = generation
        _state.update { it.copy(isBuffering = true, error = null) }
        scope.launch {
            val fresh = runCatching { resolver(track.id) }.getOrNull()
            val h = handle
            if (fresh.isNullOrBlank() || h == null || expectedGeneration != generation) {
                if (expectedGeneration == generation) {
                    _state.update { it.copy(isBuffering = false, error = "Stream URL expired") }
                }
                return@launch
            }
            val updated = track.copy(source = fresh)
            _state.update { state ->
                val queue = state.queue.toMutableList().apply {
                    if (state.index in indices) set(state.index, updated)
                }
                state.copy(
                    queue = queue,
                    positionMs = resumeAt,
                    isBuffering = false,
                    isPlaying = true,
                    error = null,
                )
            }
            lib?.mpv_command(h, arrayOf("loadfile", fresh, "replace"))
            lib?.mpv_set_property_string(h, "pause", "no")
            if (resumeAt > 0L) {
                lib?.mpv_command(h, arrayOf("seek", (resumeAt / 1000.0).toString(), "absolute"))
            }
        }
    }

    private fun advanceAfterTrackEnd() {
        val snapshot = _state.value
        val next = QueueNavigator.nextIndex(
            size = snapshot.queue.size,
            currentIndex = snapshot.index,
            repeatMode = snapshot.repeatMode,
            shuffle = snapshot.shuffle,
            autoAdvance = true,
        )
        if (next < 0) {
            _state.update { it.copy(isPlaying = false, positionMs = 0) }
        } else {
            switchTo(next)
        }
    }

    private fun intPointer(value: Int): Pointer {
        val ref = IntByReference(value)
        return ref.pointer
    }

    private fun doublePointer(value: Double): Pointer {
        val ref = DoubleByReference(value)
        return ref.pointer
    }

}
