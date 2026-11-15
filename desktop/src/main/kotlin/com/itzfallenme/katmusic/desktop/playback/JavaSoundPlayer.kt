package com.itzfallenme.katmusic.desktop.playback

import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.player.QueueNavigator
import com.itzfallenme.katmusic.shared.player.RepeatMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

/**
 * Desktop playback on javax.sound.sampled.
 *
 * Decoding goes through the Java Sound SPI providers on the classpath:
 * mp3spi (MP3), vorbisspi (Ogg Vorbis) and the JDK's own PCM readers
 * (WAV/AIFF). Queue order, shuffle/repeat and end-of-track behaviour come
 * from the shared [QueueNavigator], so desktop and Android advance the queue
 * identically.
 *
 * Deliberately not shared: audio output, seeking granularity and buffering.
 * Android keeps Media3/ExoPlayer, its exclusive USB path and DSP/native
 * processors — that is exactly the platform-specific part of playback.
 */
class JavaSoundPlayer : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var line: SourceDataLine? = null

    /** Bumped on every (re)start so a superseded render loop exits quietly. */
    private var generation = 0

    override fun play(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        _state.update {
            it.copy(
                queue = tracks,
                index = index,
                isPlaying = true,
                isBuffering = true,
                positionMs = 0L,
                durationMs = tracks[index].durationMs,
                error = null,
            )
        }
        startStreaming(0L)
    }

    override fun togglePlayPause() {
        if (_state.value.current == null) return
        _state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    override fun next() {
        val snapshot = _state.value
        val next = QueueNavigator.nextIndex(
            size = snapshot.queue.size,
            currentIndex = snapshot.index,
            repeatMode = snapshot.repeatMode,
            shuffle = snapshot.shuffle,
            autoAdvance = false,
        )
        if (next < 0) stopPlayback() else switchTo(next)
    }

    override fun previous() {
        val snapshot = _state.value
        val previous = QueueNavigator.previousIndex(
            size = snapshot.queue.size,
            currentIndex = snapshot.index,
            positionMs = snapshot.positionMs,
            repeatMode = snapshot.repeatMode,
            shuffle = snapshot.shuffle,
        )
        if (previous < 0) stopPlayback() else switchTo(previous)
    }

    override fun seekTo(positionMs: Long) {
        if (_state.value.current == null) return
        val target = positionMs.coerceAtLeast(0L)
        _state.update { it.copy(positionMs = target) }
        startStreaming(target)
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        line?.let { applyVolume(it, clamped) }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    override fun cycleRepeatMode() {
        _state.update { it.copy(repeatMode = QueueNavigator.cycle(it.repeatMode)) }
    }

    override fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(shuffle = enabled) }
    }

    override fun stop() {
        stopPlayback()
    }

    /** Closes the output line and stops the render scope. */
    fun release() {
        stopPlayback()
        scope.cancel()
    }

    private fun switchTo(index: Int) {
        val snapshot = _state.value
        val track = snapshot.queue.getOrNull(index) ?: return
        _state.update {
            it.copy(
                index = index,
                positionMs = 0L,
                durationMs = track.durationMs,
                isPlaying = true,
                error = null,
            )
        }
        startStreaming(0L)
    }

    private fun stopPlayback() {
        streamJob?.cancel()
        streamJob = null
        generation++
        closeLine()
        _state.update { it.copy(isPlaying = false, isBuffering = false, positionMs = 0L) }
    }

    private fun startStreaming(startMs: Long) {
        streamJob?.cancel()
        closeLine()
        generation++
        val myGeneration = generation
        val track = _state.value.current ?: return
        _state.update { it.copy(isBuffering = true, error = null) }
        streamJob = scope.launch { render(track, startMs, myGeneration) }
    }

    private suspend fun render(track: Track, startMs: Long, myGeneration: Int) {
        var audioStream: AudioInputStream? = null
        var dataLine: SourceDataLine? = null
        var completed = false
        try {
            audioStream = openAudioStream(track)
            val format = audioStream.format
            dataLine = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) as? SourceDataLine
            if (dataLine == null) {
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = "No audio output line for ${format.encoding} (${format.sampleRate.toInt()} Hz).",
                    )
                }
                return
            }
            dataLine.open(format)
            dataLine.start()
            line = dataLine
            applyVolume(dataLine, _state.value.volume)

            if (startMs > 0L) skipFrames(audioStream, format, startMs)
            if (_state.value.durationMs <= 0L) {
                val estimated = estimateDuration(audioStream, format)
                if (estimated > 0L) _state.update { it.copy(durationMs = estimated) }
            }
            _state.update { it.copy(isBuffering = false) }

            val frameSize = format.frameSize.coerceAtLeast(1)
            val sampleRate = format.sampleRate.coerceAtLeast(1f)
            val buffer = ByteArray(maxOf(frameSize * 2048, 8192))
            var framesRendered = (startMs * sampleRate / 1000.0).toLong()
            var linePaused = false

            while (currentCoroutineContext().isActive && myGeneration == generation) {
                if (!_state.value.isPlaying) {
                    if (!linePaused) {
                        runCatching { dataLine.stop() }
                        linePaused = true
                    }
                    delay(40)
                    continue
                }
                if (linePaused) {
                    runCatching { dataLine.start() }
                    linePaused = false
                }
                val read = audioStream.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    completed = true
                    break
                }
                var offset = 0
                while (offset < read && myGeneration == generation) {
                    if (!_state.value.isPlaying) break
                    val written = dataLine.write(buffer, offset, read - offset)
                    if (written <= 0) break
                    offset += written
                    framesRendered += written / frameSize
                    _state.update { it.copy(positionMs = (framesRendered * 1000.0 / sampleRate).toLong()) }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (myGeneration == generation) {
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        } finally {
            runCatching { dataLine?.drain() }
            runCatching { dataLine?.close() }
            runCatching { audioStream?.close() }
            if (line === dataLine) line = null
        }
        if (completed && myGeneration == generation) {
            scope.launch { advanceAfterTrackEnd() }
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
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        } else {
            switchTo(next)
        }
    }

    private fun closeLine() {
        val current = line ?: return
        line = null
        runCatching { current.stop() }
        runCatching { current.flush() }
        runCatching { current.close() }
    }

    private fun openAudioStream(track: Track): AudioInputStream {
        val raw: InputStream = when {
            track.source.startsWith("file:") -> File(URI(track.source)).inputStream()
            track.source.startsWith("http://") || track.source.startsWith("https://") ->
                URI(track.source).toURL().openStream()
            else -> File(track.source).inputStream()
        }
        // Buffered so MP3/Vorbis decoders can probe and seek the header.
        return AudioSystem.getAudioInputStream(BufferedInputStream(raw))
    }

    private fun skipFrames(stream: AudioInputStream, format: AudioFormat, startMs: Long) {
        val targetFrames = (startMs * format.sampleRate / 1000.0).toLong()
        var remaining = targetFrames * format.frameSize.coerceAtLeast(1)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun estimateDuration(stream: AudioInputStream, format: AudioFormat): Long {
        val frames = stream.frameLength
        if (frames <= 0L || format.frameRate <= 0f) return 0L
        return (frames / format.frameRate * 1000f).toLong()
    }

    private fun applyVolume(dataLine: SourceDataLine, volume: Float) {
        runCatching {
            val control = dataLine.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val gainDb = if (volume <= 0.0001f) {
                control.minimum
            } else {
                (20.0 * log10(volume.toDouble())).toFloat().coerceIn(control.minimum, control.maximum)
            }
            control.value = gainDb
        }
    }
}
