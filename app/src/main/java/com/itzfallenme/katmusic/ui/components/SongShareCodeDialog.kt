package com.itzfallenme.katmusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.itzfallenme.katmusic.util.FileExportHelper
import com.itzfallenme.katmusic.util.ShareLinks
import kotlinx.coroutines.launch

/** Branded KatMusic Code: a scannable code plus a human-readable backup. */
@Composable
fun SongShareCodeDialog(
    title: String,
    artist: String,
    url: String,
    code: String,
    album: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val codeUrl = remember(code, title, artist, album) {
        ShareLinks.trackCodeUrl(code, title, artist, album) ?: url
    }
    val brandLogo: Bitmap? = remember(context) {
        BitmapFactory.decodeResource(context.resources, com.itzfallenme.katmusic.R.drawable.ic_launcher_logo)
    }
    val bitmap = remember(code) { createKatMusicCodeTag(code) }
    val shareCard = remember(title, artist, album, code, brandLogo) {
        createKatMusicCodeCard(title, artist, code, album, brandLogo)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("KatMusic Code") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0E1722))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        brandLogo?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "KatMusic logo",
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)),
                            )
                        }
                        Text(
                            "KAT MUSIC · SONG CODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF21C4FF),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    album?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "KatMusic Code barcode; scan it in KatMusic",
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                    )
                }
                Text(
                    text = code,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF21C4FF),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Scan this tag in KatMusic from Search. The short code below is a manual backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Button(
                    onClick = {
                        val card = shareCard ?: return@Button
                        scope.launch {
                            val result = FileExportHelper(context.applicationContext)
                                .shareImage(
                                    activityContext = context,
                                    bitmap = card,
                                    title = title,
                                    filePrefix = "katmusic_code",
                                    shareLabel = "Share KatMusic Code",
                                )
                            if (result.isFailure) {
                                ShareLinks.send(
                                    context,
                                    "$codeUrl\n\nKatMusic code: $code",
                                    "$title — $artist · KatMusic Code",
                                )
                            }
                        }
                    },
                    enabled = shareCard != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share code card")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                onDismiss()
            }) { Text("Copy code") }
        },
        dismissButton = {
            TextButton(onClick = {
                ShareLinks.send(
                    context,
                    "$codeUrl\n\nKatMusic code: $code",
                    "$title — $artist · KatMusic Code",
                )
                onDismiss()
            }) { Text("Share code link") }
        },
    )
}
