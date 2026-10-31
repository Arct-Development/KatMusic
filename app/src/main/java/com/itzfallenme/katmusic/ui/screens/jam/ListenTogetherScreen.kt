package com.itzfallenme.katmusic.ui.screens.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun ListenTogetherScreen(onBack: () -> Unit, viewModel: ListenTogetherViewModel = hiltViewModel()) {
    val client = viewModel.client
    val connection by client.connectionState.collectAsState()
    val roomCode by client.roomCode.collectAsState()
    val isHost by client.isHost.collectAsState()
    val pendingRequests by client.pendingRequests.collectAsState()
    val events by client.events.collectAsState()
    var endpoint by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var chatText by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Listen Together") },
            navigationIcon = { OutlinedButton(onClick = onBack) { Text("Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Online listening rooms", style = MaterialTheme.typography.titleLarge)
            Text("Everyone streams audio independently. The room synchronizes track metadata and playback timing; it never relays audio or stream URLs.")
            OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(),
                label = { Text("WebSocket server URL") }, placeholder = { Text("wss://your-domain.example/ws") }, singleLine = true)
            OutlinedTextField(displayName, { displayName = it.take(32) }, Modifier.fillMaxWidth(),
                label = { Text("Display name") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.createRoom(endpoint, displayName) },
                    enabled = endpoint.isNotBlank() && connection != "Connecting…", modifier = Modifier.weight(1f)) { Text("Create room") }
                OutlinedButton(onClick = { viewModel.joinRoom(endpoint, joinCode, displayName) },
                    enabled = endpoint.isNotBlank() && joinCode.length >= 4 && connection != "Connecting…",
                    modifier = Modifier.weight(1f)) { Text("Join room") }
            }
            OutlinedTextField(joinCode, { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                Modifier.fillMaxWidth(), label = { Text("Room code") }, singleLine = true)
            Text("Status: $connection")
            if (roomCode.isNotBlank()) {
                Text("Room $roomCode • " + if (isHost) "Host" else "Guest", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.leaveRoom() }) { Text("Leave room") }
            }
            if (pendingRequests.isNotEmpty() && isHost) {
                Text("Join requests", style = MaterialTheme.typography.titleMedium)
                pendingRequests.forEach { request ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(request.displayName, modifier = Modifier.weight(1f))
                        Button(onClick = { client.approve(request.requestId, true) }) { Text("Allow") }
                        OutlinedButton(onClick = { client.approve(request.requestId, false) }) { Text("Deny") }
                    }
                }
            }
            if (roomCode.isNotBlank()) {
                OutlinedTextField(chatText, { chatText = it.take(500) }, Modifier.fillMaxWidth(),
                    label = { Text("Room chat") },
                    trailingIcon = { Button(onClick = { client.sendChat(chatText); chatText = "" },
                        enabled = chatText.isNotBlank()) { Text("Send") } })
            }
            Text("Room activity", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(events.asReversed()) { event ->
                    val message = event.payload["text"]?.toString()?.trim('"')
                    val name = event.payload["displayName"]?.toString()?.trim('"')
                    Text(when (event.type) {
                        "chat" -> (name ?: "Listener") + ": " + (message ?: "")
                        "error" -> "Error: " + (event.payload["message"]?.toString()?.trim('"') ?: "Unknown error")
                        "join_pending" -> "Waiting for host approval"
                        "room_created" -> "Room created"
                        "room_joined" -> "You joined the room"
                        "member_joined" -> "A listener joined"
                        "member_left" -> "A listener left"
                        "host_changed" -> "Room host changed"
                        else -> event.type.replace('_', ' ')
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
