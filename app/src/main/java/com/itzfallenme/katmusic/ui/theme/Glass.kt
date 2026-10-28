package com.itzfallenme.katmusic.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frosted surface every card, dock and control sits on: a soft two-stop gradient,
 * a specular top sheen and a light-catching rim. Over a dark, artwork-tinted background
 * this reads as glass without needing to sample what is behind it.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = CardShape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fillAlpha: Float = 0.66f,
    borderWidth: Dp = 1.dp,
    glow: Color = MaterialTheme.colorScheme.primary,
    sheen: Boolean = true
): Modifier {
    val dark = LocalIsDarkTheme.current
    val rim = if (dark) Color.White else Color.White
    return this
        .clip(shape)
        .drawWithCache {
            val fill = Brush.linearGradient(
                colors = listOf(
                    tint.copy(alpha = (fillAlpha + 0.08f).coerceAtMost(0.96f)),
                    tint.copy(alpha = (fillAlpha - 0.2f).coerceAtLeast(0.2f))
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val gloss = Brush.verticalGradient(
                colors = listOf(
                    rim.copy(alpha = if (dark) 0.10f else 0.5f),
                    Color.Transparent
                ),
                endY = size.height * 0.55f
            )
            val cornerGlow = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = if (dark) 0.16f else 0.22f), Color.Transparent),
                center = Offset(size.width * 0.15f, 0f),
                radius = size.maxDimension * 0.8f
            )
            onDrawBehind {
                drawRect(fill)
                drawRect(cornerGlow)
                if (sheen) drawRect(gloss)
                drawInnerEdge(rim, dark)
            }
        }
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    rim.copy(alpha = if (dark) 0.34f else 0.85f),
                    rim.copy(alpha = 0.05f),
                    glow.copy(alpha = 0.14f),
                    rim.copy(alpha = 0.02f)
                )
            ),
            shape = shape
        )
}

private fun DrawScope.drawInnerEdge(rim: Color, dark: Boolean) {
    // Thin top highlight makes the surface feel lit from above.
    drawLine(
        color = rim.copy(alpha = if (dark) 0.16f else 0.5f),
        start = Offset(size.width * 0.12f, 1f),
        end = Offset(size.width * 0.88f, 1f),
        strokeWidth = 1.4f
    )
}

/** Glass container with drop shadow, used for cards and floating chrome. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    elevation: Dp = 0.dp,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fillAlpha: Float = 0.66f,
    glow: Color = MaterialTheme.colorScheme.primary,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val shadowed = if (elevation > 0.dp) {
        modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.5f),
            spotColor = glow.copy(alpha = 0.35f)
        )
    } else modifier
    Box(
        modifier = shadowed.liquidGlass(
            shape = shape,
            tint = tint,
            fillAlpha = fillAlpha,
            glow = glow
        ),
        contentAlignment = contentAlignment,
        content = content
    )
}

/** Circular glass button used in the player and top bars. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(shape = CircleShape, tint = background, fillAlpha = 0.5f)
            .bounceClickable(enabled = enabled, targetScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Slow-drifting colour blobs behind the content. Seeded from the current album art,
 * it gives every screen the living wallpaper feel the design language is built around.
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
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val secondary = ColorEngine.hsl((ColorEngine.hueOf(accent) + 55) % 360, 34, if (dark) 42 else 62)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawWithCache {
                val baseAlpha = (if (dark) 0.34f else 0.28f) * intensity
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
