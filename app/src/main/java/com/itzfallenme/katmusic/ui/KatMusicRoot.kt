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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

    LaunchedEffect(Unit) {
        ServiceLocator.player.startPositionUpdates()
        if (openPlayer) nav.playerOpen = true
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

@Composable
private fun Dock(nav: Navigator) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = 18.dp,
        fillAlpha = 0.72f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(Motion.spatialDefault())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = nav.current == tab.screen
                val iconTint by animateColorAsState(
                    targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                    animationSpec = Motion.effectsDefault(),
                    label = "tabTint"
                )
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = Motion.spatialFast(),
                    label = "tabScale"
                )
                Row(
                    modifier = Modifier
                        .height(50.dp)
                        .animateContentSize(Motion.spatialDefault())
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(scheme.primary)
                            } else {
                                Modifier
                            }
                        )
                        .bounceClickable(targetScale = 0.93f) { nav.switchTab(tab.screen) }
                        .padding(horizontal = if (selected) 16.dp else 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = iconTint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally(Motion.spatialDefault()) + fadeIn(Motion.effectsDefault()),
                        exit = shrinkHorizontally(Motion.spatialFast()) + fadeOut(Motion.effectsFast())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Shared top bar used by secondary screens. */
@Composable
fun SimpleTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .liquidGlass(shape = CircleShape, fillAlpha = 0.45f)
                    .bounceClickable(targetScale = 0.9f, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
