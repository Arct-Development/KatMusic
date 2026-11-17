package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.data.DesktopLastFmRepository
import com.itzfallenme.katmusic.desktop.data.LastFmFriend
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun DesktopLastFmSection(repository: DesktopLastFmRepository) {
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(repository.apiKey) }
    var apiSecret by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(repository.isConnected) }
    var connecting by remember { mutableStateOf(false) }
    var connectionJob by remember { mutableStateOf<Job?>(null) }
    var loadingSocial by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<LastFmFriend>?>(null) }
    var topArtists by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Text(
        "Last.fm",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        if (connected) {
            "Connected as ${repository.username}. Scrobbles and friend/profile lookups go directly to Last.fm."
        } else {
            "Optional scrobbling and social profile. Your Last.fm credentials stay in this computer's user preferences."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    if (!connected) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Last.fm API key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            enabled = !connecting,
        )
        OutlinedTextField(
            value = apiSecret,
            onValueChange = { apiSecret = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Last.fm shared secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            enabled = !connecting,
        )
        Text(
            "Create an API key at last.fm/api/account/create. KatMusic does not provide or share an app-wide key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    error = null
                    repository.saveApiCredentials(apiKey, apiSecret)
                    connecting = true
                    connectionJob = scope.launch {
                        repository.connectWithBrowser()
                            .onSuccess {
                                connected = true
                                apiSecret = ""
                                error = "Connected as $it"
                            }
                            .onFailure {
                                error = it.message ?: "Last.fm sign-in failed"
                            }
                        connecting = false
                        connectionJob = null
                    }
                },
                enabled = !connecting && apiKey.isNotBlank() && apiSecret.isNotBlank(),
            ) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Waiting for browser approval…")
                } else {
                    Text("Connect Last.fm")
                }
            }
            if (connecting) {
                TextButton(
                    onClick = {
                        connectionJob?.cancel()
                        connectionJob = null
                        connecting = false
                    },
                ) { Text("Cancel") }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    error = null
                    loadingSocial = true
                    scope.launch {
                        repository.loadFriends()
                            .onSuccess { friends = it }
                            .onFailure { error = it.message ?: "Couldn't load friends" }
                        repository.loadTopArtists()
                            .onSuccess { topArtists = it }
                            .onFailure { if (error == null) error = it.message ?: "Couldn't load top artists" }
                        loadingSocial = false
                    }
                },
                enabled = !loadingSocial,
            ) {
                if (loadingSocial) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Loading…")
                } else {
                    Text("Load profile & friends")
                }
            }
            TextButton(
                onClick = {
                    repository.disconnect()
                    connected = false
                    friends = null
                    topArtists = null
                    error = null
                },
            ) { Text("Disconnect") }
        }
        topArtists?.takeIf { it.isNotEmpty() }?.let { artists ->
            Text("Top artists", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            artists.take(5).forEachIndexed { index, (name, plays) ->
                Text(
                    "${index + 1}. $name · $plays plays",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        friends?.let { people ->
            Text("Friends · ${people.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            people.take(8).forEach { friend ->
                Text(
                    friend.realName.takeIf(String::isNotBlank)?.let { "$it (@${friend.username})" } ?: friend.username,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (people.size > 8) {
                Text(
                    "Showing the first 8 friends.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    error?.let { message ->
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (message.startsWith("Connected")) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
