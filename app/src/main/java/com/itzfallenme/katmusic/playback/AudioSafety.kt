package com.itzfallenme.katmusic.playback

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Stereo-to-mono downmix for listening on a single earbud.
 *
 * Implemented on [BaseAudioProcessor] rather than delegating to Media3's
 * ChannelMixingAudioProcessor: the wrapper only needs one fixed matrix, and
 * owning the buffer replacement keeps the processor's "active" state in one
 * place, which matters because the pipeline reads it after every flush. Only
 * 16-bit and float PCM are accepted; anything else passes through untouched.
 */
class MonoDownmixProcessor : BaseAudioProcessor() {

    /** Mirrors the "Mono audio" setting; flipping it takes effect on the next track. */
    @Volatile
    var enabled: Boolean = false

    private var active = false
    private var inputChannels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var bytesPerSample = 2

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        inputChannels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        bytesPerSample = if (encoding == C.ENCODING_PCM_16BIT) 2 else 4
        active = enabled && supported && inputChannels == 2
        return if (active) {
            AudioFormat(inputAudioFormat.sampleRate, 1, inputAudioFormat.encoding)
        } else {
            inputAudioFormat
        }
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) return
        val frameCount = inputBuffer.remaining() / (bytesPerSample * inputChannels)
        if (frameCount == 0) return
        val output = replaceOutputBuffer(frameCount * bytesPerSample)
        if (encoding == C.ENCODING_PCM_16BIT) {
            repeat(frameCount) {
                val left = inputBuffer.short.toInt()
                val right = inputBuffer.short.toInt()
                output.putShort(stereoToMono(left, right))
            }
        } else {
            repeat(frameCount) {
                val left = inputBuffer.float
                val right = inputBuffer.float
                output.putFloat(stereoToMono(left, right))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}

/** Averaged 16-bit sample; equal channels come through unchanged. */
internal fun stereoToMono(left: Int, right: Int): Short =
    ((left + right) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

/** Averaged float sample, in the same -1..1 range as the input. */
internal fun stereoToMono(left: Float, right: Float): Float = (left + right) / 2f

/** Music-stream volume the guard allows for [percent] of the device maximum. */
fun volumeCapIndex(percent: Int, maxIndex: Int): Int {
    if (maxIndex <= 0) return 0
    val safePercent = percent.coerceIn(1, 100)
    // Never round down to silence: a limit should quiet the music, not mute it.
    return ((maxIndex.toLong() * safePercent + 50) / 100).toInt().coerceIn(1, maxIndex)
}

/**
 * Keeps the media stream under a chosen ceiling.
 *
 * Android has no per-app volume cap, so this trims the shared music stream
 * whenever the user (or a headset) pushes it past the limit while a guarded
 * session is playing. The cap is a percentage of the device's own maximum, so
 * it behaves the same on phones with 15 steps and on ones with 100.
 */
class VolumeGuard(context: Context) {

    private val audio: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** True when the stream had to be pulled back down. */
    fun enforce(percent: Int): Boolean {
        val manager = audio ?: return false
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = volumeCapIndex(percent, max)
        val current = runCatching { manager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        if (current <= cap) return false
        runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0) }
        return true
    }
}
