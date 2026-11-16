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
class MpvPlayer : PlayerController {

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
        /** MPV_FORMAT_FLAG / MPV_FORMAT_DOUBLE / MPV_FORMAT_STRING ids. */
        private const val FORMAT_FLAG = 0
        private const val FORMAT_STRING = 1
        private const val FORMAT_DOUBLE = 4

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

    init {
        if (lib != null) {
            val created = lib.mpv_create()
            if (created != null) {
                lib.mpv_set_property_string(created, "vo", "null")
                lib.mpv_set_property_string(created, "audio-display", "no")
                lib.mpv_set_property_string(created, "terminal", "no")
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
                    0 -> break // MPV_EVENT_NONE ends the pointer validity window
                    7 -> { // MPV_EVENT_PROPERTY_CHANGE
                        val prop = event.getPointer(8) // mpv_event_property*
                        if (prop != null) {
                            val name = prop.getString(0)
                            val format = prop.getInt(16)
                            val data = prop.getPointer(24)
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
                    21, 22, 23 -> { // MPV_EVENT_END_FILE / SHUTDOWN / PLAYBACK_RESTART
                        if (eventId == 23) break // SHUTDOWN
                        if (eventId == 21 && eofSeen) { // END_FILE after eof
                            eofSeen = false
                            if (_state.value.isPlaying && generation > 0) {
                                advanceAfterTrackEnd()
                            }
                        }
                    }
                }
                delay(1)
            }
            eventThreadRunning.set(false)
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
