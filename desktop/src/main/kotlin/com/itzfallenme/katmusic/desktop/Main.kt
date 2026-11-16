package com.itzfallenme.katmusic.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.itzfallenme.katmusic.desktop.data.DesktopDownloadsRepository
import com.itzfallenme.katmusic.desktop.data.DesktopHomeFeedRepository
import com.itzfallenme.katmusic.desktop.data.M3uPlaylistsRepository
import com.itzfallenme.katmusic.desktop.data.MpvPlayer
import com.itzfallenme.katmusic.desktop.library.DesktopMediaLibrary
import com.itzfallenme.katmusic.desktop.library.DesktopPlatformActions
import com.itzfallenme.katmusic.desktop.lyrics.DesktopLyricsProvider
import com.itzfallenme.katmusic.desktop.playback.JavaSoundPlayer
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.ui.KatMusicApp

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
    val downloads = remember { DesktopDownloadsRepository() }
    val playlists = remember { M3uPlaylistsRepository() }
    val homeFeed = remember { DesktopHomeFeedRepository() }

    // Streaming (Opus/AAC/HLS) needs libmpv; when it is not installed the app
    // falls back to the Java Sound engine, which covers local MP3/Ogg/WAV/AIFF.
    val player: PlayerController = remember {
        val mpv = MpvPlayer()
        if (mpv.isAvailable) mpv else JavaSoundPlayer()
    }

    DisposableEffect(Unit) {
        onDispose {
            (player as? MpvPlayer)?.release()
            (player as? JavaSoundPlayer)?.release()
        }
    }
    LaunchedEffect(Unit) {
        library.refresh()
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
        )
    }
}
