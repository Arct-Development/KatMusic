package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.itzfallenme.katmusic.desktop.jam.DesktopJamManager
import com.itzfallenme.katmusic.desktop.jam.JamEvent
import com.itzfallenme.katmusic.shared.jam.JamDefaults
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Listen Together controls for desktop. The same room protocol as Android;
 * the host's YouTube streams are resolved locally for each listener.
 */
@Composable
fun DesktopJamSection(jam: DesktopJamManager) {
    val connection by jam.client.connectionState.collectAsState()
    val roomCode by jam.client.roomCode.collectAsState()
    val roomMode by jam.client.roomMode.collectAsState()
    val members by jam.client.members.collectAsState()
    val playback by jam.client.playback.collectAsState()
    val isHost by jam.client.isHost.collectAsState()
    val isCoHost by jam.client.isCoHost.collectAsState()
    val memberId by jam.client.memberId.collectAsState()
    val coHostId by jam.client.coHostId.collectAsState()
    val sharedQueue by jam.client.sharedQueue.collectAsState()
    val suggestions by jam.client.suggestions.collectAsState()
    val events by jam.client.events.collectAsState()
    val error by jam.error.collectAsState()
    val mainHost = isHost && !isCoHost

    var endpoint by remember { mutableStateOf(JamDefaults.SERVER_ENDPOINT) }
    var displayName by remember { mutableStateOf("Desktop listener") }
    var joinCode by remember { mutableStateOf("") }
    var collaborative by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }

    Text(
        text = "Jam / Listen Together",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = connection,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
    )

    OutlinedTextField(
        value = endpoint,
        onValueChange = { endpoint = it },
        singleLine = true,
        label = { Text("Room server") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            singleLine = true,
            label = { Text("Your name") },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = joinCode,
            onValueChange = { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
            singleLine = true,
            label = { Text("Room code") },
            modifier = Modifier.weight(0.7f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { jam.createRoom(endpoint, displayName, collaborative) },
            enabled = roomCode.isBlank() && displayName.isNotBlank(),
        ) {
            Text("Create room")
        }
        OutlinedButton(
            onClick = { jam.joinRoom(endpoint, joinCode, displayName) },
            enabled = roomCode.isBlank() && joinCode.length >= 4 && displayName.isNotBlank(),
        ) {
            Text("Join room")
        }
        if (roomCode.isNotBlank()) {
            OutlinedButton(onClick = jam::leaveRoom) {
                Text("Leave")
            }
        }
    }

    if (roomCode.isBlank()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(
                checked = collaborative,
                onCheckedChange = { collaborative = it },
                enabled = displayName.isNotBlank(),
            )
            Text("Collaborative room", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (roomCode.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = "Room $roomCode · ${when {
                    mainHost -> "host"
                    isCoHost -> "co-host"
                    else -> "listener"
                }} · $roomMode",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(roomCode), null)
                },
            ) { Text("Copy code") }
        }
        val invite = JamDefaults.inviteUrl(roomCode).orEmpty()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showQr = !showQr }) {
                Text(if (showQr) "Hide invite QR" else "Show invite QR")
            }
            Button(
                onClick = {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(invite), null)
                },
            ) { Text("Copy invite link") }
        }
        if (showQr) {
            JamQrCode(value = invite, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = invite,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (mainHost) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(
                    checked = roomMode == "collaborative",
                    onCheckedChange = jam.client::setCollaborative,
                )
                Text("Let everyone control playback", style = MaterialTheme.typography.bodySmall)
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Listeners · ${members.size}", style = MaterialTheme.typography.titleSmall)
                members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                member.displayName + if (member.id == memberId) " · You" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
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
                        if (mainHost && member.id != memberId) {
                            when {
                                member.id == coHostId ->
                                    TextButton(onClick = { jam.client.revokeCoHost(member.id) }) {
                                        Text("Remove co-host")
                                    }
                                coHostId.isBlank() ->
                                    TextButton(onClick = { jam.client.grantCoHost(member.id) }) {
                                        Text("Make co-host")
                                    }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("Now playing together", style = MaterialTheme.typography.titleSmall)
                if (playback == null || playback?.videoId.isNullOrBlank()) {
                    Text("Waiting for the host to start a track.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(playback!!.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        playback!!.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (playback!!.isPlaying) "Playing" else "Paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!isHost) {
                    OutlinedButton(
                        onClick = jam::suggestCurrentTrack,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Suggest my current track") }
                }
            }
        }

        if (sharedQueue.isNotEmpty()) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Shared queue · ${sharedQueue.size}", style = MaterialTheme.typography.titleSmall)
                    sharedQueue.take(5).forEachIndexed { index, track ->
                        Text(
                            "${index + 1}. ${track.title} — ${track.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (mainHost && suggestions.isNotEmpty()) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Listeners' suggestions", style = MaterialTheme.typography.titleSmall)
                    suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(suggestion.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${suggestion.artist} · ${suggestion.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { jam.client.approveSuggestion(suggestion.suggestionId) }) {
                                Text("Add")
                            }
                            TextButton(onClick = { jam.client.rejectSuggestion(suggestion.suggestionId) }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Room chat",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = chatText,
            onValueChange = { chatText = it.take(500) },
            singleLine = true,
            label = { Text("Say something…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                jam.client.sendChat(chatText)
                chatText = ""
            },
            enabled = chatText.isNotBlank(),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text("Send")
        }

        val recent = events.takeLast(12).asReversed()
        if (recent.isNotEmpty()) {
            Text(
                text = "Recent activity",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            recent.forEach { event ->
                Text(
                    text = jamEventText(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }

    if (error != null) {
        Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun jamEventText(event: JamEvent): String {
    val payload = event.payload
    val name = payload["user_name"]?.jsonPrimitive?.contentOrNull
        ?: payload["name"]?.jsonPrimitive?.contentOrNull
        ?: "Listener"
    val text = payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return when (event.type) {
        "chat_message" -> "$name: $text"
        "error" -> "Error: ${payload["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"}"
        "room_created" -> "Room created"
        "user_joined" -> "$name joined"
        "user_left" -> "$name left"
        "host_changed" -> "Room host changed"
        "cohost_changed" -> "Co-host updated"
        "room_mode_changed" -> "Room mode: ${payload["room_mode"]?.jsonPrimitive?.contentOrNull?.replace('_', ' ') ?: "updated"}"
        "suggestion_received" -> "$name suggested a track"
        "queue_updated" -> "Shared queue updated"
        "station_mode" -> "Station playback updated"
        else -> event.type.replace('_', ' ')
    }
}
