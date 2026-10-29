package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import com.itzfallenme.katmusic.ui.theme.MorphingShapeLoader

/**
 * Listen Together: one phone hosts the playback and up to a handful of friends
 * on the same Wi-Fi follow it. No account, no server, no internet needed beyond
 * the stream itself.
 */
@Composable
fun ListenTogetherScreen(nav: Navigator) {
    val room by ServiceLocator.listenTogether.state.collectAsState()
    val account by ServiceLocator.account.state.collectAsState()
    var name by remember { mutableStateOf(account.name.ifBlank { "" }) }
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Listen together",
            subtitle = "Sync playback on the same Wi-Fi",
            onBack = { nav.back() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            when {
                room.hosting -> HostingCard(
                    room = room,
                    onStop = { ServiceLocator.listenTogether.leave() }
                )
                room.connected -> JoinedCard(
                    room = room,
                    onLeave = { ServiceLocator.listenTogether.leave() }
                )
                else -> SetupCard(
                    name = name,
                    onNameChange = { name = it },
                    code = code,
                    onCodeChange = { input ->
                        code = input.uppercase().filter(Char::isLetterOrDigit).take(4)
                    },
                    address = address,
                    onAddressChange = { address = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    busy = room.busy,
                    error = room.error,
                    onHost = { ServiceLocator.listenTogether.host(name) },
                    onJoin = { ServiceLocator.listenTogether.join(code, name, address) }
                )
            }

            Spacer(Modifier.height(16.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("How it works", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The host's phone leads: everyone else follows its track, play/pause and " +
                            "seeks. Both phones must be on the same Wi-Fi, and the app needs to stay " +
                            "open on the host while the room is live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Anyone on this network who knows the code can join. Audio is streamed " +
                            "from YouTube Music separately on each phone, so every listener plays " +
                            "through their own connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SetupCard(
    name: String,
    onNameChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onHost: () -> Unit,
    onJoin: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Start a room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your phone plays the music; friends' phones follow it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onHost,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Host a room") }

            Spacer(Modifier.height(22.dp))
            Text("Join a room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("4-character code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("Host IP (leave empty to find automatically)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onJoin,
                enabled = !busy && code.length == 4,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Join room") }

            if (busy) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorphingShapeLoader(size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking for the room…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun HostingCard(
    room: com.itzfallenme.katmusic.playback.ListenTogetherUiState,
    onStop: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "ROOM CODE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = room.roomCode,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    letterSpacing = 10.sp,
                    fontWeight = FontWeight.Black
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Share this code with friends on this Wi-Fi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (room.hostAddress.isNotBlank()) {
                Text(
                    "If discovery is blocked, they can enter ${room.hostAddress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (room.guests.size) {
                        0 -> "Waiting for listeners"
                        1 -> "1 listener: ${room.guests.first()}"
                        else -> "${room.guests.size} listeners: ${room.guests.joinToString()}"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Stop hosting")
            }
        }
    }
}

@Composable
private fun JoinedCard(
    room: com.itzfallenme.katmusic.playback.ListenTogetherUiState,
    onLeave: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "LISTENING WITH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                room.hostName.ifBlank { "the host" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Room ${room.roomCode} • playback follows the host",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircleSpinner()
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Leave room")
            }
        }
    }
}

@Composable
private fun CircleSpinner() {
    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
}
