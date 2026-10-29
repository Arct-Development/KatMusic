package com.itzfallenme.katmusic.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat Material 3 Expressive container treatment.
 *
 * The app used to paint a frosted, rim-lit "glass" sheet behind every card. That
 * read as a generic glassmorphism theme rather than the M3 Expressive surface
 * language: stock surfaces are opaque tonal fills with a single quiet shadow,
 * and depth comes from *tonal elevation*, not from translucency and borders.
 * This keeps the old call sites working while rendering the plain M3 surface.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = CardShape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fillAlpha: Float = 1f,
    borderWidth: Dp = 0.dp,
    glow: Color = MaterialTheme.colorScheme.primary,
    sheen: Boolean = false
): Modifier {
    return this
        .clip(shape)
        .background(tint.copy(alpha = fillAlpha.coerceIn(0.6f, 1f)))
}

/**
 * M3 surface container used for cards, mini player and floating chrome.
 * Solid fill, small shadow, no border or gradient.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    elevation: Dp = 0.dp,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fillAlpha: Float = 1f,
    glow: Color = MaterialTheme.colorScheme.primary,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = tint.copy(alpha = fillAlpha.coerceIn(0.6f, 1f)),
        shadowElevation = elevation.coerceIn(0.dp, 10.dp),
    ) {
        Box(contentAlignment = contentAlignment, content = content)
    }
}

/** Circular tonal button, the M3 Expressive default for player and header actions. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    enabled: Boolean = true
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = background,
            contentColor = tint,
            disabledContainerColor = background.copy(alpha = 0.4f),
            disabledContentColor = tint.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Slow-drifting colour wash behind the content. Kept deliberately faint so the
 * tonal surfaces above it stay readable; the artwork tint reads as ambient light,
 * not as a translucent pane.
 */
@Composable
fun AmbientBackdrop(
    accent: Color,
    modifier: Modifier = Modifier,
    intensity: Float = 1f
) {
    val dark = LocalIsDarkTheme.current
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val secondary = ColorEngine.hsl((ColorEngine.hueOf(accent) + 55) % 360, 30, if (dark) 40 else 60)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawWithCache {
                val baseAlpha = (if (dark) 0.20f else 0.16f) * intensity
                val top = Offset(
                    x = size.width * (0.15f + drift * 0.25f),
                    y = size.height * (0.05f + drift * 0.08f)
                )
                val bottom = Offset(
                    x = size.width * (0.9f - drift * 0.25f),
                    y = size.height * (0.85f - drift * 0.1f)
                )
                val radius = size.maxDimension * 0.85f
                val topBrush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = baseAlpha), Color.Transparent),
                    center = top,
                    radius = radius
                )
                val bottomBrush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = baseAlpha * 0.75f), Color.Transparent),
                    center = bottom,
                    radius = radius
                )
                onDrawBehind {
                    drawRect(topBrush)
                    drawRect(bottomBrush)
                }
            }
    )
}
