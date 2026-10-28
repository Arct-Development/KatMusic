package com.itzfallenme.katmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.ui.screens.DetailScreen
import com.itzfallenme.katmusic.ui.screens.DownloadsScreen
import com.itzfallenme.katmusic.ui.screens.EqualizerScreen
import com.itzfallenme.katmusic.ui.screens.ExploreScreen
import com.itzfallenme.katmusic.ui.screens.HomeScreen
import com.itzfallenme.katmusic.ui.screens.LibraryScreen
import com.itzfallenme.katmusic.ui.screens.PlayerScreen
import com.itzfallenme.katmusic.ui.screens.SearchScreen
import com.itzfallenme.katmusic.ui.screens.SettingsScreen
import com.itzfallenme.katmusic.ui.screens.StatsScreen
import com.itzfallenme.katmusic.ui.theme.KatMusicTheme
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

    KatMusicTheme(seed = seed, amoled = settings.amoled) {
        BackHandler(enabled = nav.canGoBack || nav.playerOpen || nav.queueOpen) {
            nav.back()
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (playerState.track != null) {
                            MiniPlayer(
                                state = playerState,
                                onOpen = { nav.openPlayer() },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        BottomNav(nav = nav)
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                ) {
                    val screen = nav.current
                    when (screen) {
                        Screen.Home -> HomeScreen(nav)
                        Screen.Explore -> ExploreScreen(nav)
                        Screen.Search -> SearchScreen(nav)
                        Screen.Library -> LibraryScreen(nav)
                        Screen.Settings -> SettingsScreen(nav)
                        Screen.Downloads -> DownloadsScreen(nav)
                        Screen.Stats -> StatsScreen(nav)
                        Screen.Equalizer -> EqualizerScreen(nav)
                        is Screen.Detail -> DetailScreen(nav, screen)
                    }
                }
            }

            AnimatedVisibility(
                visible = nav.playerOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                PlayerScreen(nav = nav)
            }
        }
    }
}

@Composable
private fun BottomNav(nav: Navigator) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val selected = nav.current == tab.screen
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { nav.switchTab(tab.screen) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    modifier = Modifier.size(23.dp),
                    tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant
                )
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
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
