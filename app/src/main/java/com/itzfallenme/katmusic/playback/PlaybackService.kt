package com.itzfallenme.katmusic.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.itzfallenme.katmusic.R
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.itzfallenme.katmusic.MainActivity
import com.itzfallenme.katmusic.ServiceLocator

/**
 * Foreground media session, and the browsable library Android Auto reads.
 *
 * Playback lives in [PlayerController] and the session only wraps its ExoPlayer, so the
 * in-car experience is the same player as the phone UI. Browsing is served from the local
 * library through [AutoLibrary]; selecting a track from a playlist queues the whole
 * playlist rather than a single song.
 */
class PlaybackService : MediaLibraryService() {

    private var librarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        val player = ServiceLocator.player.player
        player.skipSilenceEnabled = ServiceLocator.settings.state.value.skipSilence
        // A dedicated, quiet playback channel: a LOW-importance channel hides the
        // lockscreen media card on several ROMs, and the default one is easy to
        // silence by accident.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("katmusic-playback")
                .setChannelName(R.string.notification_channel_playback)
                .setNotificationId(1102)
                .build()
        )
        librarySession = MediaLibrarySession.Builder(this, player, AutoBrowserCallback())
            .setSessionActivity(nowPlayingIntent(this))
            .build()
        // Shuffle and repeat are not part of the stock transport, so they are
        // exposed as custom commands and appended to the expanded notification.
        librarySession?.setCustomLayout(
            listOf(
                CommandButton.Builder()
                    .setDisplayName("Shuffle")
                    .setIconResId(R.drawable.ic_shuffle)
                    .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Repeat")
                    .setIconResId(R.drawable.ic_repeat)
                    .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                    .build()
            )
        )
        // Safety net for the startForegroundService contract: if playback never
        // actually begins (resolution failed, the user backed out while the
        // stream was loading), stop instead of staying alive past the 5s window
        // and being killed with ForegroundServiceDidNotStartInTimeException.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // playWhenReady, not isPlaying: buffering is not a reason to stop.
            if (librarySession?.player?.playWhenReady != true) {
                com.itzfallenme.katmusic.util.AppLog.w(
                    "Playback",
                    "service started but playback did not begin; stopping to satisfy the foreground contract"
                )
                stopSelf()
            }
        }, 3_000L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = librarySession?.player
        if (player == null || !player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        // The ExoPlayer instance belongs to PlayerController and is reused when the service
        // restarts, so only the session is released here.
        librarySession?.release()
        librarySession = null
        ServiceLocator.player.onServiceDestroyed()
        super.onDestroy()
    }

    /** Serves the local library to external browsers. */
    private inner class AutoBrowserCallback : MediaLibrarySession.Callback {

            override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> ServiceLocator.player.toggleShuffle()
                ACTION_REPEAT -> ServiceLocator.player.cycleRepeat()
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(AutoLibrary.browseRoot(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = AutoLibrary.children(ServiceLocator.library, parentId)
            val paged = if (pageSize > 0) {
                children.drop(page * pageSize).take(pageSize)
            } else {
                children
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = AutoLibrary.item(ServiceLocator.library, mediaId)
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        /**
         * Auto sends only the item the user tapped. Rebuilding its siblings here turns
         * that tap into a queue, matching the behaviour of the phone UI.
         */
        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val tapped = mediaItems.getOrNull(startIndex)
            val group = tapped?.mediaMetadata?.extras?.getString(AutoLibrary.EXTRA_GROUP)
            val tracks = if (group != null) AutoLibrary.group(group) else emptyList()
            val index = tracks.indexOfFirst { it.id == tapped?.mediaId }
            if (tracks.isEmpty() || index < 0) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                )
            }
            val queue = tracks.map { AutoLibrary.mediaItemFor(it, group.orEmpty()) }
            ServiceLocator.player.setExternalQueue(tracks, index)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(queue, index, 0L)
            )
        }
    }
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ServiceLocator.init(context)
        ServiceLocator.player.setSleepTimer(0)
        ServiceLocator.player.player.pause()
    }
}

private const val ACTION_SHUFFLE = "com.itzfallenme.katmusic.SHUFFLE"
private const val ACTION_REPEAT = "com.itzfallenme.katmusic.REPEAT"

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
