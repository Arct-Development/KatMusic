package com.itzfallenme.katmusic.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Material 3 window size classes based on viewport width breakpoints.
 * - COMPACT: Standard phones in portrait (< 600dp)
 * - MEDIUM: Foldables unfolded, 7-8" tablets, portrait 10" tablets (600dp..839dp)
 * - EXPANDED: Large tablets (10"+), landscape tablets, desktop/Chromebook (>= 840dp)
 */
enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * Reads the window width from the composition (not from Android's
 * LocalConfiguration, which does not exist on desktop), so a resized desktop
 * window re-classifies exactly like a rotated Android device.
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthPx = windowInfo.containerSize.width
    val widthDp = with(density) { widthPx.toDp() }
    return when {
        widthDp < 600.dp -> WindowSizeClass.COMPACT
        widthDp < 840.dp -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
}

/** Returns true if the current viewport is tablet-wide or wider (>= 600dp). */
@Composable
fun isTabletOrWideScreen(): Boolean = rememberWindowSizeClass() != WindowSizeClass.COMPACT

/** Returns true if the current display is an expanded display (>= 840dp). */
@Composable
fun isExpandedScreen(): Boolean = rememberWindowSizeClass() == WindowSizeClass.EXPANDED

@Composable
fun Modifier.adaptiveContentWidth(maxWidth: Dp = 840.dp): Modifier {
    return if (isTabletOrWideScreen()) {
        this.widthIn(max = maxWidth).fillMaxWidth()
    } else {
        this
    }
}
