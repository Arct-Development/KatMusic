package com.itzfallenme.katmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.ui.screens.AccountScreen
import com.itzfallenme.katmusic.ui.screens.DetailScreen
import com.itzfallenme.katmusic.ui.screens.DiscoverScreen
import com.itzfallenme.katmusic.ui.screens.DownloadsScreen
import com.itzfallenme.katmusic.ui.screens.EqualizerScreen
import com.itzfallenme.katmusic.ui.screens.ExploreScreen
import com.itzfallenme.katmusic.ui.screens.FriendsScreen
import com.itzfallenme.katmusic.ui.screens.GeneratorScreen
import com.itzfallenme.katmusic.ui.screens.HomeScreen
import com.itzfallenme.katmusic.ui.screens.ImportPlaylistScreen
import com.itzfallenme.katmusic.ui.screens.LibraryScreen
import com.itzfallenme.katmusic.ui.screens.LocalMusicScreen
import com.itzfallenme.katmusic.ui.screens.PlayerScreen
import com.itzfallenme.katmusic.ui.screens.SearchScreen
import com.itzfallenme.katmusic.ui.screens.SettingsScreen
import com.itzfallenme.katmusic.ui.screens.StatsScreen
import com.itzfallenme.katmusic.ui.theme.AmbientBackdrop
import com.itzfallenme.katmusic.ui.theme.GlassSurface
import com.itzfallenme.katmusic.ui.theme.KatMusicTheme
import com.itzfallenme.katmusic.ui.theme.Motion
import com.itzfallenme.katmusic.ui.theme.bounceClickable
import com.itzfallenme.katmusic.ui.theme.liquidGlass
import com.itzfallenme.katmusic.ui.theme.rememberArtworkSeed

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Home", Icons.Filled.Home),
    Tab(Screen.Explore, "Explore", Icons.Filled.Explore),
    Tab(Screen.Library, "Library", Icons.Filled.LibraryMusic),
    Tab(Screen.Search, "Search", Icons.Filled.Search)
)

fun parseHexColor(value: String, fallback: Color = Color(0xFF8AB4F8)): Color = runCatching {
    val cleaned = value.trim().removePrefix("#")
    val hex = when (cleaned.length) {
        6 -> "FF$cleaned"
        8 -> cleaned
        else -> return fallback
    }
    Color(hex.toLong(16))
}.getOrDefault(fallback)

