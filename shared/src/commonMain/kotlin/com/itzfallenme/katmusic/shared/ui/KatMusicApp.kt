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
import androidx.compose.material.icons.rounded.LibraryMusic
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.itzfallenme.katmusic.shared.library.MediaLibrary
import com.itzfallenme.katmusic.shared.library.PlatformActions
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.settings.SettingsStore
import com.itzfallenme.katmusic.shared.settings.toThemeUiState
import com.itzfallenme.katmusic.shared.ui.components.PlayerDock
import com.itzfallenme.katmusic.shared.ui.screens.LibraryScreen
import com.itzfallenme.katmusic.shared.ui.screens.NowPlayingScreen
import com.itzfallenme.katmusic.shared.ui.screens.SearchScreen
import com.itzfallenme.katmusic.shared.ui.screens.SettingsScreen
import com.itzfallenme.katmusic.ui.components.MotionSpec
import com.itzfallenme.katmusic.ui.components.WindowSizeClass
import com.itzfallenme.katmusic.ui.components.rememberWindowSizeClass
import com.itzfallenme.katmusic.ui.theme.KatMusicTheme
import kotlinx.coroutines.launch

/** The shared top-level destinations. One navigation graph for both platforms. */
enum class KatMusicTab(val route: String, val label: String, val icon: ImageVector) {
    Library("library", "Library", Icons.Rounded.LibraryMusic),
    Search("search", "Search", Icons.Rounded.Search),
    Settings("settings", "Settings", Icons.Rounded.Settings),
}

const val NOW_PLAYING_ROUTE = "now_playing"

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
                canChooseFolder = platform.canChooseFolder,
                onRefresh = { scope.launch { library.refresh() } },
                onChooseFolder = {
                    scope.launch {
                        platform.chooseLibraryFolder()
                        library.refresh()
                    }
                },
                onPlay = playQueue,
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
                    KatMusicTab.entries.forEach { tab ->
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (!themeState.hideNavTabs) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        KatMusicTab.entries.forEach { tab ->
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
    canChooseFolder: Boolean,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
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
        composable(KatMusicTab.Library.route) {
            LibraryScreen(
                tracks = tracks,
                scan = scan,
                playerState = playerState,
                canChooseFolder = canChooseFolder,
                onRefresh = onRefresh,
                onChooseFolder = onChooseFolder,
                onPlay = onPlay,
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
            )
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
