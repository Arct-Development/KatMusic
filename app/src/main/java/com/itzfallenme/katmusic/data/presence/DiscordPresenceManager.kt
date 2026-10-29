package com.itzfallenme.katmusic.data.presence

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.itzfallenme.katmusic.data.SettingsRepository
import com.itzfallenme.katmusic.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Publishes the playing track to Discord.
 *
 * Pushes happen on a signature of what the card shows (track, artist, play state), never
 * on the position ticker, and at most once per [DiscordPresence.PUSH_INTERVAL_MS]. A
 * missing Discord app costs one cheap service lookup per [DiscordPresence.RETRY_INTERVAL_MS].
 * When playback stops, or the user switches presence off, the card is cleared.
 *
 * Playback is never gated by any of this: every failure is swallowed and logged at debug
 * level only.
 */
class DiscordPresenceManager(
    private val context: Context,
    private val player: PlayerController,
    private val settings: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private var transport: DiscordRpcTransport? = null
    private var started = false
    private var observer: Job? = null

    private var lastSignature = ""
    private var lastPushAt = 0L
    private var lastAttemptAt = 0L
    private var published = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        observer = scope.launch {
            player.state.collect { evaluate() }
        }
        scope.launch {
            // A slow heartbeat keeps the card from going stale if an event was missed
            // (Discord restarted, a push dropped, the app resumed from background).
            while (isActive) {
                delay(DiscordPresence.PUSH_INTERVAL_MS)
                evaluate(force = false)
            }
        }
        settings.state.let { flow ->
            scope.launch { flow.collect { evaluate(force = true) } }
        }
    }

    private fun evaluate(force: Boolean = false) {
        scope.launch { publish(force) }
    }

    private suspend fun publish(force: Boolean) {
        val config = settings.state.value
        val wanted = config.discordPresenceEnabled && config.discordAppId.isNotBlank()
        val state = player.state.value
        val now = SystemClock.elapsedRealtime()

        lock.withLock {
            if (!wanted) {
                transport?.let { if (published) it.clearActivity() }
                transport?.close()
                transport = null
                published = false
                lastSignature = ""
                return
            }

            val track = state.track
            val playing = state.playing || state.buffering
            if (track == null || !playing) {
                if (published) {
                    transport?.clearActivity()
                    published = false
                }
                lastSignature = ""
                return
            }

            // Discord runs the progress bar from the timestamps, so an unchanged card
            // needs no repush at all - position ticks never reach the wire.
            val signature = DiscordPresence.signature(track, playing, state.durationMs)
            if (!force && signature == lastSignature) return

            val appId = config.discordAppId.trim().toLongOrNull()
            if (appId == null || appId <= 0L) {
                Log.d(TAG, "Discord presence is on but the application ID is not a number")
                return
            }

            if (transport?.isOpen != true) {
                if (now - lastAttemptAt < DiscordPresence.RETRY_INTERVAL_MS && lastAttemptAt > 0L) return
                lastAttemptAt = now
                transport?.close()
                transport = DiscordRpcTransport.connectOrNull(context, appId)
            }

            val activity = DiscordPresence.buildActivity(
                track = track,
                playing = playing,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                nowMs = System.currentTimeMillis()
            )
            val sent = transport?.setActivity(activity) == true
            if (sent) {
                published = true
                lastSignature = signature
                lastPushAt = now
            } else {
                transport?.close()
                transport = null
            }
        }
    }

    /** Called when the user toggles the switch so the change lands immediately. */
    fun refresh() = evaluate(force = true)

    fun stop() {
        scope.launch {
            lock.withLock {
                if (published) transport?.clearActivity()
                transport?.close()
                transport = null
                published = false
            }
            observer?.cancel()
            started = false
        }
    }

    private companion object {
        const val TAG = "DiscordPresence"
    }
}
