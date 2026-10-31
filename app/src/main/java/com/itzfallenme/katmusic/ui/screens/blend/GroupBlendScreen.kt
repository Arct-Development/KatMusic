package com.itzfallenme.katmusic.ui.screens.blend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBlendScreen(onBack: () -> Unit, viewModel: GroupBlendViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var friendName by rememberSaveable { mutableStateOf("") }
    var friendCode by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Group Blend") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = viewModel::refreshMyProfile) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh profile")
                }
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your music, mixed with your people.", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "A taste match built from KatMusic listening history. No Spotify account or login needed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your taste profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (state.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator()
                                Text("Reading listening history…")
                            }
                        } else {
                            Text("${state.myTrackCount} tracks • ${state.myArtistCount} artists")
                            Text(
                                "Copy your code and send it to a friend. Ask them to send their code back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(state.myShareCode)) },
                                enabled = state.myShareCode.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Text("  Copy my Blend code")
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add a friend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = friendName,
                            onValueChange = { friendName = it.take(32) },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = friendCode,
                            onValueChange = { friendCode = it.take(48000) },
                            label = { Text("Paste their Blend code") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                viewModel.addMember(friendName, friendCode)
                                friendName = ""
                                friendCode = ""
                            },
                            enabled = !state.isLoading && friendName.isNotBlank() && friendCode.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add to Blend") }
                    }
                }
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            state.message?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.primary) }
            }
            item { Text("Your group • ${state.members.size}/10", style = MaterialTheme.typography.titleLarge) }
            items(state.members, key = { it.userId }) { member ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${member.trackCount} tracks • ${member.artistCount} artists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!member.isYou) TextButton(onClick = { viewModel.removeMember(member.userId) }) {
                            Text("Remove")
                        } else Text("You", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (state.members.size >= 2) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Taste compatibility", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.result.compatibilityPercent}%",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "KatMusic's own score, based on shared artist and track preferences.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                if (state.result.sharedArtists.isNotEmpty()) {
                    item { Text("Artists you share", style = MaterialTheme.typography.titleMedium) }
                    item { Text(state.result.sharedArtists.joinToString("  •  ") { it.artist }) }
                }
                if (state.result.sharedTracks.isNotEmpty()) {
                    item { Text("Tracks everyone shares", style = MaterialTheme.typography.titleMedium) }
                    items(state.result.sharedTracks, key = { it.key }) { track ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(track.title, fontWeight = FontWeight.Medium)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.result.recommendations.isNotEmpty()) {
                    item { Text("Group picks", style = MaterialTheme.typography.titleMedium) }
                    items(state.result.recommendations, key = { it.track.key }) { recommendation ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(recommendation.track.title, fontWeight = FontWeight.Medium)
                            Text(
                                "${recommendation.track.artist} • ${recommendation.memberCoverage}/${state.members.size} members",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Add at least one friend to calculate compatibility and group picks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
