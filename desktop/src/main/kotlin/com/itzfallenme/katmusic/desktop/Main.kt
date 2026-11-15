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
import com.itzfallenme.katmusic.desktop.library.DesktopMediaLibrary
import com.itzfallenme.katmusic.desktop.library.DesktopPlatformActions
import com.itzfallenme.katmusic.desktop.playback.JavaSoundPlayer
import com.itzfallenme.katmusic.shared.ui.KatMusicApp

/**
 * KatMusic for desktop. The window hosts the exact same Compose UI Android
 * runs ([KatMusicApp]); only playback, file scanning and settings storage are
 * desktop implementations.
 */
fun main() = application {
    // Coil 3 needs an explicit singleton loader outside Android. Local files
    // and folder covers are the only artwork sources here, so the default
    // components are enough.
    SingletonImageLoader.setSafe { context: PlatformContext -> ImageLoader.Builder(context).build() }

    val settingsStore = remember { DesktopSettingsStore() }
    val library = remember { DesktopMediaLibrary(settingsStore) }
    val platformActions = remember { DesktopPlatformActions(settingsStore, library) }
    val player = remember { JavaSoundPlayer() }

    DisposableEffect(Unit) {
        onDispose { player.release() }
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
        )
    }
}
