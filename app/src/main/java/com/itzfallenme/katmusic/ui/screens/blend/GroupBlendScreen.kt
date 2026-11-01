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
import androidx.compose.material.icons.filled.PlayArrow
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
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var blendName by rememberSaveable { mutableStateOf("Our Blend") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

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
                        Text("Online Blend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        when {
                            !state.cloudConfigured -> {
                                Text("Cloud Blend is ready in the app code, but this build has no Blend Supabase configuration. Add KATMUSIC_BLEND_SUPABASE_URL and KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY to your local environment or GitHub secrets, then rebuild.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            state.signedInEmail == null -> {
                                Text("Sign in so your Blend groups and taste profile can sync across devices.")
                                OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(password, { password = it.take(128) }, label = { Text("Password (8+ characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.signIn(email, password, false) }, enabled = !state.isLoading && email.isNotBlank() && password.length >= 8, modifier = Modifier.weight(1f)) { Text("Sign in") }
                                    OutlinedButton(onClick = { viewModel.signIn(email, password, true) }, enabled = !state.isLoading && email.isNotBlank() && password.length >= 8, modifier = Modifier.weight(1f)) { Text("Create account") }
                                }
                                Text("If email confirmation is enabled in Supabase, confirm your address before signing in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(state.signedInEmail ?: "Signed in", fontWeight = FontWeight.SemiBold)
                                        Text("Taste profile syncs to your separate Blend project.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = viewModel::signOut) { Text("Sign out") }
                                }
                                if (state.activeBlendId == null) {
                                    OutlinedTextField(displayName, { displayName = it.take(32) }, label = { Text("Your display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(blendName, { blendName = it.take(60) }, label = { Text("New Blend name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = { viewModel.createCloudBlend(blendName, displayName) }, enabled = !state.isLoading && displayName.isNotBlank() && blendName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create a Blend") }
                                    Text("Or join an existing group", style = MaterialTheme.typography.titleSmall)
                                    OutlinedTextField(inviteCode, { inviteCode = it.take(32) }, label = { Text("Invitation code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedButton(onClick = { viewModel.joinCloudBlend(inviteCode, displayName) }, enabled = !state.isLoading && displayName.isNotBlank() && inviteCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Join Blend") }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Your online Blend", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        IconButton(onClick = viewModel::refreshCloudBlend) { Icon(Icons.Default.Refresh, contentDescription = "Refresh online Blend") }
                                        TextButton(onClick = viewModel::leaveCloudBlend) { Text("Leave") }
                                    }
                                    state.inviteCode?.let { code ->
                                        Text("Invitation code: $code", fontWeight = FontWeight.SemiBold)
                                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Text("  Copy invitation code")
                                        }
                                    }
                                    Text("Members: ${state.cloudMembers.size}/10")
                                    state.cloudMembers.forEach { member ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(member.displayName, fontWeight = FontWeight.Medium)
                                                Text("${member.trackCount} tracks · ${member.artistCount} artists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (member.isYou) Text("You", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                            Text("${state.myTrackCount} tracks • ${state.myArtistCount} artists", style = MaterialTheme.typography.titleMedium)
                        Text("Your full listening history stays on this device. Only the compact profile in your share code is sent to friends.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                "Average taste similarity across every pair in this group, weighted toward artists (75%) and tracks (25%). This is a KatMusic score, not a popularity ranking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                if (state.result.sharedArtists.isNotEmpty()) {
                    item { Text("Your common ground", style = MaterialTheme.typography.titleMedium) }
                    items(state.result.sharedArtists, key = { it.artist }) { artist ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(artist.artist, fontWeight = FontWeight.SemiBold)
                                    Text("Shared by ${artist.memberCount} of ${state.members.size} listeners", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${(artist.averageAffinity * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (state.result.sharedTracks.isNotEmpty()) {
                    item { Text("Tracks everyone shares", style = MaterialTheme.typography.titleMedium) }
                    item { Text("These tracks appear in every member profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.result.sharedTracks, key = { it.key }) { track ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.Medium)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.playTrack(track) }, enabled = !track.videoId.isNullOrBlank()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play ${track.title}")
                            }
                        }
                    }
                }
                if (state.result.recommendations.isNotEmpty()) {
                    item { Text("Made for this group", style = MaterialTheme.typography.titleMedium) }
                    item { Text("Recommendations balance how strongly people like a track with how many members have it in their profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.result.recommendations, key = { it.track.key }) { recommendation ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(recommendation.track.title, fontWeight = FontWeight.Medium)
                                Text(
                                    "${recommendation.track.artist} • in ${recommendation.memberCoverage} of ${state.members.size} profiles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { viewModel.playTrack(recommendation.track) },
                                enabled = !recommendation.track.videoId.isNullOrBlank(),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play ${recommendation.track.title}")
                            }
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
