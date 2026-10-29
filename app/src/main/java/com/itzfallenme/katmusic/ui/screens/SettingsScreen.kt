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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Which section is open, read by [SettingsCard] so hidden cards never compose. */
private val LocalSettingsSection = staticCompositionLocalOf<SettingsCategory?> { null }

@Composable
fun SettingsScreen(nav: Navigator, categoryId: String? = null) {
    val category = remember(categoryId) { SettingsCategory.fromId(categoryId) }
    val settings by ServiceLocator.settings.state.collectAsState()
    val library by ServiceLocator.library.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastFmDialog by remember { mutableStateOf(false) }
    var pendingToken by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var showDebugLog by remember { mutableStateOf(false) }

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
        SimpleTopBar(
            title = category?.title ?: "Settings",
            subtitle = category?.subtitle,
            onBack = { nav.back() }
        )
        CompositionLocalProvider(LocalSettingsSection provides category) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (category == null) {
                SettingsHome(nav = nav)
            }
            val account by ServiceLocator.account.state.collectAsState()
            SettingsCard(
                title = "YouTube Music account",
                icon = Icons.Filled.Person,
                section = SettingsCategory.ACCOUNT
            ) {
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

            SettingsCard(
                title = "Appearance",
                icon = Icons.Filled.Palette,
                section = SettingsCategory.APPEARANCE
            ) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach {
                        (value, label) ->
                        com.itzfallenme.katmusic.ui.Chip(
                            text = label,
                            selected = settings.themeMode == value,
                            onClick = { update { s -> s.copy(themeMode = value) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingToggle(
                    title = "Wallpaper colours",
                    subtitle = "Use the system Material You palette (Android 12+)",
                    checked = settings.useSystemDynamicColor
                ) { update { s -> s.copy(useSystemDynamicColor = it) } }
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

            SettingsCard(
                title = "Playback",
                icon = Icons.Filled.PlayCircle,
                section = SettingsCategory.PLAYBACK
            ) {
                val streamQuality = com.itzfallenme.katmusic.data.StreamQuality.fromId(settings.streamQuality)
                Text("Streaming quality", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                com.itzfallenme.katmusic.ui.theme.ExpressiveSegmentedRow(
                    options = com.itzfallenme.katmusic.data.StreamQuality.entries.map { it.label },
                    selectedIndex = com.itzfallenme.katmusic.data.StreamQuality.entries.indexOf(streamQuality),
                    onSelect = { index ->
                        val chosen = com.itzfallenme.katmusic.data.StreamQuality.entries[index]
                        update { s -> s.copy(streamQuality = chosen.id) }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
                Text(
                    streamQuality.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(4.dp))
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

            SettingsCard(
                title = "Audio & safety",
                icon = Icons.Filled.VolumeUp,
                section = SettingsCategory.AUDIO
            ) {
                SettingToggle(
                    title = "Mono audio",
                    subtitle = "Downmix both channels into one for a single earbud. Applies from the next track.",
                    checked = settings.monoAudio
                ) { on -> ServiceLocator.player.applyMono(on) }
                SettingToggle(
                    title = "Volume limit",
                    subtitle = if (settings.volumeLimitEnabled)
                        "Music stays at or below ${settings.volumeLimitPercent}% of the device maximum"
                    else "Cap the music stream to protect your hearing",
                    checked = settings.volumeLimitEnabled
                ) { on ->
                    update { s -> s.copy(volumeLimitEnabled = on) }
                    ServiceLocator.player.applyVolumeGuard()
                }
                if (settings.volumeLimitEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Limit: ${settings.volumeLimitPercent}%",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.volumeLimitPercent.toFloat(),
                        onValueChange = { value ->
                            update { s -> s.copy(volumeLimitPercent = value.toInt()) }
                        },
                        onValueChangeFinished = { ServiceLocator.player.applyVolumeGuard() },
                        valueRange = 40f..100f,
                        steps = 5
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Listening break reminder", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (settings.listeningReminderMinutes <= 0) "Off — no reminders"
                    else "A nudge after ${settings.listeningReminderMinutes} minutes of continuous playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 30 to "30m", 45 to "45m", 60 to "60m", 90 to "90m").forEach {
                        (minutes, label) ->
                        com.itzfallenme.katmusic.ui.Chip(
                            text = label,
                            selected = settings.listeningReminderMinutes == minutes,
                            onClick = {
                                update { s -> s.copy(listeningReminderMinutes = minutes) }
                                ServiceLocator.player.acknowledgeListeningReminder()
                            }
                        )
                    }
                }
            }

            SettingsCard(
                title = "Lyrics",
                icon = Icons.Filled.Lyrics,
                section = SettingsCategory.LYRICS
            ) {
                SettingToggle(
                    title = "Fetch lyrics automatically",
                    subtitle = "Synced lyrics from LRCLIB",
                    checked = settings.lyricsEnabled
                ) { update { s -> s.copy(lyricsEnabled = it) } }
                SettingToggle(
                    title = "Word-by-word lyrics",
                    subtitle = "Apple Music TTML timing, karaoke highlight",
                    checked = settings.wordByWordLyrics
                ) { update { s -> s.copy(wordByWordLyrics = it) } }
                SettingToggle(
                    title = "Lyric glow",
                    subtitle = "Halo follows the word being sung, with lifted glyphs",
                    checked = settings.lyricsGlow
                ) { update { s -> s.copy(lyricsGlow = it) } }
                SettingToggle(
                    title = "Romanise lyrics",
                    subtitle = "Show a Latin-script reading under each line",
                    checked = settings.romanizedLyrics
                ) { update { s -> s.copy(romanizedLyrics = it) } }
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

            SettingsCard(
                title = "Downloads",
                icon = Icons.Filled.Download,
                section = SettingsCategory.DOWNLOADS
            ) {
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

            SettingsCard(
                title = "AI",
                icon = Icons.Filled.AutoAwesome,
                section = SettingsCategory.AI
            ) {
                Text(
                    "Suggestions are generated by an OpenAI-compatible model you choose. " +
                        "The key is stored on this device and only sent to the endpoint below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.aiBaseUrl,
                    onValueChange = { update { s -> s.copy(aiBaseUrl = it.trim()) } },
                    label = { Text("Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.aiApiKey,
                    onValueChange = { update { s -> s.copy(aiApiKey = it.trim()) } },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.aiModel,
                    onValueChange = { update { s -> s.copy(aiModel = it.trim()) } },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Works with OpenAI, Groq, OpenRouter, Ollama and anything else that speaks " +
                        "the chat-completions API. Ask for picks from Discover.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard(
                title = "Discord",
                icon = Icons.Filled.GraphicEq,
                section = SettingsCategory.DISCORD
            ) {
                SettingToggle(
                    title = "Discord Rich Presence",
                    subtitle = "Show the playing track on your Discord profile",
                    checked = settings.discordPresenceEnabled
                ) { on ->
                    update { s -> s.copy(discordPresenceEnabled = on) }
                    ServiceLocator.discord.refresh()
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.discordAppId,
                    onValueChange = { update { s -> s.copy(discordAppId = it.filter(Char::isDigit)) } },
                    label = { Text("Application ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create an application at discord.com/developers/applications, then paste its " +
                        "Application ID. Both apps use the same Discord RPC service: install Discord, " +
                        "keep presence on, and the card appears while you listen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard(
                title = "Last.fm",
                icon = Icons.Filled.BarChart,
                section = SettingsCategory.LASTFM
            ) {
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
                SettingRow(
                    title = "Friends' listening",
                    subtitle = if (settings.lastFmFriends.isEmpty()) "Follow Last.fm profiles and see what they play"
                    else "Following ${settings.lastFmFriends.size}",
                    icon = Icons.Filled.Group
                ) { nav.go(Screen.Friends) }
                if (status != null) {
                    Text(
                        status ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            SettingsCard(
                title = "Data",
                icon = Icons.Filled.Storage,
                section = SettingsCategory.DATA
            ) {
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
                if (com.itzfallenme.katmusic.BuildConfig.DEBUG) {
                    SettingRow(
                        title = "Debug log",
                        subtitle = "${com.itzfallenme.katmusic.util.AppLog.sizeBytes() / 1024} KB of events, warnings and errors",
                        icon = Icons.Filled.BugReport
                    ) { showDebugLog = true }
                    if (showDebugLog) {
                        DebugLogDialog(onDismiss = { showDebugLog = false })
                    }
                }
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

            if (category == null) {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            com.itzfallenme.katmusic.R.drawable.katmusic_logo
                        ),
                        contentDescription = "KatMusic logo",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("KatMusic", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${com.itzfallenme.katmusic.BuildConfig.VERSION_NAME} " +
                            "(${com.itzfallenme.katmusic.BuildConfig.BUILD_TYPE}) • " +
                            "built ${com.itzfallenme.katmusic.BuildConfig.BUILD_STAMP}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Design language adapted from LastWave • GPL-3.0",
                        style = MaterialTheme.typography.labelSmall,
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
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

/**
 * Root of the settings tree: one search box over a flat list of sections.
 * Search matches individual rows from [settingsIndex] and opens the section
 * that owns them, so the user never has to know where a toggle lives.
 */
@Composable
private fun SettingsHome(nav: Navigator) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { searchSettings(query) }

    Column(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search settings…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { query = "" }
                    )
                }
            }
        }

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Text(
                    "No setting matches \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
                )
            }
            results.forEach { item ->
                SettingRow(
                    title = item.title,
                    subtitle = item.category.title +
                        if (item.subtitle.isNotBlank()) " • ${item.subtitle}" else "",
                    icon = item.category.icon
                ) { nav.go(Screen.SettingsSection(item.category.id)) }
                Spacer(Modifier.height(8.dp))
            }
            return@Column
        }

        Text(
            "Browse",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp)
        )
        SettingsCategory.entries.forEach { section ->
            SettingRow(
                title = section.title,
                subtitle = section.subtitle,
                icon = section.icon
            ) { nav.go(Screen.SettingsSection(section.id)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * A settings section: a small muted label above its rows, as in the reference
 * app. The rows themselves are individual rounded cards, so the card wrapper
 * here only carries the heading and spacing.
 */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    section: SettingsCategory,
    content: @Composable () -> Unit
) {
    // A section that is not open is skipped entirely, so its rows (and their
    // sliders, text fields and launchers) never enter composition.
    if (LocalSettingsSection.current != section) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Tonal tile palette for row icons; picked per title so it is stable across launches. */
@Composable
private fun tileColors(seed: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val pairs = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHighest to scheme.onSurface
    )
    return pairs[(seed.hashCode() and Int.MAX_VALUE) % pairs.size]
}

@Composable
private fun SettingIconTile(icon: ImageVector, seed: String) {
    val (background, tint) = tileColors(seed)
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIconTile(
                if (checked) Icons.Filled.Check else Icons.Filled.Close,
                title
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Debug-build log viewer: last few hundred events, copyable for a bug report. */
@Composable
private fun DebugLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var contents by remember { mutableStateOf(com.itzfallenme.katmusic.util.AppLog.tail()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Debug log") },
        text = {
            Column(
                Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    contents,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("KatMusic debug log", contents)
                )
            }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = {
                com.itzfallenme.katmusic.util.AppLog.clear()
                contents = com.itzfallenme.katmusic.util.AppLog.tail()
            }) { Text("Clear") }
        }
    )
}
