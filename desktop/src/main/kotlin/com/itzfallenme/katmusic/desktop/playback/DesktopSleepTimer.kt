package com.itzfallenme.katmusic.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Local monotonic sleep timer shared by the Desktop playback engines. */
internal class DesktopSleepTimer(
    private val scope: CoroutineScope,
    private val onRemainingChanged: (Long?) -> Unit,
    private val onExpire: () -> Unit,
) {
    private var job: Job? = null
    private var deadlineNanos: Long? = null

    @Synchronized
    fun setMinutes(minutes: Int) {
        require(minutes >= 0) { "Timer duration cannot be negative" }
        job?.cancel()
        job = null
        deadlineNanos = null
        if (minutes == 0) {
            onRemainingChanged(null)
            return
        }

        val deadline = System.nanoTime() + minutes.toLong() * 60L * 1_000_000_000L
        deadlineNanos = deadline
        onRemainingChanged(minutes.toLong() * 60L * 1_000L)
        job = scope.launch {
            while (true) {
                val remaining = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                if (remaining == 0L) {
                    synchronized(this@DesktopSleepTimer) {
                        if (deadlineNanos == deadline) {
                            deadlineNanos = null
                            job = null
                            onRemainingChanged(null)
                            onExpire()
                        }
                    }
                    break
                }
                onRemainingChanged(remaining)
                delay(minOf(1_000L, remaining))
            }
        }
    }
}
