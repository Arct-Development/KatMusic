package com.itzfallenme.katmusic.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
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
        librarySession = MediaLibrarySession.Builder(this, player, AutoBrowserCallback())
            .setSessionActivity(nowPlayingIntent(this))
            .build()
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
        super.onDestroy()
    }

    /** Serves the local library to external browsers. */
    private inner class AutoBrowserCallback : MediaLibrarySession.Callback {

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
