package com.itzfallenme.katmusic.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.itzfallenme.katmusic.MainActivity
import com.itzfallenme.katmusic.ServiceLocator

/** Foreground media session so playback survives the app being backgrounded. */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        val player = ServiceLocator.player.player
        mediaSession = MediaSession.Builder(this, player).build()
        player.skipSilenceEnabled = ServiceLocator.settings.state.value.skipSilence
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        // The ExoPlayer instance is owned by PlayerController and reused when the
        // service restarts, so only the session is released here.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ServiceLocator.init(context)
        ServiceLocator.player.setSleepTimer(0)
        ServiceLocator.player.player.pause()
    }
}

internal fun nowPlayingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("open_player", true)
    }
    return PendingIntent.getActivity(
        context,
        42,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
