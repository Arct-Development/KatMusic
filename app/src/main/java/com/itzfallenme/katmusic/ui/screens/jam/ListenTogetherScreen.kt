package com.itzfallenme.katmusic.ui.screens.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ui.components.ScreenHeader
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ListenTogetherScreen(onBack: () -> Unit = {}, viewModel: ListenTogetherViewModel = hiltViewModel()) {
    val client = viewModel.client
    val connection by client.connectionState.collectAsState()
    val roomCode by client.roomCode.collectAsState()
    val isHost by client.isHost.collectAsState()
    val pendingRequests by client.pendingRequests.collectAsState()
    val events by client.events.collectAsState()
    val members by client.members.collectAsState()
    val playback by client.playback.collectAsState()
    val sharedQueue by client.sharedQueue.collectAsState()
    val suggestions by client.suggestions.collectAsState()
    val clipboard = LocalClipboardManager.current
    var endpoint by rememberSaveable { mutableStateOf("wss://katmusic.onrender.com/ws") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var chatText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = "Listen Together", onBack = onBack)
            LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                androidx.compose.material3.Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Music feels better shared", style = MaterialTheme.typography.headlineSmall)
                        Text("Create a room and invite friends with its code, or join a room someone has shared with you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = if (connection == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(8.dp)) {}
                            Text(connection, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (members.isNotEmpty()) Text("· ${members.size} listening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                androidx.compose.material3.Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your name", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(displayName, { displayName = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("How friends see you") }, singleLine = true)
                        Text("Create a room", style = MaterialTheme.typography.titleLarge)
                        Button(
                            onClick = { viewModel.createRoom(endpoint, displayName) },
                            enabled = displayName.isNotBlank() && connection != "Connecting…" && roomCode.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Create room") }
                    }
                }
            }
            item {
                androidx.compose.material3.Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Join a room", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(joinCode, { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(8) }, Modifier.fillMaxWidth(), label = { Text("Room code") }, singleLine = true)
                        OutlinedButton(
                            onClick = { viewModel.joinRoom(endpoint, joinCode, displayName) },
                            enabled = displayName.isNotBlank() && joinCode.length >= 4 && connection != "Connecting…" && roomCode.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Join room") }
                    }
                }
            }
            if (roomCode.isNotBlank()) {
                item {
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("YOUR ROOM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(roomCode, modifier = Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(roomCode)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy room code", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Text(if (isHost) "You're hosting · you control playback" else "You're a listener · suggest tracks to the host", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            OutlinedButton(onClick = { viewModel.leaveRoom() }) { Text("Leave room") }
                        }
                    }
                }
                item {
                    Text("Listeners · ${members.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(38.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text(member.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(member.displayName, fontWeight = FontWeight.Medium)
                                        Text(if (member.role == "host") "Host" else "Listener", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (member.role == "host") Text("HOST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Now playing together", style = MaterialTheme.typography.titleMedium)
                            if (playback == null || playback?.videoId.isNullOrBlank()) {
                                Text("Waiting for the host to start a track.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(playback!!.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(playback!!.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (playback!!.isPlaying) "Playing" else "Paused", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (sharedQueue.isNotEmpty()) item {
                    Text("Shared queue · ${sharedQueue.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    sharedQueue.take(20).forEachIndexed { index, track ->
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
                if (isHost && suggestions.isNotEmpty()) item {
                    Text("Friends' suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    suggestions.forEach { suggestion ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(suggestion.title, fontWeight = FontWeight.Medium)
                                    Text("${suggestion.artist} · suggested by ${suggestion.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.playSuggestion(suggestion) }, enabled = suggestion.videoId.isNotBlank()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play suggestion")
                                }
                            }
                        }
                    }
                }
                if (pendingRequests.isNotEmpty() && isHost) item {
                    androidx.compose.material3.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Join requests", style = MaterialTheme.typography.titleMedium)
                            pendingRequests.forEach { request ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(request.displayName, modifier = Modifier.weight(1f))
                                    Button(onClick = { client.approve(request.requestId, true) }) { Text("Allow") }
                                    OutlinedButton(onClick = { client.approve(request.requestId, false) }) { Text("Deny") }
                                }
                            }
                        }
                    }
                }
                item {
                    androidx.compose.material3.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Room chat", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                chatText, { chatText = it.take(500) }, Modifier.fillMaxWidth(),
                                label = { Text("Say something…") },
                                trailingIcon = {
                                    Button(onClick = { client.sendChat(chatText); chatText = "" }, enabled = chatText.isNotBlank()) { Text("Send") }
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
                    val message = event.payload["text"]?.toString()?.trim('"')
                    val name = event.payload["displayName"]?.toString()?.trim('"')
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (event.type) {
                                "chat" -> (name ?: "Listener") + ": " + (message ?: "")
                                "error" -> "Error: " + (event.payload["message"]?.toString()?.trim('"') ?: "Unknown error")
                                "join_pending" -> "Waiting for host approval"
                                "room_created" -> "Room created"
                                "room_joined" -> "You joined the room"
                                "member_joined" -> "A listener joined"
                                "member_left" -> "A listener left"
                                "host_changed" -> "Room host changed"
                                else -> event.type.replace('_', ' ')
                            },
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
