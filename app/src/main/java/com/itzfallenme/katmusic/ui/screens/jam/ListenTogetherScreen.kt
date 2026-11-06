package com.itzfallenme.katmusic.ui.screens.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itzfallenme.katmusic.ui.components.ScreenHeader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ListenTogetherScreen(onBack: () -> Unit = {}, viewModel: ListenTogetherViewModel = hiltViewModel()) {
    val client = viewModel.client
    val connection by client.connectionState.collectAsState()
    val roomCode by client.roomCode.collectAsState()
    val memberId by client.memberId.collectAsState()
    val isHost by client.isHost.collectAsState()
    val isCoHost by client.isCoHost.collectAsState()
    val coHostId by client.coHostId.collectAsState()
    val roomMode by client.roomMode.collectAsState()
    val roomName by client.roomName.collectAsState()
    val radio by client.radio.collectAsState()
    val events by client.events.collectAsState()
    val members by client.members.collectAsState()
    val playback by client.playback.collectAsState()
    val sharedQueue by client.sharedQueue.collectAsState()
    val suggestions by client.suggestions.collectAsState()
    val clipboard = LocalClipboardManager.current
    var endpoint by rememberSaveable { mutableStateOf("wss://katmusic.onrender.com/ws") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var collaborative by rememberSaveable { mutableStateOf(false) }
    var chatText by rememberSaveable { mutableStateOf("") }
    val mainHost = isHost && !isCoHost

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = "Listen Together", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Music feels better shared", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Create a room, add a co-host, or switch to collaborative playback. Rooms are saved on the Jam server so a restart does not end the session.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (connection == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(8.dp),
                                ) {}
                                Text(connection, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                if (members.isNotEmpty()) {
                                    Text("· ${members.size} listening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (roomCode.isBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Your name", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    displayName,
                                    { displayName = it.take(32) },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("How friends see you") },
                                    singleLine = true,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Collaborative room", style = MaterialTheme.typography.titleMedium)
                                        Text("Anyone can change the track and queue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = collaborative, onCheckedChange = { collaborative = it })
                                }
                                Button(
                                    onClick = { viewModel.createRoom(endpoint, displayName, collaborative) },
                                    enabled = displayName.isNotBlank() && connection != "Connecting…",
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Create room") }
                            }
                        }
                    }
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Join a room", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    joinCode,
                                    { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("Room code") },
                                    singleLine = true,
                                )
                                OutlinedButton(
                                    onClick = { viewModel.joinRoom(endpoint, joinCode, displayName) },
                                    enabled = displayName.isNotBlank() && joinCode.length >= 4 && connection != "Connecting…",
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Join room") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.joinRoom(endpoint, "INDIA", displayName) },
                                        enabled = displayName.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                    ) { Text("India station") }
                                    OutlinedButton(
                                        onClick = { viewModel.joinRoom(endpoint, "WORLD", displayName) },
                                        enabled = displayName.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                    ) { Text("International") }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (radio) "LIVE STATION" else "YOUR ROOM",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                if (roomName.isNotBlank()) {
                                    Text(roomName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        roomCode,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(roomCode)) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy room code", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Text(
                                    when {
                                        radio -> "Always-on station · playback is automatic"
                                        isCoHost -> "You're a co-host · you can control playback"
                                        isHost -> "You're hosting · you control playback and roles"
                                        roomMode == "collaborative" -> "Collaborative room · anyone can control playback"
                                        else -> "You're a listener · suggest tracks to the host"
                                    },
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                OutlinedButton(onClick = { viewModel.leaveRoom() }) { Text("Leave room") }
                            }
                        }
                    }
                    if (mainHost && !radio) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Collaborative playback", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (roomMode == "collaborative") "Everyone can control the room" else "Only hosts can control playback",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = roomMode == "collaborative",
                                        onCheckedChange = viewModel::setCollaborative,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text("Listeners · ${members.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            members.forEach { member ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(38.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(member.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(member.displayName, fontWeight = FontWeight.Medium)
                                            Text(
                                                when {
                                                    member.role == "host" -> "Host"
                                                    member.role == "cohost" -> "Co-host"
                                                    else -> "Listener"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (mainHost && !radio && member.id != memberId) {
                                            when {
                                                member.id == coHostId -> TextButton(onClick = { viewModel.removeCoHost(member.id) }) { Text("Remove co-host") }
                                                coHostId.isBlank() -> TextButton(onClick = { viewModel.makeCoHost(member.id) }) { Text("Make co-host") }
                                            }
                                        } else if (member.role == "host") {
                                            Text("HOST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Now playing together", style = MaterialTheme.typography.titleMedium)
                                val current = playback
                                if (current == null || current.videoId.isBlank()) {
                                    Text("Waiting for the host to start a track.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text(current.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text(current.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (current.isPlaying) "Playing" else "Paused",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (!isHost && !radio) {
                                    OutlinedButton(onClick = viewModel::suggestCurrentTrack, modifier = Modifier.fillMaxWidth()) {
                                        Text("Suggest my current track")
                                    }
                                }
                            }
                        }
                    }
                    if (sharedQueue.isNotEmpty()) {
                        item {
                            Text("Shared queue · ${sharedQueue.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            sharedQueue.take(5).forEachIndexed { index, track ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.Medium)
                                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (isHost && suggestions.isNotEmpty()) {
                        item {
                            Text("Friends' suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            suggestions.forEach { suggestion ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(suggestion.title, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${suggestion.artist} · suggested by ${suggestion.displayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { viewModel.approveSuggestion(suggestion) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Add suggestion to queue")
                                        }
                                        TextButton(onClick = { viewModel.rejectSuggestion(suggestion) }) { Text("Dismiss") }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Room chat", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    chatText,
                                    { chatText = it.take(500) },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("Say something…") },
                                    trailingIcon = {
                                        Button(
                                            onClick = { client.sendChat(chatText); chatText = "" },
                                            enabled = chatText.isNotBlank(),
                                        ) { Text("Send") }
                                    },
                                )
                            }
                        }
                    }
                }

                item { Text("Recent activity", style = MaterialTheme.typography.titleMedium) }
                if (events.isEmpty()) {
                    item { Text("Room activity will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(events.asReversed()) { event ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                eventText(event),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun eventText(event: com.itzfallenme.katmusic.data.jam.JamEvent): String {
    val p = event.payload
    val name = p.text("user_name") ?: p.text("name") ?: "Listener"
    val text = p.text("text").orEmpty()
    return when (event.type) {
        "chat_message" -> "$name: $text"
        "error" -> "Error: ${p.text("message") ?: "Unknown error"}"
        "room_created" -> "Room created"
        "user_joined" -> "$name joined"
        "user_left" -> "$name left"
        "host_changed" -> "Room host changed"
        "cohost_changed" -> "Co-host updated"
        "room_mode_changed" -> "Room mode: ${p.text("room_mode")?.replace('_', ' ') ?: "updated"}"
        "suggestion_received" -> "$name suggested a track"
        "queue_updated" -> "Shared queue updated"
        "station_mode" -> "Station playback updated"
        else -> event.type.replace('_', ' ')
    }
}

private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
