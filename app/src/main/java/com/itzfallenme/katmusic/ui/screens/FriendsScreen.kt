package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import com.itzfallenme.katmusic.ui.theme.wobbleOverscroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.FriendScrobble
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.Artwork
import com.itzfallenme.katmusic.ui.Screen
import com.itzfallenme.katmusic.ui.SimpleTopBar
import kotlinx.coroutines.launch

/**
 * Friends' listening, straight from Last.fm.
 *
 * KatMusic already ships a Last.fm client for scrobbling; this reuses the same
 * API key and session-free public endpoints (user.getRecentTracks), so no extra
 * service, backend or login is involved. A friend is just a Last.fm profile
 * name, stored on this device.
 */
@Composable
fun FriendsScreen(nav: Navigator) {
    val settings by ServiceLocator.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val feeds = remember { mutableStateMapOf<String, List<FriendScrobble>>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    val friends = settings.lastFmFriends

    fun load(username: String) {
        val client = ServiceLocator.lastFm()
        if (client == null) {
            errors[username] = "Add your Last.fm API key in Settings first"
            return
        }
        loading = true
        scope.launch {
            val tracks = runCatching { client.recentTracks(username) }.getOrElse { emptyList() }
            if (tracks.isEmpty()) errors[username] = "Nothing public to show"
            else {
                feeds[username] = tracks
                errors.remove(username)
            }
            loading = false
        }
    }

    fun addFriend() {
        val name = draft.trim().trimStart('@')
        if (name.isBlank()) return
        if (friends.any { it.equals(name, ignoreCase = true) }) {
            status = "$name is already in your feed"
            return
        }
        val client = ServiceLocator.lastFm()
        if (client == null) {
            status = "Add your Last.fm API key in Settings first"
            return
        }
        loading = true
        scope.launch {
            val exists = runCatching { client.userExists(name) }.getOrDefault(false)
            loading = false
            if (!exists) {
                status = "Last.fm has no profile called $name"
                return@launch
            }
            ServiceLocator.settings.update { it.copy(lastFmFriends = it.lastFmFriends + name) }
            draft = ""
            status = "Added $name"
            load(name)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = "Friends", onBack = { nav.back() })
        LazyColumn(
            modifier = Modifier.fillMaxSize().wobbleOverscroll(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Follow a Last.fm profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your API key from Settings is used to read public listening data. " +
                                "Nothing is posted to their profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text("Last.fm username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = ::addFriend, enabled = draft.isNotBlank()) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Add")
                            }
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            status?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (friends.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("No friends yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Add a profile name above to see what they are playing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            friends.forEach { friend ->
                item(key = "header-$friend") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            friend,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { load(friend) }) { Text("Refresh") }
                        IconButton(onClick = {
                            ServiceLocator.settings.update { current ->
                                current.copy(lastFmFriends = current.lastFmFriends - friend)
                            }
                            feeds.remove(friend)
                            errors.remove(friend)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove $friend")
                        }
                    }
                }
                val feed = feeds[friend]
                if (feed == null) {
                    item(key = "loading-$friend") {
                        val message = errors[friend]
                        if (message != null) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = { load(friend) }) { Text("Load recent listening") }
                        }
                    }
                } else {
                    items(feed, key = { "$friend-${it.artist}-${it.title}-${it.playedAtMs}" }) { scrobble ->
                        FeedRow(scrobble) {
                            scope.launch {
                                val query = "${scrobble.artist} ${scrobble.title}"
                                val shelf = runCatching { YtMusic.search(query) }.getOrNull()
                                val track = shelf.orEmpty().flatMap { it.tracks }.firstOrNull()
                                if (track != null) {
                                    ServiceLocator.player.playTracks(listOf(track))
                                    nav.playerOpen = true
                                } else {
                                    status = "Could not find that on YouTube Music"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(scrobble: FriendScrobble, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(
                url = scrobble.artworkUrl,
                modifier = Modifier.size(44.dp),
                corner = 12.dp
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scrobble.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    buildString {
                        append(scrobble.artist)
                        if (scrobble.album.isNotBlank()) append(" • ${scrobble.album}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (scrobble.nowPlaying) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        "now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
