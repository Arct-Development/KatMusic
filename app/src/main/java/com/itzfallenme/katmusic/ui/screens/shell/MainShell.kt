package com.itzfallenme.katmusic.ui.screens.shell

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.SideEffect
import com.itzfallenme.katmusic.ui.screens.player.LocalBottomNavCollapsed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import android.view.HapticFeedbackConstants
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalView
import android.graphics.Bitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.graphics.scale
import com.itzfallenme.katmusic.ui.theme.drawInteractiveGlass
import com.itzfallenme.katmusic.ui.theme.liquidGlass
import com.itzfallenme.katmusic.ui.theme.rememberGlassInteraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.IntBuffer
import kotlin.time.Duration.Companion.seconds
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itzfallenme.katmusic.ui.components.MotionSpec
import com.itzfallenme.katmusic.ui.components.BackAwareScreen
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import com.itzfallenme.katmusic.ui.screens.feed.FeedScreen
import com.itzfallenme.katmusic.ui.screens.home.HomeScreen
import com.itzfallenme.katmusic.ui.screens.player.LocalMiniPlayerScrollClearance
import com.itzfallenme.katmusic.ui.screens.playlist.PlaylistScreen
import androidx.compose.foundation.shape.CornerBasedShape
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.sign
import kotlin.math.roundToInt
import com.itzfallenme.katmusic.ui.theme.LocalIsDarkTheme
import com.itzfallenme.katmusic.ui.theme.LocalLiquidGlass
import com.itzfallenme.katmusic.ui.theme.LocalMd3NavBar
import com.itzfallenme.katmusic.ui.theme.LiquidGlassPreset
import com.itzfallenme.katmusic.ui.theme.liquidGlassChrome
import com.itzfallenme.katmusic.ui.theme.liquidGlassContainerColor
import com.itzfallenme.katmusic.ui.theme.LayerBackdrop
import com.itzfallenme.katmusic.ui.theme.SquircleShape
import com.itzfallenme.katmusic.ui.theme.rememberLayerBackdrop
import com.itzfallenme.katmusic.ui.theme.isLiquidGlassBackdropSupported
import com.itzfallenme.katmusic.ui.theme.liquidGlassSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin bridge exposing AppUpdateManager's live update state to MainShell */
@HiltViewModel
class MainShellViewModel @Inject constructor(
    val appUpdateManager: com.itzfallenme.katmusic.data.update.AppUpdateManager,
    val ytAuthManager: com.itzfallenme.katmusic.data.ytmusic.YtMusicAuthManager,
    val settingsPreferences: com.itzfallenme.katmusic.data.local.SettingsPreferences,
) : ViewModel() {
    val updateInfo = appUpdateManager.updateInfo
    val ytConnection = ytAuthManager.connection
    val settings = settingsPreferences.settings

    fun dismissUpdate(version: String) {
        appUpdateManager.dismissUpdate(version)
    }

    fun openUpdate(context: android.content.Context) {
        appUpdateManager.openUpdate(context)
    }

    fun setSuppressYouTubeLoginPrompt(suppress: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setSuppressYouTubeLoginPrompt(suppress)
        }
    }

    fun setDismissedJoinSupportPrompt(dismissed: Boolean = true) {
        viewModelScope.launch {
            settingsPreferences.setDismissedJoinSupportPrompt(dismissed)
        }
    }
}

private enum class MainTab(val labelRes: Int) {
    FEED(com.itzfallenme.katmusic.R.string.nav_feed),
    STATS(com.itzfallenme.katmusic.R.string.nav_stats),
    LIBRARY(com.itzfallenme.katmusic.R.string.nav_library),
    SEARCH(com.itzfallenme.katmusic.R.string.nav_search),
}

/** Shared with any screen hosted inside [MainShell] so their scrolling
 *  lists know how much bottom content padding to reserve — the nav
 *  overlays content (it's not a Scaffold bottomBar reserving space), so
 *  each screen leaves this much room for its last item to clear it. */
object FloatingNavDefaults {
    val ContentBottomPadding = 112.dp

