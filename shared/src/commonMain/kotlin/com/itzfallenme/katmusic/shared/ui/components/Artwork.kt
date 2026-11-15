package com.itzfallenme.katmusic.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itzfallenme.katmusic.ui.theme.ArtworkShape

/**
 * Artwork for a track, from any platform URI (file:// on desktop,
 * content:// or https:// on Android). Coil 3 is multiplatform, so the same
 * composable loads both.
 */
@Composable
fun Artwork(
    uri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = ArtworkShape,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (uri.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** mm:ss (or h:mm:ss) for any duration in milliseconds. Shared by every row. */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    fun two(value: Long) = if (value < 10) "0$value" else value.toString()
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}

/** 48dp is the shared row artwork size; larger views scale from it. */
val RowArtworkSize = 48.dp
