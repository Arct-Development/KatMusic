package com.itzfallenme.katmusic.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itzfallenme.katmusic.shared.data.DownloadsRepository
import com.itzfallenme.katmusic.shared.data.HomeFeedRepository
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
import com.itzfallenme.katmusic.shared.library.MediaLibrary
import com.itzfallenme.katmusic.shared.library.PlatformActions
import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.settings.SettingsStore
import com.itzfallenme.katmusic.shared.settings.toThemeUiState
import com.itzfallenme.katmusic.shared.ui.components.PlayerDock
import com.itzfallenme.katmusic.shared.ui.screens.CollectionScreen
import com.itzfallenme.katmusic.shared.ui.screens.DownloadsScreen
import com.itzfallenme.katmusic.shared.ui.screens.HomeScreen
import com.itzfallenme.katmusic.shared.ui.screens.LibraryScreen
import com.itzfallenme.katmusic.shared.ui.screens.LyricsScreen
import com.itzfallenme.katmusic.shared.ui.screens.NowPlayingScreen
import com.itzfallenme.katmusic.shared.ui.screens.PlaylistsScreen
import com.itzfallenme.katmusic.shared.ui.screens.QueueScreen
import com.itzfallenme.katmusic.shared.ui.screens.SearchScreen
import com.itzfallenme.katmusic.shared.ui.screens.SettingsScreen
import com.itzfallenme.katmusic.ui.components.MotionSpec
import com.itzfallenme.katmusic.ui.components.WindowSizeClass
import com.itzfallenme.katmusic.ui.components.rememberWindowSizeClass
import com.itzfallenme.katmusic.ui.theme.KatMusicTheme
import kotlinx.coroutines.launch

/** The shared top-level destinations. One navigation graph for both platforms. */
enum class KatMusicTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Library("library", "Library", Icons.Rounded.LibraryMusic),
    Search("search", "Search", Icons.Rounded.Search),
    Settings("settings", "Settings", Icons.Rounded.Settings),
}

const val NOW_PLAYING_ROUTE = "now_playing"
const val QUEUE_ROUTE = "queue"
const val COLLECTION_ROUTE = "collection"
const val LYRICS_ROUTE = "lyrics"
const val DOWNLOADS_ROUTE = "downloads"
const val PLAYLISTS_ROUTE = "playlists"

/**
 * What the collection screen should show. Held in composition state instead of
 * navigation arguments so the same graph works on both platforms without
 * platform-specific argument encoding.
 */
private data class CollectionTarget(
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val tracks: List<Track>,
)

/**
 * The whole KatMusic UI, shared verbatim by Android and desktop.
 *
 * Layout is the only thing that adapts: below 600dp the navigation is a
 * bottom bar (phones), at 600dp and wider it becomes a rail beside the
 * content (tablets, resized windows, desktop). The screens, design tokens,
 * components, navigation and player state are the same code.
 *
 * Platform behaviour arrives through [player], [library] and [platform];
 * audio engines, file access and storage stay in the platform modules.
 */