    /**
     * Full bottom clearance for edge-to-edge scrolling content: the floating
     * dock's visual height + margins ([ContentBottomPadding]) PLUS the live
     * navigation-bar (gesture area) inset. Screens that let their list draw
     * beneath the transparent gesture area must use this instead of the raw
     * constant, otherwise the last row hides behind the dock/gesture bar.
     */
    @Composable
    fun contentBottomPadding(): Dp {
        val hideNav = com.itzfallenme.katmusic.ui.theme.LocalHideNavTabs.current
        val basePadding = if (hideNav) 16.dp else ContentBottomPadding
        return basePadding +
            LocalMiniPlayerScrollClearance.current +
            WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    }
}

private val DockShape: CornerBasedShape = RoundedCornerShape(32.dp)
private val PillShape: Shape = CircleShape

private fun <T> navSpring() = MotionSpec.spatialSpring<T>()

@Composable
fun MainShell(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenGenres: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFriendProfile: (username: String, displayName: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
    onOpenFeedPlaylist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenGenerator: () -> Unit = {},
    onOpenNewReleases: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenPodcasts: () -> Unit = {},
    onOpenPodcastShow: (String) -> Unit = {},
    onOpenListenTogether: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenSearchResultArtist: (String, String?) -> Unit = { _, _ -> },
    onOpenSearchResultAlbum: (String, String, String?) -> Unit = { _, _, _ -> },
    onOpenSearchResultPlaylist: (String) -> Unit = {},
    onOpenYouTubeLogin: () -> Unit = {},
    onReturnToHome: () -> Boolean = { false },
    mainShellViewModel: MainShellViewModel = hiltViewModel(),
) {
    val tabs = MainTab.entries
    var isHomeScrolledToTop by rememberSaveable { mutableStateOf(true) }
    var isStatsScrolledToTop by rememberSaveable { mutableStateOf(true) }
    var isLibraryScrolledToTop by rememberSaveable { mutableStateOf(true) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val feedTapEvents = remember { kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Drive the dock selection from an explicit tab index instead of
    // pagerState.currentPage. currentPage flips mid-scroll (halfway through
    // animateScrollToPage), which re-triggers the pill width animation + the
    // generator FAB enter/exit while the pager is still moving — the two
    // competing size animations clip the dock (rectangular, limited) and let
    // the FAB draw over the pill. Updating immediately on tap and syncing from
    // settledPage on swipe keeps one clean transition.
    var selectedTabIndex by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTabIndex != pagerState.settledPage) {
            selectedTabIndex = pagerState.settledPage
        }
    }
    val updateInfo by mainShellViewModel.updateInfo.collectAsStateWithLifecycle()
    val showUpdateBanner = updateInfo.isUpdateAvailable && !updateInfo.isDismissed
    val ytConnection by mainShellViewModel.ytConnection.collectAsStateWithLifecycle()
    val settings by mainShellViewModel.settings.collectAsStateWithLifecycle(initialValue = com.itzfallenme.katmusic.data.local.MiscSettings())
    var promptDismissedThisSession by rememberSaveable { mutableStateOf(false) }

    // The legacy Telegram/community welcome dialog has been removed.
    val shouldShowLoginPrompt = !ytConnection.isConnected &&
        !settings.suppressYouTubeLoginPrompt &&
        !promptDismissedThisSession

    if (shouldShowLoginPrompt) {
        var dontShowAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (dontShowAgain) mainShellViewModel.setSuppressYouTubeLoginPrompt(true)
                promptDismissedThisSession = true
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = if (LocalLiquidGlass.current) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            title = {
                Text(
                    text = "Get better recommendations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Log in to YouTube Music, or create a free account, to get personalized songs and queues.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Don't show again",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dontShowAgain) mainShellViewModel.setSuppressYouTubeLoginPrompt(true)
                        promptDismissedThisSession = true
                        onOpenYouTubeLogin()
                    },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("Take me to YT Music login")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (dontShowAgain) mainShellViewModel.setSuppressYouTubeLoginPrompt(true)
                        promptDismissedThisSession = true
                    },
                ) {
                    Text("OK, thanks")
                }
            },
        )
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    // Unconditional remember keeps composition stable; usage gated below.
    val navigationBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val navGlass = isLiquidGlassBackdropSupported()

    Box(Modifier.fillMaxSize()) {
        val feedIndex = tabs.indexOf(MainTab.FEED)
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize().liquidGlassSource(if (navGlass) navigationBackdrop else null),
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            BackAwareScreen(
                enabled = isCurrent && tabs[page] != MainTab.FEED,
                onBack = { scope.launch { pagerState.animateScrollToPage(feedIndex) } },
            ) {
                when (tabs[page]) {
                    MainTab.FEED -> FeedScreen(
                        onOpenSettings = onOpenSettings,
                        onOpenSearch = {
                            val searchIndex = tabs.indexOf(MainTab.SEARCH)
                            if (selectedTabIndex != searchIndex) selectedTabIndex = searchIndex
                            scope.launch { pagerState.animateScrollToPage(searchIndex) }
                        },
                        onOpenDiscover = onOpenDiscover,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenFeedPlaylist = onOpenFeedPlaylist,
                        onOpenGenerator = onOpenGenerator,
                        onOpenFriends = onOpenFriends,
                        onOpenFriendProfile = onOpenFriendProfile,
                        onOpenNewReleases = onOpenNewReleases,
                        onOpenDownloads = onOpenDownloads,
                        onOpenListenTogether = onOpenListenTogether,
                        onOpenPodcasts = onOpenPodcasts,
                        onOpenPodcastShow = onOpenPodcastShow,
                        onOpenYouTubeLogin = onOpenYouTubeLogin,
                        feedTapEvents = feedTapEvents,
                        onScrollToTopChanged = { isHomeScrolledToTop = it },
                    )
                    MainTab.STATS -> HomeScreen(
                        onOpenSettings = onOpenSettings,
                        onOpenSearch = {
                            val searchIndex = tabs.indexOf(MainTab.SEARCH)
                            if (selectedTabIndex != searchIndex) selectedTabIndex = searchIndex
                            scope.launch { pagerState.animateScrollToPage(searchIndex) }
                        },
                        onOpenDiscover = onOpenDiscover,
                        onOpenGenres = onOpenGenres,
                        onOpenFriends = onOpenFriends,
                        onOpenDownloads = onOpenDownloads,
                        onScrollToTopChanged = { isStatsScrolledToTop = it },
                    )
                    MainTab.LIBRARY -> PlaylistScreen(
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenGenerator = onOpenGenerator,
                        onOpenDownloads = onOpenDownloads,
                        onOpenStats = {
                            val statsIndex = tabs.indexOf(MainTab.STATS)
                            if (selectedTabIndex != statsIndex) selectedTabIndex = statsIndex
                            scope.launch { pagerState.animateScrollToPage(statsIndex) }
                        },
                        onScrollToTopChanged = { isLibraryScrolledToTop = it },
                    )
                    MainTab.SEARCH -> com.itzfallenme.katmusic.ui.screens.search.SearchScreen(
                        showBackButton = false,
                        onOpenArtist = onOpenSearchResultArtist,
                        onOpenAlbum = onOpenSearchResultAlbum,
                        onOpenPlaylist = onOpenSearchResultPlaylist,
                    )
                }
            }
        }

        // App update prompt banner (only shown on app open when an update is available and not dismissed)
        AnimatedVisibility(
            visible = showUpdateBanner,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .adaptiveContentWidth(maxWidth = 600.dp)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(10f),
        ) {
            UpdatePromptCard(
                version = updateInfo.latestVersion,
                onUpdate = { mainShellViewModel.openUpdate(context) },
                onDismiss = { mainShellViewModel.dismissUpdate(updateInfo.latestVersion) },
            )
        }

        val bottomNavSlot = com.itzfallenme.katmusic.ui.screens.player.LocalBottomNavSlot.current
        val hideNavTabs = com.itzfallenme.katmusic.ui.theme.LocalHideNavTabs.current
        val md3NavBar = LocalMd3NavBar.current
    // KatMusic's primary navigation intentionally stays in the floating liquid-glass dock style.
        val clearance = com.itzfallenme.katmusic.ui.screens.player.LocalMiniPlayerScrollClearance.current
        val isMiniPlayerActive = clearance > 0.dp
        val systemBottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

        val currentSelectedIndex by androidx.compose.runtime.rememberUpdatedState(selectedTabIndex)
        val currentIsScrolledToTop by androidx.compose.runtime.rememberUpdatedState(
            when (tabs.getOrNull(selectedTabIndex)) {
                MainTab.FEED -> isHomeScrolledToTop
                MainTab.STATS -> isStatsScrolledToTop
                MainTab.LIBRARY -> isLibraryScrolledToTop
                else -> true
            }
        )
        val currentOnSelect by androidx.compose.runtime.rememberUpdatedState { index: Int ->
            if (tabs[index] == MainTab.FEED) {
                val wasOnDetail = onReturnToHome()
                if (wasOnDetail) {
                    if (selectedTabIndex != feedIndex) selectedTabIndex = feedIndex
                    scope.launch { pagerState.scrollToPage(feedIndex) }
                    return@rememberUpdatedState
                }
                if (pagerState.currentPage != feedIndex) {
                    if (selectedTabIndex != feedIndex) selectedTabIndex = feedIndex
                    scope.launch { pagerState.scrollToPage(feedIndex) }
                    return@rememberUpdatedState
                }
                feedTapEvents.tryEmit(Unit)
            } else {
                if (index != selectedTabIndex) selectedTabIndex = index
                scope.launch { pagerState.animateScrollToPage(index) }
            }
            Unit
        }
        val currentOnOpenGenerator by androidx.compose.runtime.rememberUpdatedState(onOpenGenerator)

        androidx.compose.runtime.DisposableEffect(navigationBackdrop, tabs, hideNavTabs) {
            if (!hideNavTabs) {
                bottomNavSlot.value = {
                    Box(Modifier.fillMaxSize()) {
                        FloatingNavBar(
                            backdrop = navigationBackdrop,
                            tabs = tabs,
                            selectedIndex = currentSelectedIndex,
                            isScrolledToTop = currentIsScrolledToTop,
                            onSelect = currentOnSelect,
                            onOpenGenerator = currentOnOpenGenerator,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            } else {
                bottomNavSlot.value = {}
            }
            onDispose {
                bottomNavSlot.value = {}
                com.itzfallenme.katmusic.ui.screens.player.LocalBottomNavCollapsed.current.value = false
            }
        }
    }
}

@Composable
private fun UpdatePromptCard(
    version: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.itzfallenme.katmusic.R.string.update_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.itzfallenme.katmusic.R.string.update_ready_to_install, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onUpdate),
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.itzfallenme.katmusic.R.string.update),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(com.itzfallenme.katmusic.R.string.dismiss_update),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBar(
    backdrop: LayerBackdrop?,
    tabs: List<MainTab>,
    selectedIndex: Int,
    isScrolledToTop: Boolean,
    onSelect: (Int) -> Unit,
    onOpenGenerator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquidGlass = true
    val searchIndex = tabs.indexOf(MainTab.SEARCH)
    val isSearchSelected = selectedIndex == searchIndex
    val capsuleTabs = remember(tabs) { tabs.filterNot { it == MainTab.SEARCH } }
    val selectedTab = tabs.getOrNull(selectedIndex) ?: MainTab.FEED
    val selectedCapsuleIndex = capsuleTabs.indexOf(selectedTab)
    val dockInteraction = remember { MutableInteractionSource() }
    val searchInteraction = remember { MutableInteractionSource() }
    val collapsedState = LocalBottomNavCollapsed.current

    // Search remains a separate glass FAB; the three primary destinations
    // live in the capsule, which folds as the active list scrolls.
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(isSearchSelected, isScrolledToTop) {
        isExpanded = !isSearchSelected && isScrolledToTop
    }
    SideEffect {
        collapsedState.value = !isExpanded
    }

    val expansion by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "navigationMorph",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val searchSize = 56.dp
        val searchGap = 12.dp
        val capsuleBudget = (maxWidth - searchSize - searchGap).coerceAtLeast(48.dp)
        val tabWidth = (((capsuleBudget - 12.dp) / capsuleTabs.size).coerceAtMost(96.dp)).coerceAtLeast(48.dp)
        val expandedCapsuleWidth = tabWidth * capsuleTabs.size + 12.dp
        val capsuleWidth = lerp(48.dp, expandedCapsuleWidth, expansion)
        val capsuleHeight = lerp(48.dp, 64.dp, expansion)
        val searchVisibility = ((expansion - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val foldedIconAlpha = (1f - expansion * 2.8f).coerceIn(0f, 1f)

        val targetIndicatorIndex = selectedCapsuleIndex.coerceAtLeast(0)
        val indicatorPosition by animateFloatAsState(
            targetValue = targetIndicatorIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
            label = "glassTabIndicatorPosition",
        )
        var dragPosition by remember { mutableStateOf<Float?>(null) }
        val indicatorProgress = dragPosition ?: indicatorPosition
        val indicatorOffset = 6.dp + (tabWidth * indicatorProgress)
        val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }

        val clusterWidth = capsuleWidth + searchGap * searchVisibility + searchSize * searchVisibility
        val clusterOffset = ((maxWidth - clusterWidth).coerceAtLeast(0.dp) / 2) * expansion
        Row(
            modifier = Modifier.offset(x = clusterOffset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = DockShape,
                color = liquidGlassContainerColor(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    enabled = liquidGlass,
                    backdrop = backdrop,
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .width(capsuleWidth)
                    .height(capsuleHeight)
                    .clip(DockShape)
                    .liquidGlassChrome(
                        DockShape,
                        liquidGlass,
                        LiquidGlassPreset.BottomNavigation,
                        backdrop,
                        interactionSource = dockInteraction,
                    ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (selectedCapsuleIndex >= 0) {
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .height(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f))
                                .liquidGlassChrome(
                                    CircleShape,
                                    liquidGlass,
                                    LiquidGlassPreset.BottomNavigation,
                                    backdrop,
                                    interactionSource = dockInteraction,
                                ),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = expansion }
                            .pointerInput(capsuleTabs, tabWidthPx, selectedIndex) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragPosition = indicatorPosition
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        val next = ((dragPosition ?: indicatorPosition) + amount / tabWidthPx)
                                            .coerceIn(0f, (capsuleTabs.lastIndex).toFloat())
                                        dragPosition = next
                                    },
                                    onDragEnd = {
                                        val target = (dragPosition ?: indicatorPosition)
                                            .roundToInt()
                                            .coerceIn(0, capsuleTabs.lastIndex)
                                        onSelect(tabs.indexOf(capsuleTabs[target]))
                                        dragPosition = null
                                    },
                                    onDragCancel = { dragPosition = null },
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        capsuleTabs.forEachIndexed { position, tab ->
                            val index = tabs.indexOf(tab)
                            FloatingNavItem(
                                label = androidx.compose.ui.res.stringResource(tab.labelRes),
                                icon = tab.icon(),
                                selected = selectedIndex == index,
                                width = tabWidth,
                                onClick = { onSelect(index) },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = foldedIconAlpha }
                            .clickable(enabled = !isExpanded) { isExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = selectedTab.icon(),
                            contentDescription = androidx.compose.ui.res.stringResource(selectedTab.labelRes),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(searchGap * searchVisibility))
            Box(
                modifier = Modifier
                    .width(searchSize * searchVisibility)
                    .height(searchSize),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = { if (isExpanded) onSelect(searchIndex) },
                    enabled = isExpanded,
                    shape = CircleShape,
                    color = liquidGlassContainerColor(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        enabled = liquidGlass,
                        backdrop = backdrop,
                    ),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(searchSize)
                        .graphicsLayer {
                            alpha = searchVisibility
                            scaleX = lerp(0.82f, 1f, searchVisibility)
                            scaleY = scaleX
                        }
                        .liquidGlassChrome(
                            CircleShape,
                            liquidGlass,
                            LiquidGlassPreset.BottomNavigation,
                            backdrop,
                            interactionSource = searchInteraction,
                        ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = androidx.compose.ui.res.stringResource(com.itzfallenme.katmusic.R.string.nav_search),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionSpec.effectsDefault(),
        label = "glassNavTabContent",
    )
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.FEED -> Icons.Filled.Home
    MainTab.STATS -> Icons.Filled.BarChart
    MainTab.LIBRARY -> Icons.AutoMirrored.Filled.QueueMusic
    MainTab.SEARCH -> Icons.Filled.Search
}
