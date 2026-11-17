package com.itzfallenme.katmusic.shared.player

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE }

/** Everything the shared UI needs to draw a player. */
data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val error: String? = null,
    val sleepTimerRemainingMs: Long? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = queue.isNotEmpty()
    val progressFraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * The one playback contract the shared UI drives. Android backs it with the
 * existing Media3/ExoPlayer service bridge; desktop backs it with
 * javax.sound.sampled. Neither platform is replaced by a stub.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>

    fun play(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0L)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun cycleRepeatMode()
    /** Stop playback after the selected number of minutes; zero cancels. */
    fun setSleepTimerMinutes(minutes: Int)
    fun stop()
}

/**
 * Pure queue arithmetic shared by every platform player: shuffle, repeat and
 * end-of-queue behaviour live here instead of being reimplemented per engine.
 */
object QueueNavigator {

    /**
     * Index to play after the current one, or -1 when playback should stop.
     *
     * @param autoAdvance true when the current track simply ended (repeat ONE
     *   replays it), false for an explicit "next" tap (which moves on).
     */
    fun nextIndex(
        size: Int,
        currentIndex: Int,
        repeatMode: RepeatMode,
        shuffle: Boolean,
        autoAdvance: Boolean,
        random: Random = Random.Default,
    ): Int {
        if (size <= 0) return -1
        if (autoAdvance && repeatMode == RepeatMode.ONE) return currentIndex.coerceIn(0, size - 1)
        val candidate = if (shuffle && size > 1) randomShuffledIndex(size, currentIndex, random) else currentIndex + 1
        return when {
            candidate in 0 until size -> candidate
            repeatMode == RepeatMode.ALL -> if (shuffle) randomShuffledIndex(size, -1, random) else 0
            else -> -1
        }
    }

    /**
     * Index to play for "previous": restart the current track when it is more
     * than [restartThresholdMs] in (the behaviour every music player has), and
     * otherwise step back, honouring repeat-all at the queue start.
     */
    fun previousIndex(
        size: Int,
        currentIndex: Int,
        positionMs: Long,
        repeatMode: RepeatMode,
        shuffle: Boolean,
        restartThresholdMs: Long = 3_000L,
        random: Random = Random.Default,
    ): Int {
        if (size <= 0) return -1
        if (positionMs > restartThresholdMs) return currentIndex.coerceIn(0, size - 1)
        val candidate = if (shuffle && size > 1) randomShuffledIndex(size, currentIndex, random) else currentIndex - 1
        return when {
            candidate in 0 until size -> candidate
            repeatMode == RepeatMode.ALL -> size - 1
            else -> 0
        }
    }

    fun cycle(mode: RepeatMode): RepeatMode = when (mode) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    private fun randomShuffledIndex(size: Int, avoid: Int, random: Random): Int {
        if (size <= 1) return 0
        var candidate = random.nextInt(size)
        var guard = 0
        while (candidate == avoid && guard < 8) {
            candidate = random.nextInt(size)
            guard++
        }
        return candidate
    }
}
