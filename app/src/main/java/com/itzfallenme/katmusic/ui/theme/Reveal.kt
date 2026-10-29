package com.itzfallenme.katmusic.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Staggered entrance for list content.
 *
 * The first eight rows animate in with a short cascade - fade plus a small upward
 * spring - and everything after that appears with the last row, so a long list never
 * spends a second revealing itself. Rows only animate once per composition; scrolling
 * back to a row does not replay it.
 */
@Composable
fun RevealItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 8) * 45L))
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.effectsDefault(),
        label = "revealAlpha"
    )
    val offset by animateFloatAsState(
        targetValue = if (shown) 0f else 30f,
        animationSpec = Motion.spatialDefault(),
        label = "revealOffset"
    )
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.97f,
        animationSpec = Motion.spatialDefault(),
        label = "revealScale"
    )
    Box(
        modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offset
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}
