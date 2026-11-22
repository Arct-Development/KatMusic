package com.itzfallenme.katmusic.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itzfallenme.katmusic.shared.data.DownloadsRepository
import com.itzfallenme.katmusic.shared.data.HomeFeedRepository
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
import com.itzfallenme.katmusic.shared.data.PodcastsRepository
import com.itzfallenme.katmusic.shared.data.sharedCodeTrack
import com.itzfallenme.katmusic.shared.library.MediaLibrary
import com.itzfallenme.katmusic.shared.library.PlatformActions
import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.resources.Res
import com.itzfallenme.katmusic.shared.resources.katmusic_logo
import com.itzfallenme.katmusic.shared.settings.SettingsStore
import com.itzfallenme.katmusic.shared.settings.toThemeUiState
import com.itzfallenme.katmusic.shared.ui.components.PlayerDock
import com.itzfallenme.katmusic.shared.ui.components.SongCodeEntryDialog
import com.itzfallenme.katmusic.shared.ui.screens.CollectionScreen
import com.itzfallenme.katmusic.shared.ui.screens.DownloadsScreen
import com.itzfallenme.katmusic.shared.ui.screens.HomeScreen
import com.itzfallenme.katmusic.shared.ui.screens.LibraryScreen
import com.itzfallenme.katmusic.shared.ui.screens.LyricsScreen
import com.itzfallenme.katmusic.shared.ui.screens.NowPlayingScreen
import com.itzfallenme.katmusic.shared.ui.screens.PlaylistsScreen
import com.itzfallenme.katmusic.shared.ui.screens.PodcastsScreen
import com.itzfallenme.katmusic.shared.ui.screens.QueueScreen
import com.itzfallenme.katmusic.shared.ui.screens.SearchScreen
import com.itzfallenme.katmusic.shared.ui.screens.SettingsScreen
import com.itzfallenme.katmusic.ui.components.MotionSpec
import com.itzfallenme.katmusic.ui.components.WindowSizeClass
import com.itzfallenme.katmusic.ui.components.rememberWindowSizeClass
import com.itzfallenme.katmusic.ui.theme.GlassBackdrop
import com.itzfallenme.katmusic.ui.theme.GlassSurface
import com.itzfallenme.katmusic.ui.theme.KatMusicTheme
import com.itzfallenme.katmusic.ui.theme.LocalGlassRootSize
import com.itzfallenme.katmusic.ui.theme.LocalGlassWallpaper
import com.itzfallenme.katmusic.ui.theme.LocalIsDarkTheme
import com.itzfallenme.katmusic.ui.theme.glassWallpaperFor
import com.itzfallenme.katmusic.ui.theme.liquidGlassSurface
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** The shared top-level destinations. One navigation graph for both platforms. */
enum class KatMusicTab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Explore),
    NewReleases("new_releases", "Releases", Icons.Filled.NewReleases),
    Artists("artists", "Artists", Icons.Rounded.Mic),
    Social("social", "Social", Icons.Filled.People),
    Home("home", "Home", Icons.Rounded.Home),
    Stats("stats", "Stats", Icons.Rounded.BarChart),
    Library("library", "Library", Icons.Rounded.LibraryMusic),
    Search("search", "Search", Icons.Rounded.Search),
    Podcasts("podcasts", "Podcasts", Icons.Rounded.Mic),
    Jam("jam", "Jam", Icons.Rounded.Groups),
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
 * Layout is the only thing that adapts, and it adapts the way Android's own
 * MainShell does when the window is wide: the navigation moves to a rail on
 * the left and the content + floating player dock fill the rest, so a desktop
 * window reads like the Android app in landscape. Below 600dp it is the same
 * app with the navigation as a bottom bar.
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
    podcasts: PodcastsRepository? = null,
    /** Optional platform Jam screen; hidden when the platform has no room client. */
    jamContent: (@Composable () -> Unit)? = null,
    /** Optional platform file import for the shared Playlists screen. */
    onImportPlaylist: (() -> Unit)? = null,
    /** Optional provider-backed playlist import for platforms with a catalog client. */
    onImportOnlinePlaylist: (() -> Unit)? = null,
    /** Optional platform-only settings section, rendered above About. */
    settingsExtra: (@Composable ColumnScope.() -> Unit)? = null,
    /** Optional platform-provided listening statistics screen. */
    statsContent: (@Composable () -> Unit)? = null,
    /** Optional platform-specific Discover destination. */
    discoverContent: (@Composable () -> Unit)? = null,
    /** Optional provider-backed new release/album destination. */
    newReleasesContent: (@Composable () -> Unit)? = null,
    /** Optional platform Last.fm/social destination. */
    socialContent: (@Composable () -> Unit)? = null,
    /** Optional provider-backed artist browsing destination. */
    artistsContent: (@Composable () -> Unit)? = null,
    /** Desktop-native (or shared) song-code share surface for the current track. */
    onShareSongCode: ((Track) -> Unit)? = null,
    /** Optional platform renderer for a lyrics share card. */
    onShareLyrics: ((Track, com.itzfallenme.katmusic.shared.lyrics.Lyrics) -> Unit)? = null,
    /** Android's "Start mix with this song": seed a radio from the current track. */
    onStartMix: ((Track) -> Unit)? = null,
    /** Optional reader for a saved code card, replacing the Android camera. */
    onPickCodeImage: (suspend () -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val settings by settingsStore.settings.collectAsState()
    val themeState = remember(settings) { settings.toThemeUiState() }

    KatMusicTheme(themeState) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: KatMusicTab.Library.route
        val sizeClass = rememberWindowSizeClass()
        val wideLayout = sizeClass != WindowSizeClass.COMPACT
        val tracks by library.tracks.collectAsState()
        val scan by library.scan.collectAsState()
        val playerState by player.state.collectAsState()
        val scope = rememberCoroutineScope()
        var collection by remember { mutableStateOf<CollectionTarget?>(null) }
        var showCodeEntry by remember { mutableStateOf(false) }

        val visibleTabs = remember(
            homeFeed,
            podcasts,
            jamContent,
            statsContent,
            discoverContent,
            newReleasesContent,
            socialContent,
            artistsContent,
        ) {
            KatMusicTab.entries.filter { tab ->
                (tab != KatMusicTab.Discover || discoverContent != null) &&
                    (tab != KatMusicTab.NewReleases || newReleasesContent != null) &&
                    (tab != KatMusicTab.Social || socialContent != null) &&
                    (tab != KatMusicTab.Artists || artistsContent != null) &&
                    (tab != KatMusicTab.Home || homeFeed != null) &&
                    (tab != KatMusicTab.Stats || statsContent != null) &&
                    (tab != KatMusicTab.Podcasts || podcasts != null) &&
                    (tab != KatMusicTab.Jam || jamContent != null)
            }
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
        val openRoute: (String) -> Unit = { route ->
            if (currentRoute != route) {
                navController.navigate(route) { launchSingleTop = true }
            }
        }
        val playQueue: (List<Track>, Int) -> Unit = { queue, index -> player.play(queue, index) }

        // One path for every KatMusic Code, used by Search and the rail's
        // "Song code" action: repository-backed metadata when the platform has
        // a catalog client, a direct video-id track otherwise.
        val resolveCode: suspend (String) -> Result<Track> = { videoId ->
            homeFeed?.resolveCode(videoId) ?: Result.success(sharedCodeTrack(videoId))
        }

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
                onPlayCode = resolveCode,
                onShareSongCode = onShareSongCode,
                onShareLyrics = onShareLyrics,
                onStartMix = onStartMix,
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
                podcasts = podcasts,
                jamContent = jamContent,
                onImportPlaylist = onImportPlaylist,
                onImportOnlinePlaylist = onImportOnlinePlaylist,
                settingsExtra = settingsExtra,
                statsContent = statsContent,
                discoverContent = discoverContent,
                newReleasesContent = newReleasesContent,
                socialContent = socialContent,
                artistsContent = artistsContent,
                onOpenHolderCollection = { target ->
                    collection = CollectionTarget(target.title, target.subtitle, target.artwork, target.tracks)
                    navController.navigate(COLLECTION_ROUTE) { launchSingleTop = true }
                },
            )
        }

        // Real glass needs a backdrop to sample: one themed wallpaper is drawn
        // behind the shell and every glass panel blurs its own region of it.
        val isDarkTheme = LocalIsDarkTheme.current
        val scheme = MaterialTheme.colorScheme
        val wallpaper = remember(themeState.liquidGlass, isDarkTheme, scheme) {
            if (!themeState.liquidGlass) {
                null
            } else {
                glassWallpaperFor(
                    isDark = isDarkTheme,
                    background = scheme.background,
                    primary = scheme.primary,
                    tertiary = scheme.tertiary,
                )
            }
        }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val glassRootSize = IntSize(constraints.maxWidth, constraints.maxHeight)
            wallpaper?.let { spec ->
                GlassBackdrop(wallpaper = spec, modifier = Modifier.fillMaxSize())
            }
            CompositionLocalProvider(
                LocalGlassWallpaper provides wallpaper,
                LocalGlassRootSize provides glassRootSize,
            ) {
        if (wideLayout) {
            val expandedRail = sizeClass == WindowSizeClass.EXPANDED
            val railWidth = if (expandedRail) 224.dp else 104.dp
            // Android's main destinations come first, exactly like its
            // bottom navigation; the catalog/library destinations follow.
            val railTabs = listOf(
                KatMusicTab.Home,
                KatMusicTab.Stats,
                KatMusicTab.Library,
                KatMusicTab.Search,
                KatMusicTab.Discover,
                KatMusicTab.NewReleases,
                KatMusicTab.Artists,
                KatMusicTab.Podcasts,
                KatMusicTab.Social,
                KatMusicTab.Jam,
            ).filter { it in visibleTabs }
            Row(modifier = Modifier.fillMaxSize()) {
                val railBody: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp, horizontal = if (expandedRail) 14.dp else 8.dp),
                        horizontalAlignment = if (expandedRail) Alignment.Start else Alignment.CenterHorizontally,
                    ) {
                    RailBrand(expanded = expandedRail)
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = if (expandedRail) Alignment.Start else Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        railTabs.forEach { tab ->
                            RailEntry(
                                icon = tab.icon,
                                label = tab.label,
                                selected = currentRoute == tab.route,
                                expanded = expandedRail,
                                onClick = { selectTab(tab) },
                            )
                        }
                        if (playlists != null || downloads != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            if (playlists != null) {
                                RailEntry(
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    label = "Playlists",
                                    selected = currentRoute == PLAYLISTS_ROUTE,
                                    expanded = expandedRail,
                                    onClick = { openRoute(PLAYLISTS_ROUTE) },
                                )
                            }
                            if (downloads != null) {
                                RailEntry(
                                    icon = Icons.Rounded.Download,
                                    label = "Downloads",
                                    selected = currentRoute == DOWNLOADS_ROUTE,
                                    expanded = expandedRail,
                                    onClick = { openRoute(DOWNLOADS_ROUTE) },
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    RailEntry(
                        icon = Icons.Rounded.QrCodeScanner,
                        label = "Song code",
                        selected = false,
                        expanded = expandedRail,
                        onClick = { showCodeEntry = true },
                    )
                    RailEntry(
                        icon = KatMusicTab.Settings.icon,
                        label = KatMusicTab.Settings.label,
                        selected = currentRoute == KatMusicTab.Settings.route,
                        expanded = expandedRail,
                        onClick = { selectTab(KatMusicTab.Settings) },
                    )
                    }
                }
                if (themeState.liquidGlass) {
                    GlassSurface(
                        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        modifier = Modifier.width(railWidth).fillMaxHeight(),
                        shadowElevation = 12.dp,
                    ) {
                        railBody()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(railWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        railBody()
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
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) { content() }
                PlayerDock(
                    state = playerState,
                    player = player,
                    onExpand = openNowPlaying,
                    onOpenQueue = openQueue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (!themeState.hideNavTabs) {
                    val glassNav = themeState.liquidGlass && !themeState.md3NavBar
                    NavigationBar(
                        modifier = Modifier.liquidGlassSurface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            enabled = glassNav,
                            shadowElevation = 12.dp,
                        ),
                        containerColor = if (glassNav) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
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

        if (showCodeEntry) {
            SongCodeEntryDialog(
                onDismiss = { showCodeEntry = false },
                onOpen = resolveCode,
                onPlay = { track -> player.play(listOf(track), 0) },
                onPickImage = onPickCodeImage,
            )
        }
            }
        }
    }
}

/** Brand lockup at the top of the wide navigation rail. */
@Composable
private fun RailBrand(expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (expanded) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.katmusic_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape),
        )
        if (expanded) {
            Text(
                text = "Kat Music",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One navigation rail row: icon plus label, Android's selected-pill treatment. */
@Composable
private fun RailEntry(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(container)
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(container)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    onPlayCode: suspend (String) -> Result<Track>,
    onShareSongCode: ((Track) -> Unit)?,
    onShareLyrics: ((Track, com.itzfallenme.katmusic.shared.lyrics.Lyrics) -> Unit)?,
    onStartMix: ((Track) -> Unit)?,
    downloads: DownloadsRepository?,
    playlists: PlaylistsRepository?,
    homeFeed: HomeFeedRepository?,
    podcasts: PodcastsRepository?,
    jamContent: (@Composable () -> Unit)?,
    onImportPlaylist: (() -> Unit)?,
    onImportOnlinePlaylist: (() -> Unit)?,
    settingsExtra: (@Composable ColumnScope.() -> Unit)?,
    statsContent: (@Composable () -> Unit)?,
    discoverContent: (@Composable () -> Unit)?,
    newReleasesContent: (@Composable () -> Unit)?,
    socialContent: (@Composable () -> Unit)?,
    artistsContent: (@Composable () -> Unit)?,
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
        composable(KatMusicTab.Discover.route) {
            val content = discoverContent
            if (content == null) {
                navController.popBackStack()
            } else {
                content()
            }
        }
        composable(KatMusicTab.NewReleases.route) {
            val content = newReleasesContent
            if (content == null) {
                navController.popBackStack()
            } else {
                content()
            }
        }
        composable(KatMusicTab.Social.route) {
            val content = socialContent
            if (content == null) {
                navController.popBackStack()
            } else {
                content()
            }
        }
        composable(KatMusicTab.Artists.route) {
            val content = artistsContent
            if (content == null) {
                navController.popBackStack()
            } else {
                content()
            }
        }
        composable(KatMusicTab.Home.route) {
            val feed = homeFeed
            if (feed == null) {
                navController.popBackStack()
            } else {
                HomeScreen(repository = feed, playerQueue = onPlay)
            }
        }
        composable(KatMusicTab.Stats.route) {
            val content = statsContent
            if (content == null) {
                navController.popBackStack()
            } else {
                content()
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
                onOpenGenre = { genre, genreTracks ->
                    onOpenCollection(
                        CollectionTarget(
                            title = genre,
                            subtitle = "${genreTracks.size} songs",
                            artwork = genreTracks.firstOrNull()?.artwork,
                            tracks = genreTracks,
                        ),
                    )
                },
            )
        }
        composable(KatMusicTab.Search.route) {
            SearchScreen(
                tracks = tracks,
                playerState = playerState,
                onPlay = onPlay,
                homeFeed = homeFeed,
                onPlayCode = onPlayCode,
            )
        }
        composable(KatMusicTab.Podcasts.route) {
            val repository = podcasts
            if (repository == null) {
                navController.popBackStack()
            } else {
                PodcastsScreen(repository = repository, onPlay = onPlay)
            }
        }
        composable(KatMusicTab.Jam.route) {
            if (jamContent == null) {
                navController.popBackStack()
            } else {
                jamContent()
            }
        }
        composable(KatMusicTab.Settings.route) {
            SettingsScreen(
                settings = settings,
                settingsStore = settingsStore,
                extraContent = settingsExtra,
            )
        }
        composable(NOW_PLAYING_ROUTE) {
            NowPlayingScreen(
                state = playerState,
                player = player,
                onBack = { navController.popBackStack() },
                onShareSongCode = onShareSongCode,
                onStartMix = onStartMix,
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
                onShareLyrics = onShareLyrics,
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
                    onImport = onImportPlaylist,
                    onImportOnlinePlaylist = onImportOnlinePlaylist,
                )
            }
        }
    }
}
