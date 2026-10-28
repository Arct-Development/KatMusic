package com.itzfallenme.katmusic.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.LocalTrack
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import com.itzfallenme.katmusic.ui.TrackRow
import kotlinx.coroutines.launch

@Composable
fun LocalMusicScreen(nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerState by ServiceLocator.player.state.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var tracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }

    fun load() {
        if (!granted || loading) return
        scope.launch {
            loading = true
            tracks = ServiceLocator.localMusic.scan()
            loading = false
            scanned = true
        }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        if (allowed) load()
    }

    LaunchedEffect(granted) { if (granted) load() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(title = "On this device", onBack = { nav.back() }) {
            TextButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            if (tracks.isEmpty()) "Your device music" else "${tracks.size} songs found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Files stay where they are - nothing is uploaded. Downloads made in " +
                                "KatMusic also appear here once your phone has indexed them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tracks.isNotEmpty()) {
                                TextButton(onClick = {
                                    val mapped = tracks.map { it.toTrack() }
                                    ServiceLocator.player.playTracks(mapped, 0)
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play all")
                                }
                            }
                            if (!granted) {
                                TextButton(onClick = { requestPermission.launch(permission) }) {
                                    Text("Allow access to audio")
                                }
                            }
                        }
                        if (loading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            items(tracks, key = { it.id }) { local ->
                TrackRow(
                    track = local.toTrack(),
                    isCurrent = playerState.track?.id == "local-${local.id}",
                    isPlaying = playerState.playing,
                    onClick = {
                        val mapped = tracks.map { it.toTrack() }
                        val index = tracks.indexOfFirst { it.id == local.id }.coerceAtLeast(0)
                        ServiceLocator.player.playTracks(mapped, index)
                    },
                    onToggleLike = { ServiceLocator.library.toggleLike(it) }
                )
            }

            if (!loading && tracks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.MusicNote,
                        title = if (scanned) "Nothing on this device" else "Library not scanned yet",
                        subtitle = if (granted) {
                            "Copy some audio files to your phone, then tap rescan."
                        } else {
                            "KatMusic needs permission to read the audio files stored on your phone."
                        }
                    )
                }
            }
        }
    }
}
