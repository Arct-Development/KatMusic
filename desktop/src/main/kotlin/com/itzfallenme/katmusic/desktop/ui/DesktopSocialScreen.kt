package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.data.DesktopLastFmRepository
import com.itzfallenme.katmusic.desktop.data.LastFmFriend
import com.itzfallenme.katmusic.desktop.data.LastFmRecentTrack
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/** Last.fm friend profiles, recent activity and listening charts. */
@Composable
fun DesktopSocialScreen(repository: DesktopLastFmRepository) {
    val connection by repository.connection.collectAsState()
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<LastFmFriend>>(emptyList()) }
    var ownArtists by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var ownRecent by remember { mutableStateOf<List<LastFmRecentTrack>>(emptyList()) }
    var selectedFriend by remember { mutableStateOf<LastFmFriend?>(null) }
    var friendArtists by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var friendRecent by remember { mutableStateOf<List<LastFmRecentTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (!connection.connected || loading) return
        scope.launch {
            loading = true
            error = null
            repository.loadFriends()
                .onSuccess { friends = it }
                .onFailure { error = it.message ?: "Couldn't load your Last.fm friends" }
            repository.loadTopArtists()
                .onSuccess { ownArtists = it }
                .onFailure { if (error == null) error = it.message ?: "Couldn't load your listening profile" }
            repository.loadRecentTracks()
                .onSuccess { ownRecent = it }
                .onFailure { if (error == null) error = it.message ?: "Couldn't load recent listening" }
            selectedFriend?.let { friend ->
                repository.loadTopArtists(friend.username)
                    .onSuccess { friendArtists = it }
                    .onFailure { if (error == null) error = it.message ?: "Couldn't load ${friend.username}'s charts" }
                repository.loadRecentTracks(friend.username)
                    .onSuccess { friendRecent = it }
                    .onFailure { if (error == null) error = it.message ?: "Couldn't load ${friend.username}'s recent tracks" }
            }
            loading = false
        }
    }

    LaunchedEffect(connection.connected, connection.username) {
        selectedFriend = null
        if (connection.connected) refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selectedFriend?.let { "${it.username}'s profile" } ?: "Last.fm Social",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (connection.connected) "Signed in as ${connection.username}"
                        else "Connect Last.fm in Settings to view profiles and friends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedFriend != null) {
                    TextButton(onClick = { selectedFriend = null }) { Text("My profile") }
                }
                IconButton(onClick = ::refresh, enabled = connection.connected && !loading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Last.fm data")
                }
            }
        }

        if (!connection.connected) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Your Last.fm account", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Connect from Settings → Integrations. Desktop can then scrobble playback and show your public listening profile and friends. Personal data is fetched directly from Last.fm.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (loading && ownArtists.isEmpty() && friends.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Loading your Last.fm profile…")
                    }
                }
            }
            selectedFriend?.let { friend ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                friend.realName.takeIf(String::isNotBlank) ?: friend.username,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "@${friend.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { openExternal(friend.profileUrl) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open Last.fm profile")
                        }
                    }
                }
                if (friendArtists.isNotEmpty()) {
                    item { SectionHeading("Top artists") }
                    items(friendArtists.take(20), key = { "friend-artist:${it.first}" }) { (artist, plays) ->
                        ArtistLine(artist, plays)
                    }
                }
                if (friendRecent.isNotEmpty()) {
                    item { SectionHeading("Recent listening") }
                    items(friendRecent, key = { "friend-recent:${it.title}:${it.artist}:${it.album}" }) { track ->
                        RecentTrackLine(track)
                    }
                }
            } ?: run {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Your listening profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { openExternal("https://www.last.fm/user/${connection.username}") },
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open Last.fm profile")
                        }
                    }
                }
                if (ownArtists.isNotEmpty()) {
                    item { SectionHeading("Top artists") }
                    items(ownArtists.take(20), key = { "own-artist:${it.first}" }) { (artist, plays) ->
                        ArtistLine(artist, plays)
                    }
                }
                if (ownRecent.isNotEmpty()) {
                    item { SectionHeading("Recent listening") }
                    items(ownRecent, key = { "own-recent:${it.title}:${it.artist}:${it.album}" }) { track ->
                        RecentTrackLine(track)
                    }
                }
                item { SectionHeading("Friends · ${friends.size}") }
                if (friends.isEmpty() && !loading) {
                    item {
                        Text(
                            "No public friends were returned by Last.fm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                items(friends, key = { it.username }) { friend ->
                    Card(
                        onClick = {
                            selectedFriend = friend
                            friendArtists = emptyList()
                            friendRecent = emptyList()
                            error = null
                            loading = true
                            scope.launch {
                                repository.loadTopArtists(friend.username)
                                    .onSuccess { friendArtists = it }
                                    .onFailure { error = it.message ?: "Couldn't load this profile" }
                                repository.loadRecentTracks(friend.username)
                                    .onSuccess { friendRecent = it }
                                    .onFailure { if (error == null) error = it.message ?: "Couldn't load recent tracks" }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    friend.realName.takeIf(String::isNotBlank) ?: friend.username,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (friend.realName.isNotBlank()) {
                                    Text(
                                        "@${friend.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { openExternal(friend.profileUrl) }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open ${friend.username} on Last.fm")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 20.dp)) }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun ArtistLine(name: String, plays: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$plays plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentTrackLine(track: LastFmRecentTrack) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · ") +
                    if (track.nowPlaying) " · now playing" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.profileUrl?.let { url ->
            IconButton(onClick = { openExternal(url) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open Last.fm track")
            }
        }
    }
}

private fun openExternal(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