@Composable
fun KatMusicRoot(openPlayer: Boolean = false) {
    val settings by ServiceLocator.settings.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    val nav = remember { Navigator() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var crashReport by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ServiceLocator.player.startPositionUpdates()
        if (openPlayer) nav.playerOpen = true
        crashReport = com.itzfallenme.katmusic.data.CrashLog.read(context)
    }

    // The previous session died: show the trace instead of leaving the user to guess.
    crashReport?.let { report ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text("Last session crashed") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        report.take(4000),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("KatMusic crash", report)
                    )
                }) { Text("Copy report") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.itzfallenme.katmusic.data.CrashLog.clear(context)
                    crashReport = null
                }) { Text("Dismiss") }
            }
        )
    }

    // Hearing-safety nudge from PlayerController; counts actual playback time, not
    // time with the app open.
    if (playerState.listeningReminderMinutes > 0) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ServiceLocator.player.acknowledgeListeningReminder() },
            title = { Text("Time for a break?") },
            text = {
                Text(
                    "You have been listening for ${playerState.listeningReminderMinutes} minutes " +
                        "without a pause. Give your ears a rest, or turn the volume down a notch."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ServiceLocator.player.acknowledgeListeningReminder()
                    ServiceLocator.player.player.pause()
                }) { Text("Pause") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ServiceLocator.player.acknowledgeListeningReminder()
                }) { Text("Keep listening") }
            }
        )
    }

    LaunchedEffect(playerState.track?.id) {
        val session = ServiceLocator.player.player.audioSessionId
        if (session != 0) ServiceLocator.eq.attach(session)
    }

    val baseSeed = parseHexColor(settings.seedColor)
    val artworkSeed = rememberArtworkSeed(
        url = playerState.track?.artwork.takeIf { settings.dynamicArtworkTheme },
        fallback = baseSeed
    )
    val seed = if (settings.dynamicArtworkTheme) artworkSeed else baseSeed
    val darkTheme = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    KatMusicTheme(
        seed = seed,
        amoled = settings.amoled,
        light = !darkTheme,
        dynamicColor = settings.useSystemDynamicColor
    ) {
        BackHandler(enabled = nav.canGoBack || nav.playerOpen || nav.queueOpen) {
            nav.back()
        }
        Box(Modifier.fillMaxSize()) {
            AmbientBackdrop(
                accent = seed,
                intensity = if (playerState.track != null) 1f else 0.55f
            )
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = playerState.track != null && !nav.playerOpen,
                            enter = slideInVertically(Motion.spatialDefault()) { it } + fadeIn(Motion.effectsDefault()),
                            exit = slideOutVertically(Motion.spatialFast()) { it } + fadeOut(Motion.effectsFast())
                        ) {
                            MiniPlayer(
                                state = playerState,
                                onOpen = { nav.openPlayer() },
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        Dock(nav = nav)
                    }
                }
            ) { padding ->
                var lastDepth by remember { mutableIntStateOf(nav.stack.size) }
                LaunchedEffect(nav.current) { lastDepth = nav.stack.size }
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = {
                        val forward = nav.stack.size >= lastDepth
                        val enter = if (forward) Motion.forwardEnter() else Motion.backEnter()
                        val exit = if (forward) Motion.forwardExit() else Motion.backExit()
                        enter togetherWith exit
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                    label = "screens"
                ) { screen ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    ) {
                        when (screen) {
                            Screen.Home -> HomeScreen(nav)
                            Screen.Explore -> ExploreScreen(nav)
                            Screen.Discover -> DiscoverScreen(nav)
                            Screen.Search -> SearchScreen(nav)
                            Screen.Library -> LibraryScreen(nav)
                            Screen.Settings -> SettingsScreen(nav)
                            is Screen.SettingsSection -> SettingsScreen(nav, screen.id)
                            Screen.Downloads -> DownloadsScreen(nav)
                            Screen.Stats -> StatsScreen(nav)
                            Screen.Equalizer -> EqualizerScreen(nav)
                            Screen.Import -> ImportPlaylistScreen(nav)
                            Screen.Account -> AccountScreen(nav)
                            Screen.Generator -> GeneratorScreen(nav)
                            Screen.LocalMusic -> LocalMusicScreen(nav)
                            Screen.Friends -> FriendsScreen(nav)
                            is Screen.Detail -> DetailScreen(nav, screen)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = nav.playerOpen,
                enter = slideInVertically(Motion.spatialSlow()) { it } + fadeIn(Motion.effectsDefault()),
                exit = slideOutVertically(Motion.spatialDefault()) { it } + fadeOut(Motion.effectsFast())
            ) {
                PlayerScreen(nav = nav)
            }
        }
    }
}

/**
 * Floating M3 Expressive navigation pill. One opaque tonal surface, a squircle
 * that grows and shifts to the container colour for the active destination, and
 * label reveal on the same spring as the width change.
 */
@Composable
private fun Dock(nav: Navigator) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .height(54.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = nav.current == tab.screen
                    val weight by animateFloatAsState(
                        targetValue = if (selected) 1.22f else 0.92f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        ),
                        label = "tabWeight"
                    )
                    val radius by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (selected) 18.dp else 27.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        ),
                        label = "tabCorner"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        animationSpec = Motion.effectsDefault(),
                        label = "tabTint"
                    )
                    Row(
                        modifier = Modifier
                            .weight(weight)
                            .height(44.dp)
                            .graphicsLayer {
                                shape = com.itzfallenme.katmusic.ui.theme.SmoothCornerShape(radius, 0.6f)
                                clip = true
                            }
                            .then(
                                if (selected) Modifier.background(scheme.primaryContainer) else Modifier
                            )
                            .bounceClickable(targetScale = 0.93f) { nav.switchTab(tab.screen) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = iconTint,
                            modifier = Modifier.size(21.dp)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(Motion.spatialDefault()) + fadeIn(Motion.effectsDefault()),
                            exit = shrinkHorizontally(Motion.spatialFast()) + fadeOut(Motion.effectsFast())
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared top bar used by secondary screens, in the reference's shape: the back
 * button sits on its own row as a tonal circle, and the title is a large
 * left-aligned wordmark with actions on the same baseline.
 */
@Composable
fun SimpleTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 10.dp)
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .bounceClickable(targetScale = 0.9f, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 36.sp),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { actions() }
        }
    }
}
