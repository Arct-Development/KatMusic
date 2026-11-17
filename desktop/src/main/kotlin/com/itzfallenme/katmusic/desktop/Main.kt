package com.itzfallenme.katmusic.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.itzfallenme.katmusic.desktop.data.DesktopDownloadsRepository
import com.itzfallenme.katmusic.desktop.data.DesktopHomeFeedRepository
import com.itzfallenme.katmusic.desktop.data.DesktopListeningStats
import com.itzfallenme.katmusic.desktop.data.DesktopPodcastsRepository
import com.itzfallenme.katmusic.desktop.data.M3uPlaylistsRepository
import com.itzfallenme.katmusic.desktop.data.MpvPlayer
import com.itzfallenme.katmusic.desktop.data.NewPipeStreamResolver
import com.itzfallenme.katmusic.desktop.data.YouTubeSessionStore
import com.itzfallenme.katmusic.desktop.jam.DesktopJamManager
import com.itzfallenme.katmusic.desktop.library.DesktopMediaLibrary
import com.itzfallenme.katmusic.desktop.library.DesktopPlatformActions
import com.itzfallenme.katmusic.desktop.lyrics.DesktopLyricsProvider
import com.itzfallenme.katmusic.desktop.playback.JavaSoundPlayer
import com.itzfallenme.katmusic.desktop.ui.DesktopEqualizerSection
import com.itzfallenme.katmusic.desktop.ui.DesktopJamScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopStatsSection
import com.itzfallenme.katmusic.desktop.ui.YouTubeLoginSection
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.ui.KatMusicApp
import kotlinx.coroutines.launch

/**
 * KatMusic for desktop. The window hosts the exact same Compose UI Android
 * runs ([KatMusicApp]); playback, file scanning, downloads, playlists, the
 * home feed and settings storage are desktop implementations.
 */
fun main() = application {
    // Coil 3 needs an explicit singleton loader outside Android. Local files,
    // folder covers and feed thumbnails are the artwork sources here, so the
    // default components are enough.
    SingletonImageLoader.setSafe { context: PlatformContext -> ImageLoader.Builder(context).build() }

    val settingsStore = remember { DesktopSettingsStore() }
    val library = remember { DesktopMediaLibrary(settingsStore) }
    val platformActions = remember { DesktopPlatformActions(settingsStore, library) }
    val lyrics = remember { DesktopLyricsProvider() }
    val youtubeSession = remember { YouTubeSessionStore() }
    // One resolver shared by playback and downloads so both use the same
    // signed-in session (or anonymous access when no phone is paired).
    val streamResolver = remember {
        NewPipeStreamResolver(cookieHeader = youtubeSession::cookieHeader)
    }
    val downloads = remember { DesktopDownloadsRepository(resolver = streamResolver) }
    val playlists = remember { M3uPlaylistsRepository() }
    val homeFeed = remember { DesktopHomeFeedRepository(resolver = streamResolver) }
    val podcasts = remember { DesktopPodcastsRepository() }
    val stats = remember { DesktopListeningStats() }
    val scope = rememberCoroutineScope()

    // Streaming (Opus/AAC/HLS) needs libmpv; when it is not installed the app
    // falls back to the Java Sound engine, which covers local MP3/Ogg/WAV/AIFF.
    val player: PlayerController = remember {
        val mpv = MpvPlayer { videoId -> streamResolver.resolveAudioUrl(videoId).getOrNull() }
        if (mpv.isAvailable) mpv else JavaSoundPlayer()
    }

    val jam = remember {
        DesktopJamManager(
            player = player,
            resolveAudio = { videoId -> streamResolver.resolveAudioUrl(videoId) },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            jam.close()
            (player as? MpvPlayer)?.release()
            (player as? JavaSoundPlayer)?.release()
        }
    }
    LaunchedEffect(Unit) {
        library.refresh()
    }
    LaunchedEffect(player) {
        var lastTrackId: String? = null
        player.state.collect { state ->
            val track = state.current
            if (track != null && track.id != lastTrackId) {
                lastTrackId = track.id
                stats.record(track)
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "KatMusic",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        KatMusicApp(
            settingsStore = settingsStore,
            library = library,
            player = player,
            platform = platformActions,
            lyrics = lyrics,
            downloads = downloads,
            playlists = playlists,
            homeFeed = homeFeed,
            podcasts = podcasts,
            jamContent = { DesktopJamScreen(jam) },
            onImportPlaylist = {
                scope.launch {
                    platformActions.choosePlaylistFile()?.let { playlists.importM3u(it) }
                }
            },
            settingsExtra = {
                YouTubeLoginSection(youtubeSession)
                DesktopEqualizerSection(settingsStore, player)
                DesktopStatsSection(stats)
            },
        )
    }
}
