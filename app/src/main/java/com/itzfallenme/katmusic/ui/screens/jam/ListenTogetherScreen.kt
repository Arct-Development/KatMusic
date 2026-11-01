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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(onBack: () -> Unit = {}, viewModel: ListenTogetherViewModel = hiltViewModel()) {
    val client = viewModel.client
    val connection by client.connectionState.collectAsState()
    val roomCode by client.roomCode.collectAsState()
    val isHost by client.isHost.collectAsState()
    val pendingRequests by client.pendingRequests.collectAsState()
    val events by client.events.collectAsState()
    var endpoint by rememberSaveable { mutableStateOf("wss://katmusic.onrender.com/ws") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var chatText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listen Together") },
                navigationIcon = { OutlinedButton(onClick = onBack) { Text("Back") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
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
                        Text("Status · $connection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                androidx.compose.material3.Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create a room", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(displayName, { displayName = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
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
                    androidx.compose.material3.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ROOM CODE", style = MaterialTheme.typography.labelLarge)
                            Text(roomCode, style = MaterialTheme.typography.displaySmall)
                            Text(if (isHost) "You’re hosting this session" else "You’re listening with the room")
                            OutlinedButton(onClick = { viewModel.leaveRoom() }) { Text("Leave room") }
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
