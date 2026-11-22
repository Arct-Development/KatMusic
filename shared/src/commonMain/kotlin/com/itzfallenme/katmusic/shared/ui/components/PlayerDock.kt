package com.itzfallenme.katmusic.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.ui.theme.ExpressivePillShape
import com.itzfallenme.katmusic.ui.theme.GlassSurface
import com.itzfallenme.katmusic.ui.theme.LocalGlassWallpaper
import com.itzfallenme.katmusic.ui.theme.LocalLiquidGlass
import com.itzfallenme.katmusic.ui.theme.liquidGlassSurface

/**
 * The compact player bar. Shown on both platforms above the navigation
 * surface; tapping it opens the full now-playing screen.
 *
 * When the app shell provides a backdrop wallpaper the pill is real frosted
 * glass (the wallpaper behind it is blurred); platforms without one fall back
 * to the painted glass material so the chrome still matches.
 */
@Composable
fun PlayerDock(
    state: PlayerState,
    player: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQueue: (() -> Unit)? = null,
) {
    val current = state.current ?: return
    val glassEnabled = LocalLiquidGlass.current
    val hasBackdrop = LocalGlassWallpaper.current != null
    val body: @Composable BoxScope.() -> Unit = {
        DockContent(
            state = state,
            player = player,
            current = current,
            onExpand = onExpand,
            onOpenQueue = onOpenQueue,
        )
    }
    when {
        glassEnabled && hasBackdrop -> GlassSurface(
            shape = ExpressivePillShape,
            modifier = modifier.fillMaxWidth(),
            shadowElevation = 10.dp,
            tintAlpha = 0.30f,
            content = body,
        )

        else -> Box(
            modifier = modifier
                .fillMaxWidth()
                .liquidGlassSurface(shape = ExpressivePillShape, shadowElevation = 10.dp),
            content = body,
        )
    }
}

@Composable
private fun BoxScope.DockContent(
    state: PlayerState,
    player: PlayerController,
    current: com.itzfallenme.katmusic.shared.models.Track,
    onExpand: () -> Unit,
    onOpenQueue: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(
                uri = current.artwork,
                modifier = Modifier.size(44.dp),
                contentDescription = current.title,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = current.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { player.togglePlayPause() }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
            }
            IconButton(onClick = { player.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
            }
            if (onOpenQueue != null) {
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue")
                }
            }
        }
    }
}
