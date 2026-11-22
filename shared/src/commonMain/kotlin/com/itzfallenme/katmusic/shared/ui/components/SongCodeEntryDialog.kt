package com.itzfallenme.katmusic.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.share.KatMusicCode
import kotlinx.coroutines.launch

/**
 * The receiving half of KatMusic Code sharing.
 *
 * Android scans the code card with its camera; desktop has no scanner, so the
 * same code can be typed or pasted here (and Search accepts it too). The code
 * is validated locally — checksum, alphabet and payload — before any network
 * work, so a mistyped code never opens the wrong song.
 */
@Composable
fun SongCodeEntryDialog(
    onDismiss: () -> Unit,
    onOpen: suspend (String) -> Result<Track>,
    onPlay: (Track) -> Unit,
    /**
     * Optional platform reader for a saved code card. Android scans with the
     * camera; desktop has no camera, so it reads the image instead.
     */
    onPickImage: (suspend () -> String?)? = null,
) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val videoId = remember(input) { KatMusicCode.decode(input) }
    val hasInput = input.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Open a KatMusic Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the code from a shared KatMusic card. Scanning needs the Android app; the code itself works on both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        // Codes are Crockford base32 plus separators; drop
                        // anything else as it is typed or pasted.
                        input = it.uppercase()
                            .filter { ch -> ch.isLetterOrDigit() || ch == '-' }
                            .take(32)
                        error = null
                    },
                    label = { Text("Code") },
                    placeholder = { Text("KM-XXXXX-XXXXX-XXXXX-XXXXX") },
                    singleLine = true,
                    isError = hasInput && videoId == null,
                    supportingText = {
                        when {
                            videoId != null -> Text(
                                text = "Valid code",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            hasInput -> Text("That code isn't valid. Check it and try again.")
                            else -> Text("Tip: Search also accepts a code.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onPickImage != null && !busy) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                val picked = runCatching { onPickImage() }.getOrNull()
                                busy = false
                                if (picked != null) {
                                    input = picked
                                } else {
                                    error = "No KatMusic Code found in that image"
                                }
                            }
                        },
                    ) {
                        Text("Open a code card image")
                    }
                }
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = videoId != null && !busy,
                onClick = {
                    val id = videoId ?: return@TextButton
                    scope.launch {
                        busy = true
                        error = null
                        onOpen(id)
                            .onSuccess { track ->
                                busy = false
                                onPlay(track)
                                onDismiss()
                            }
                            .onFailure { failure ->
                                busy = false
                                error = failure.message ?: "Couldn't open this KatMusic Code"
                            }
                    }
                },
            ) {
                Text("Play song")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
