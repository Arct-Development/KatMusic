package com.itzfallenme.katmusic.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.itzfallenme.katmusic.desktop.data.DesktopDownloadsRepository
import com.itzfallenme.katmusic.desktop.data.DesktopHomeFeedRepository
import com.itzfallenme.katmusic.desktop.data.DesktopLastFmRepository
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
import com.itzfallenme.katmusic.desktop.ui.DesktopDiscoverScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopArtistsScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopJamScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopLibrarySettingsSection
import com.itzfallenme.katmusic.desktop.ui.DesktopLastFmSection
import com.itzfallenme.katmusic.desktop.ui.DesktopNewReleasesScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopPlaybackIntegrationSection
import com.itzfallenme.katmusic.desktop.ui.DesktopSocialScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopSongCodeDialog
import com.itzfallenme.katmusic.desktop.ui.DesktopStatsScreen
import com.itzfallenme.katmusic.desktop.ui.DesktopYouTubePlaylistImportDialog
import com.itzfallenme.katmusic.desktop.ui.YouTubeLoginSection
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.ui.KatMusicApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    val lastFm = remember { DesktopLastFmRepository() }
    val stats = remember { DesktopListeningStats() }
    val libraryTracks by library.tracks.collectAsState()
    val scope = rememberCoroutineScope()
    var songCodeTrack by remember { mutableStateOf<Track?>(null) }
    var showYouTubePlaylistImport by remember { mutableStateOf(false) }

    // Streaming (Opus/AAC/HLS) needs libmpv; when it is not installed the app
    // falls back to the Java Sound engine, which covers local MP3/Ogg/WAV/AIFF.
    val player: PlayerController = remember {
        val mpv = MpvPlayer { videoId -> streamResolver.resolveAudioUrl(videoId).getOrNull() }
        if (mpv.isAvailable) mpv else JavaSoundPlayer()
    }
    val systemTray = remember(player) { DesktopSystemTray(player) }
    val mediaKeys = remember(player) { DesktopMediaKeys(player) }
    val playerState by player.state.collectAsState()

    val jam = remember {
        DesktopJamManager(
            player = player,
            resolveAudio = { videoId -> streamResolver.resolveAudioUrl(videoId) },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            jam.close()
            systemTray.close()
            mediaKeys.close()
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
    LaunchedEffect(player, systemTray) {
        var lastTrackId: String? = null
        player.state.collect { state ->
            val track = state.current
            val changed = track?.id != lastTrackId
            if (changed) lastTrackId = track?.id
            systemTray.update(track, state.isPlaying, changed)
        }
    }
    LaunchedEffect(player, lastFm) {
        combine(player.state.map { it.current }, lastFm.connection) { track, connection ->
            track to connection.connected
        }
            .distinctUntilChangedBy { (track, connected) -> track?.id to connected }
            .collectLatest { (track, connected) ->
                if (!connected || track == null || track.artist.equals("Podcast", ignoreCase = true)) {
                    return@collectLatest
                }
                val ready = player.state.first {
                    it.current?.id != track.id || it.isPlaying
                }
                if (ready.current?.id != track.id || !ready.isPlaying) return@collectLatest
                lastFm.updateNowPlaying(track.artist, track.title, track.album)
                val thresholdMs = (if (track.durationMs > 0L) track.durationMs / 2L else 180_000L)
                    .coerceAtMost(4 * 60_000L)
                    .coerceAtLeast(30_000L)
                val scrobblePoint = player.state.first { state ->
                    state.current?.id != track.id || (state.isPlaying && state.positionMs >= thresholdMs)
                }
                if (scrobblePoint.current?.id == track.id && scrobblePoint.positionMs >= thresholdMs) {
                    val startedAt = System.currentTimeMillis() / 1_000L -
                        (scrobblePoint.positionMs / 1_000L)
                    lastFm.scrobble(track.artist, track.title, track.album, startedAt)
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
            statsContent = { DesktopStatsScreen(stats) },
            discoverContent = {
                DesktopDiscoverScreen(
                    tracks = libraryTracks,
                    playerState = playerState,
                    feed = homeFeed,
                    stats = stats,
                    playlists = playlists,
                    onPlay = { queue, index -> player.play(queue, index) },
                    initialRegion = remember { settingsStore.trendingRegion() },
                    onRegionChanged = settingsStore::setTrendingRegion,
                )
            },
            newReleasesContent = {
                DesktopNewReleasesScreen(
                    feed = homeFeed,
                    onPlay = { queue, index -> player.play(queue, index) },
                )
            },
            socialContent = { DesktopSocialScreen(lastFm) },
            artistsContent = {
                DesktopArtistsScreen(
                    feed = homeFeed,
                    onPlay = { queue, index -> player.play(queue, index) },
                )
            },
            onImportPlaylist = {
                scope.launch {
                    platformActions.choosePlaylistFile()?.let { playlists.importM3u(it) }
                }
            },
            onImportOnlinePlaylist = { showYouTubePlaylistImport = true },
            settingsExtra = {
                DesktopLibrarySettingsSection(
                    store = settingsStore,
                    onChooseFolder = { platformActions.chooseLibraryFolder() },
                    onRefresh = { library.refresh() },
                )
                YouTubeLoginSection(youtubeSession)
                DesktopEqualizerSection(settingsStore, player)
                DesktopPlaybackIntegrationSection(globalMediaKeys = mediaKeys.globalHookActive)
                DesktopLastFmSection(lastFm)
            },
            onShareSongCode = { track -> songCodeTrack = track },
            onShareLyrics = { track, lyrics ->
                runCatching { DesktopLyricsShare.export(window, track, lyrics) }
                    .onFailure { error ->
                        javax.swing.JOptionPane.showMessageDialog(
                            window,
                            error.message ?: "Couldn't save the lyrics card",
                            "KatMusic",
                            javax.swing.JOptionPane.ERROR_MESSAGE,
                        )
                    }
            },
        )
        songCodeTrack?.let { track ->
            DesktopSongCodeDialog(
                track = track,
                onDismiss = { songCodeTrack = null },
            )
        }
        if (showYouTubePlaylistImport) {
            DesktopYouTubePlaylistImportDialog(
                feed = homeFeed,
                playlists = playlists,
                onDismiss = { showYouTubePlaylistImport = false },
            )
        }
    }
}
