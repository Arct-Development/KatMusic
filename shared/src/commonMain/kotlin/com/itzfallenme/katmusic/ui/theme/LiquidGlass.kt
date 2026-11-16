package com.itzfallenme.katmusic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shared liquid-glass material used by desktop and iOS.
 *
 * Android has Kyant's Backdrop renderer for true background refraction. That
 * renderer is Android-only, so this is the portable equivalent: a translucent
 * frosted fill, a moving-light style specular sheen, a bright rim and a soft
 * drop shadow. It is drawn entirely with Compose primitives, which means the
 * exact same chrome appears on desktop windows and in the iOS app.
 *
 * When [LocalLiquidGlass] is off (or the caller opts out), the modifier is a
 * no-op and callers keep their solid Material surface.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    enabled: Boolean = LocalLiquidGlass.current,
    isDark: Boolean = LocalIsDarkTheme.current,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation: Dp = 8.dp,
    intensity: Float = 1f,
): Modifier {
    if (!enabled) return this

    val frost = tint.copy(alpha = if (isDark) 0.46f else 0.58f)
    val topSheen = if (isDark) {
        Color.White.copy(alpha = 0.15f * intensity)
    } else {
        Color.White.copy(alpha = 0.62f * intensity)
    }
    val bottomShade = if (isDark) {
        Color.Black.copy(alpha = 0.16f * intensity)
    } else {
        Color(0xFF0B2A4A).copy(alpha = 0.07f * intensity)
    }
    val rimBright = if (isDark) {
        Color.White.copy(alpha = 0.34f * intensity)
    } else {
        Color.White.copy(alpha = 0.92f * intensity)
    }
    val rimDim = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF6E8FB5).copy(alpha = 0.16f)
    }

    return this
        .shadow(elevation = shadowElevation, shape = shape, clip = false)
        .clip(shape)
        .background(frost)
        .background(
            Brush.verticalGradient(
                0f to topSheen,
                0.42f to Color.Transparent,
                1f to bottomShade,
            ),
        )
        .drawWithCache {
            // A diagonal specular band gives the surface the wet, lens-like
            // glint of liquid glass without needing a backdrop texture.
            val glint = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.11f * intensity),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.045f * intensity),
                ),
                start = Offset(size.width * 0.1f, 0f),
                end = Offset(size.width * 1.15f, size.height),
            )
            onDrawWithContent {
                drawRect(brush = glint)
                drawContent()
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(rimBright, rimDim, rimBright),
            ),
            shape = shape,
        )
}
