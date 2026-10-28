package com.itzfallenme.katmusic.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.RestoreResult
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.Screen
import com.itzfallenme.katmusic.ui.SimpleTopBar
import com.itzfallenme.katmusic.ui.parseHexColor
import kotlinx.coroutines.launch

private val seedColors = listOf(
    "#8AB4F8", "#F8BBD0", "#B2FFDA", "#FFD9A0", "#C7B9FF", "#FF6B6B", "#77E4D4", "#F4F1DE"
)

@Composable
fun SettingsScreen(nav: Navigator) {
    val settings by ServiceLocator.settings.state.collectAsState()
    val library by ServiceLocator.library.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastFmDialog by remember { mutableStateOf(false) }
    var pendingToken by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = ServiceLocator.backup.export()
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(payload.encodeToByteArray())
            } ?: error("Could not write to that file")
        }
        backupStatus = written.fold(
            onSuccess = { "Backup saved" },
            onFailure = { it.message ?: "Backup failed" }
        )
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            backupStatus = "Could not read that file"
            return@rememberLauncherForActivityResult
        }
        backupStatus = when (val result = ServiceLocator.backup.restore(text)) {
            is RestoreResult.Success -> buildString {
                append("Restored ${result.likes} likes and ${result.playlists} playlists")
                if (result.settingsRestored) append(" plus your settings")
            }
            is RestoreResult.Failure -> result.message
        }
    }

    fun update(transform: (com.itzfallenme.katmusic.data.AppSettings) -> com.itzfallenme.katmusic.data.AppSettings) {
        ServiceLocator.settings.update(transform)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(title = "Settings", onBack = { nav.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            val account by ServiceLocator.account.state.collectAsState()
            SettingsCard(title = "YouTube Music account", icon = Icons.Filled.Person) {
                if (account.signedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.itzfallenme.katmusic.ui.Artwork(
                            url = account.avatar,
                            modifier = Modifier.size(44.dp),
                            corner = 22.dp,
                            fallbackIcon = Icons.Filled.Person
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.name.ifBlank { "YouTube Music account" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Library, likes and history sync with this account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nav.go(Screen.Account) }) { Text("Switch account") }
                        TextButton(onClick = {
                            ServiceLocator.account.signOut()
                            status = "Signed out"
                        }) { Text("Sign out") }
                    }
                } else {
                    Text(
                        "Sign in to personalise your home feed and sync your YouTube Music library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { nav.go(Screen.Account) }) { Text("Sign in with YouTube Music") }
                    TextButton(onClick = { nav.go(Screen.Account) }) { Text("Continue as guest") }
                }
            }

            SettingsCard(title = "Appearance", icon = Icons.Filled.Palette) {
                SettingToggle(
                    title = "Artwork colours",
                    subtitle = "Recolour the app from the current album art",
                    checked = settings.dynamicArtworkTheme
                ) { update { s -> s.copy(dynamicArtworkTheme = it) } }
                SettingToggle(
                    title = "Pure black",
                    subtitle = "AMOLED friendly backgrounds",
                    checked = settings.amoled
                ) { update { s -> s.copy(amoled = it) } }
                Spacer(Modifier.height(12.dp))
                Text("Now playing style", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("expressive" to "Expressive", "classic" to "Classic", "apple" to "Apple").forEach {
                        (value, label) ->
                        com.itzfallenme.katmusic.ui.Chip(
                            text = label,
                            selected = settings.playerStyle == value,
                            onClick = { update { s -> s.copy(playerStyle = value) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Accent colour",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    seedColors.forEach { hex ->
                        val selected = settings.seedColor.equals(hex, true)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .clickable {
                                    update { s -> s.copy(seedColor = hex, dynamicArtworkTheme = false) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }

            SettingsCard(title = "Playback", icon = Icons.Filled.PlayCircle) {
                SettingToggle(
                    title = "High quality audio",
                    subtitle = "Prefer the highest bitrate Opus stream",
                    checked = settings.highQualityStream
                ) { update { s -> s.copy(highQualityStream = it) } }
                SettingToggle(
                    title = "Autoplay radio",
                    subtitle = "Keep the music going with related tracks",
                    checked = settings.autoplayRadio
                ) { update { s -> s.copy(autoplayRadio = it) } }
                SettingToggle(
                    title = "SponsorBlock",
                    subtitle = "Skip sponsor and intro segments in videos",
                    checked = settings.sponsorBlockEnabled
                ) { update { s -> s.copy(sponsorBlockEnabled = it) } }
                SettingToggle(
                    title = "Skip silence",
                    subtitle = "Jump over silent intros and gaps",
                    checked = settings.skipSilence
                ) {
                    update { s -> s.copy(skipSilence = it) }
                    ServiceLocator.player.applySkipSilence(it)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crossfade: ${settings.crossfadeSeconds}s",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 6, 9).forEach { seconds ->
                        com.itzfallenme.katmusic.ui.Chip(
                            text = if (seconds == 0) "Off" else "${seconds}s",
                            selected = settings.crossfadeSeconds == seconds,
                            onClick = { update { s -> s.copy(crossfadeSeconds = seconds) } }
                        )
                    }
                }
                SettingRow(
                    title = "Equalizer",
                    subtitle = if (settings.equalizerEnabled) "Enabled • ${settings.equalizerPreset}"
                    else "Off",
                    icon = Icons.Filled.Equalizer
                ) { nav.go(Screen.Equalizer) }
            }

            SettingsCard(title = "Lyrics", icon = Icons.Filled.Lyrics) {
                SettingToggle(
                    title = "Fetch lyrics automatically",
                    subtitle = "Synced lyrics from LRCLIB",
                    checked = settings.lyricsEnabled
                ) { update { s -> s.copy(lyricsEnabled = it) } }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Timing offset: ${settings.lyricsOffsetMs} ms",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = settings.lyricsOffsetMs.toFloat(),
                    onValueChange = { update { s -> s.copy(lyricsOffsetMs = it.toLong()) } },
                    valueRange = -3000f..3000f
                )
                Text(
                    "Text size: ${(settings.lyricsTextScale * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = settings.lyricsTextScale,
                    onValueChange = { update { s -> s.copy(lyricsTextScale = it) } },
                    valueRange = 0.7f..1.6f
                )
            }

            SettingsCard(title = "Downloads", icon = Icons.Filled.Download) {
                SettingToggle(
                    title = "Download in high quality",
                    subtitle = "Bigger files, better sound",
                    checked = settings.highQualityStream
                ) { update { s -> s.copy(highQualityStream = it) } }
                SettingRow(
                    title = "Manage downloads",
                    subtitle = "${library.downloads.size} tracks • ${ServiceLocator.downloads.humanTotalSize()}",
                    icon = Icons.Filled.Storage
                ) { nav.go(Screen.Downloads) }
                Text(
                    "Saved to ${ServiceLocator.downloads.root.absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SettingsCard(title = "Last.fm", icon = Icons.Filled.BarChart) {
                if (settings.lastFmSessionKey.isNotBlank()) {
                    Text(
                        "Connected as ${settings.lastFmUsername.ifBlank { "Last.fm user" }}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    SettingToggle(
                        title = "Scrobble plays",
                        subtitle = "Send tracks to your Last.fm profile",
                        checked = settings.scrobblingEnabled
                    ) { update { s -> s.copy(scrobblingEnabled = it) } }
                    SettingRow(
                        title = "Pending scrobbles",
                        subtitle = "${library.scrobbles.size} waiting",
                        icon = Icons.Filled.BarChart
                    ) {
                        scope.launch {
                            val client = ServiceLocator.lastFm()
                            if (client == null) {
                                status = "Add your API key first"
                                return@launch
                            }
                            val pending = ServiceLocator.library.pendingScrobbles()
                            var sent = 0
                            pending.forEach { scrobble ->
                                val ok = client.scrobble(
                                    com.itzfallenme.katmusic.data.Track(
                                        id = "",
                                        title = scrobble.title,
                                        artist = scrobble.artist,
                                        album = scrobble.album
                                    ),
                                    scrobble.timeMs * 1000,
                                    settings.lastFmSessionKey
                                )
                                if (ok) sent++
                            }
                            if (sent > 0) ServiceLocator.library.markScrobblesSynced(sent)
                            status = "Sent $sent scrobbles"
                        }
                    }
                    TextButton(onClick = {
                        update { s -> s.copy(lastFmSessionKey = "", lastFmUsername = "", scrobblingEnabled = false) }
                    }) { Text("Disconnect") }
                } else {
                    Text(
                        "Connect Last.fm to scrobble your listening. You need an API key and secret from last.fm/api/account/create.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = settings.lastFmApiKey,
                        onValueChange = { update { s -> s.copy(lastFmApiKey = it.trim()) } },
                        label = { Text("API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.lastFmApiSecret,
                        onValueChange = { update { s -> s.copy(lastFmApiSecret = it.trim()) } },
                        label = { Text("API secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = settings.lastFmApiKey.isNotBlank() && settings.lastFmApiSecret.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    val client = ServiceLocator.lastFm()
                                    if (client == null) {
                                        status = "Add API key and secret first"
                                        return@launch
                                    }
                                    val token = client.requestToken()
                                    if (token == null) {
                                        status = "Could not reach Last.fm"
                                        return@launch
                                    }
                                    pendingToken = token
                                    status = "Authorise in the browser, then tap Connect."
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(client.authUrl(token)))
                                        )
                                    }
                                }
                            }
                        ) { Text("Authorise") }
                        TextButton(
                            enabled = pendingToken != null,
                            onClick = {
                                val token = pendingToken ?: return@TextButton
                                scope.launch {
                                    val client = ServiceLocator.lastFm() ?: return@launch
                                    val session = client.createSession(token)
                                    if (session == null) {
                                        status = "Authorisation failed - try again"
                                    } else {
                                        update { s ->
                                            s.copy(
                                                lastFmUsername = session.first,
                                                lastFmSessionKey = session.second,
                                                scrobblingEnabled = true
                                            )
                                        }
                                        status = "Connected as ${session.first}"
                                        pendingToken = null
                                    }
                                }
                            }
                        ) { Text("Connect") }
                    }
                }
                if (status != null) {
                    Text(
                        status ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            SettingsCard(title = "Data", icon = Icons.Filled.Storage) {
                SettingRow(
                    title = "Backup library",
                    subtitle = "Save likes, playlists, history and settings to a JSON file",
                    icon = Icons.Filled.Upload
                ) { createBackup.launch(ServiceLocator.backup.fileName()) }
                SettingRow(
                    title = "Restore backup",
                    subtitle = "Replace this library with a previously saved file",
                    icon = Icons.Filled.Storage
                ) { openBackup.launch(arrayOf("application/json", "text/*")) }
                SettingRow(
                    title = "Music on this device",
                    subtitle = "Play audio files already stored on your phone",
                    icon = Icons.Filled.MusicNote
                ) { nav.go(Screen.LocalMusic) }
                SettingRow(
                    title = "Import a playlist",
                    subtitle = "Copy a public YouTube Music playlist into your library",
                    icon = Icons.Filled.PlaylistAdd
                ) { nav.go(Screen.Import) }
                SettingRow(
                    title = "Listening stats",
                    subtitle = "${library.history.size} plays recorded",
                    icon = Icons.Filled.BarChart
                ) { nav.go(Screen.Stats) }
                SettingRow(
                    title = "Clear history",
                    subtitle = "Remove every recorded play",
                    icon = Icons.Filled.Storage
                ) { ServiceLocator.library.clearHistory() }
                SettingRow(
                    title = "Clear recent searches",
                    subtitle = "${library.recentSearches.size} saved searches",
                    icon = Icons.Filled.Storage
                ) { ServiceLocator.library.clearRecentSearches() }
                if (backupStatus != null) {
                    Text(
                        backupStatus ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("KatMusic", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version 1.0.0 • built by ItzFallenMe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "github.com/ItzFallenMe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ItzFallenMe"))
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