@Composable
fun KatMusicApp(
    settingsStore: SettingsStore,
    library: MediaLibrary,
    player: PlayerController,
    platform: PlatformActions,
    lyrics: LyricsProvider? = null,
    downloads: DownloadsRepository? = null,
    playlists: PlaylistsRepository? = null,
    homeFeed: HomeFeedRepository? = null,
    modifier: Modifier = Modifier,
) {
    val settings by settingsStore.settings.collectAsState()
    val themeState = remember(settings) { settings.toThemeUiState() }

    KatMusicTheme(themeState) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: KatMusicTab.Library.route
        val wideLayout = rememberWindowSizeClass() != WindowSizeClass.COMPACT
        val tracks by library.tracks.collectAsState()
        val scan by library.scan.collectAsState()
        val playerState by player.state.collectAsState()
        val scope = rememberCoroutineScope()
        var collection by remember { mutableStateOf<CollectionTarget?>(null) }
        var holderCollection by remember { mutableStateOf<com.itzfallenme.katmusic.shared.ui.screens.CollectionTargetHolder?>(null) }

        val visibleTabs = remember(homeFeed) {
            if (homeFeed != null) KatMusicTab.entries else KatMusicTab.entries.filter { it != KatMusicTab.Home }
        }

        val selectTab: (KatMusicTab) -> Unit = { tab ->
            if (currentRoute != tab.route) {
                navController.navigate(tab.route) {
                    popUpTo(KatMusicTab.Library.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        val openNowPlaying = {
            if (currentRoute != NOW_PLAYING_ROUTE) {
                navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true }
            }
        }
        val openQueue = {
            if (currentRoute != QUEUE_ROUTE) {
                navController.navigate(QUEUE_ROUTE) { launchSingleTop = true }
            }
        }
        val playQueue: (List<Track>, Int) -> Unit = { queue, index -> player.play(queue, index) }

        val content: @Composable () -> Unit = {
            AppNavHost(
                navController = navController,
                tracks = tracks,
                scan = scan,
                playerState = playerState,
                settings = settings,
                settingsStore = settingsStore,
                player = player,
                lyrics = lyrics,
                collection = collection,
                onOpenCollection = { target ->
                    collection = target
                    navController.navigate(COLLECTION_ROUTE) { launchSingleTop = true }
                },
                onPlay = playQueue,
                canChooseFolder = platform.canChooseFolder,
                onRefresh = { scope.launch { library.refresh() } },
                onChooseFolder = {
                    scope.launch {
                        platform.chooseLibraryFolder()
                        library.refresh()
                    }
                },
                downloads = downloads,
                playlists = playlists,
                homeFeed = homeFeed,
                onOpenHolderCollection = { target ->
                    holderCollection = target
                    collection = CollectionTarget(target.title, target.subtitle, target.artwork, target.tracks)
                    navController.navigate(COLLECTION_ROUTE) { launchSingleTop = true }
                },
            )
        }

        if (wideLayout) {
            Row(modifier = modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "K",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    visibleTabs.forEach { tab ->
                        NavigationRailEntry(
                            tab = tab,
                            selected = currentRoute == tab.route,
                            onClick = { selectTab(tab) },
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) { content() }
                    PlayerDock(
                        state = playerState,
                        player = player,
                        onExpand = openNowPlaying,
                        onOpenQueue = openQueue,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        } else {
            Column(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) { content() }
                PlayerDock(
                    state = playerState,
                    player = player,
                    onExpand = openNowPlaying,
                    onOpenQueue = openQueue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (!themeState.hideNavTabs) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        visibleTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { selectTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    tracks: List<Track>,
    scan: com.itzfallenme.katmusic.shared.library.ScanState,
    playerState: com.itzfallenme.katmusic.shared.player.PlayerState,
    settings: com.itzfallenme.katmusic.shared.settings.AppSettings,
    settingsStore: SettingsStore,
    player: PlayerController,
    lyrics: LyricsProvider?,
    collection: CollectionTarget?,
    onOpenCollection: (CollectionTarget) -> Unit,
    canChooseFolder: Boolean,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    downloads: DownloadsRepository?,
    playlists: PlaylistsRepository?,
    homeFeed: HomeFeedRepository?,
    onOpenHolderCollection: (com.itzfallenme.katmusic.shared.ui.screens.CollectionTargetHolder) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = KatMusicTab.Library.route,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { MotionSpec.forwardEnter() },
        exitTransition = { MotionSpec.forwardExit() },
        popEnterTransition = { MotionSpec.backEnter() },
        popExitTransition = { MotionSpec.backExit() },
    ) {
        composable(KatMusicTab.Home.route) {
            val feed = homeFeed
            if (feed == null) {
                navController.popBackStack()
            } else {
                HomeScreen(repository = feed, playerQueue = onPlay)
            }
        }
        composable(KatMusicTab.Library.route) {
            LibraryScreen(
                tracks = tracks,
                scan = scan,
                playerState = playerState,
                canChooseFolder = canChooseFolder,
                onRefresh = onRefresh,
                onChooseFolder = onChooseFolder,
                onPlay = onPlay,
                onOpenDownloads = downloads?.let {
                    { navController.navigate(DOWNLOADS_ROUTE) { launchSingleTop = true } }
                },
                onOpenPlaylists = playlists?.let {
                    { navController.navigate(PLAYLISTS_ROUTE) { launchSingleTop = true } }
                },
                onOpenAlbum = { album ->
                    onOpenCollection(
                        CollectionTarget(
                            title = album.album,
                            subtitle = "${album.artist} · ${album.tracks.size} songs",
                            artwork = album.artwork,
                            tracks = album.tracks,
                        ),
                    )
                },
                onOpenArtist = { artist ->
                    onOpenCollection(
                        CollectionTarget(
                            title = artist.artist,
                            subtitle = "${artist.tracks.size} songs · ${artist.albumCount} albums",
                            artwork = artist.artwork,
                            tracks = artist.tracks,
                        ),
                    )
                },
            )
        }
        composable(KatMusicTab.Search.route) {
            SearchScreen(tracks = tracks, playerState = playerState, onPlay = onPlay)
        }
        composable(KatMusicTab.Settings.route) {
            SettingsScreen(settings = settings, settingsStore = settingsStore)
        }
        composable(NOW_PLAYING_ROUTE) {
            NowPlayingScreen(
                state = playerState,
                player = player,
                onBack = { navController.popBackStack() },
                onOpenLyrics = lyrics?.let { provider ->
                    { navController.navigate(LYRICS_ROUTE) { launchSingleTop = true } }
                },
            )
        }
        composable(LYRICS_ROUTE) {
            LyricsScreen(
                state = playerState,
                provider = lyrics,
                onBack = { navController.popBackStack() },
            )
        }
        composable(QUEUE_ROUTE) {
            QueueScreen(
                state = playerState,
                onBack = { navController.popBackStack() },
                onPlayAt = { index -> player.play(playerState.queue, index) },
            )
        }
        composable(COLLECTION_ROUTE) {
            val target = collection
            if (target == null) {
                // The screen was restored after process death with no target;
                // returning to the library is the only sensible state.
                navController.popBackStack()
            } else {
                CollectionScreen(
                    title = target.title,
                    subtitle = target.subtitle,
                    artwork = target.artwork,
                    tracks = target.tracks,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onPlay = onPlay,
                )
            }
        }
        composable(DOWNLOADS_ROUTE) {
            val store = downloads
            if (store == null) {
                navController.popBackStack()
            } else {
                DownloadsScreen(
                    repository = store,
                    playerState = playerState,
                    onPlay = onPlay,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(PLAYLISTS_ROUTE) {
            val store = playlists
            if (store == null) {
                navController.popBackStack()
            } else {
                PlaylistsScreen(
                    repository = store,
                    playerState = playerState,
                    onPlay = onPlay,
                    onBack = { navController.popBackStack() },
                    onOpenCollection = onOpenHolderCollection,
                )
            }
        }
    }
}

@Composable
private fun NavigationRailEntry(
    tab: KatMusicTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = tab.icon, contentDescription = tab.label, tint = contentColor)
        Text(text = tab.label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}
