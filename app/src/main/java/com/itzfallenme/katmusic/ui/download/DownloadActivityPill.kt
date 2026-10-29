package com.itzfallenme.katmusic.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.playback.DownloadProgress
import com.itzfallenme.katmusic.playback.DownloadState
import com.itzfallenme.katmusic.ui.Artwork
import com.itzfallenme.katmusic.ui.theme.GlassChrome
import com.itzfallenme.katmusic.ui.theme.Motion
import com.itzfallenme.katmusic.ui.theme.bounceClickable

/**
 * Floating status for the download queue.
 *
 * Downloads keep running while the user browses elsewhere, and without this the
 * only feedback lived on the Downloads screen itself - tapping the download
 * button on a song looked like nothing happened. The pill stays out of the way
 * when the queue is empty, follows whichever transfer is active, and opens the
 * full manager on tap.
 */
@Composable
fun DownloadActivityPill(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: com.kyant.backdrop.Backdrop? = null
) {
    val progress by ServiceLocator.downloads.progress.collectAsState()
    val queue by ServiceLocator.downloads.queue.collectAsState()
    val activeId by ServiceLocator.downloads.activeTrackId.collectAsState()

    val active: DownloadProgress? = remember(progress, activeId) {
        activeId?.let { progress[it] } ?: progress.values.firstOrNull { it.running }
    }
    val visible = active != null && active.track != null

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.spatialDefault()) { it } + fadeIn(Motion.effectsDefault()),
        exit = slideOutVertically(Motion.spatialFast()) { it } + fadeOut(Motion.effectsFast()),
        modifier = modifier
    ) {
        val current = active ?: return@AnimatedVisibility
        val track = current.track ?: return@AnimatedVisibility
        // Everything still waiting behind the active transfer, so the count reads
        // as "1 of 5" rather than pretending the queue is a single file.
        val waiting = progress.values.count {
            it.state == DownloadState.QUEUED || it.state == DownloadState.WAITING_NETWORK
        }
        val total = maxOf(queue.size, waiting + 1)
        val position = (total - waiting).coerceIn(1, total)

        GlassChrome(
            backdrop = backdrop,
            shape = com.itzfallenme.katmusic.ui.theme.CardShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClickable(targetScale = 0.985f, onClick = onOpen)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    url = track.artwork,
                    modifier = Modifier.size(40.dp),
                    corner = 13.dp,
                    fallbackIcon = Icons.Filled.DownloadDone
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            statusLine(current, position, total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    if (current.running) {
                        LinearProgressIndicator(
                            progress = { current.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { ServiceLocator.downloads.cancel(track.id) }
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel download",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "45% · 2 of 5" while transferring, a plain status word otherwise. */
private fun statusLine(progress: DownloadProgress, position: Int, total: Int): String {
    val counter = if (total > 1) " · $position of $total" else ""
    return when {
        progress.state == DownloadState.WAITING_NETWORK -> "Waiting for Wi-Fi$counter"
        progress.running -> "${(progress.fraction * 100).toInt().coerceIn(0, 100)}%$counter"
        progress.state == DownloadState.FAILED -> "Failed · tap to retry"
        else -> "Queued$counter"
    }
}
