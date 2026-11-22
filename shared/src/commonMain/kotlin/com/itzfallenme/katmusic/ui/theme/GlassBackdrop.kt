package com.itzfallenme.katmusic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * The desktop glass system.
 *
 * Android gets true background refraction from Kyant's Backdrop renderer,
 * which is Android-only. This is the portable equivalent that is still real
 * rather than painted-on: the app draws a wallpaper once, and every glass
 * surface draws that same wallpaper again, aligned to its own position in the
 * window, then blurs it and lays a translucent tint and rim on top. Because
 * the sample is the actual backdrop, panels genuinely frost what is behind
 * them instead of faking it with gradients.
 *
 * Scrolling content is not sampled (Compose has no cross-layer backdrop
 * filter); the wallpaper behind the navigation rail and the player dock is.
 */

/** One soft colour orb of the wallpaper, positioned in fractional surface space. */
data class GlassBlob(
    val color: Color,
    val center: Offset,
    val radiusScale: Float,
)

/**
 * The app backdrop: a base colour, soft accent orbs and light beams. The beams
 * matter: they are the structure the frozen panels blur, so the frost reads as
 * glass instead of a tinted card.
 */
data class GlassWallpaper(
    val base: Color,
    val blobs: List<GlassBlob>,
)

private val GlassAccentBlue = Color(0xFF0877F9)
private val GlassAccentCyan = Color(0xFF21C4FF)

/** Wallpaper currently behind the app chrome, or null when glass is off. */
val LocalGlassWallpaper = staticCompositionLocalOf<GlassWallpaper?> { null }

/** Size of the window content, used to keep glass samples aligned to the root. */
val LocalGlassRootSize = staticCompositionLocalOf { IntSize.Zero }

/** Builds the accent wallpaper for the active colour scheme. */
fun glassWallpaperFor(
    isDark: Boolean,
    background: Color,
    primary: Color,
    tertiary: Color,
): GlassWallpaper {
    // KatMusic's signature blue/cyan keeps the backdrop on-brand instead of a
    // flat theme-purple wash, and gives the frost two hues to blend.
    val blue = if (isDark) GlassAccentBlue else GlassAccentBlue.copy(alpha = 0.9f)
    val cyan = if (isDark) GlassAccentCyan else GlassAccentCyan.copy(alpha = 0.9f)
    val primaryAlpha = if (isDark) 0.62f else 0.42f
    val tertiaryAlpha = if (isDark) 0.52f else 0.36f
    return GlassWallpaper(
        base = background,
        blobs = listOf(
            GlassBlob(
                color = blue.copy(alpha = primaryAlpha),
                center = Offset(0.16f, 0.10f),
                radiusScale = 0.78f,
            ),
            GlassBlob(
                color = cyan.copy(alpha = tertiaryAlpha),
                center = Offset(0.86f, 0.24f),
                radiusScale = 0.82f,
            ),
            GlassBlob(
                color = tertiary.copy(alpha = primaryAlpha * 0.45f),
                center = Offset(0.52f, 1.04f),
                radiusScale = 0.9f,
            ),
        ),
    )
}

/** Draws the wallpaper filling [size]; shared by the page and by glass panels. */
fun DrawScope.drawGlassWallpaper(wallpaper: GlassWallpaper, size: Size = this.size) {
    drawRect(color = wallpaper.base, topLeft = Offset.Zero, size = size)
    // Wide diagonal light beams: low-contrast structure for the frost to blur.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.055f),
                Color.Transparent,
                Color.White.copy(alpha = 0.035f),
            ),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
        ),
        size = size,
    )
    val maxDimension = maxOf(size.width, size.height)
    wallpaper.blobs.forEach { blob ->
        val radius = maxDimension * blob.radiusScale
        val center = Offset(size.width * blob.center.x, size.height * blob.center.y)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blob.color, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** The page backdrop every glass panel samples. */
@Composable
fun GlassBackdrop(
    wallpaper: GlassWallpaper,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) { drawGlassWallpaper(wallpaper) }
}

/**
 * A frosted panel: the wallpaper behind [modifier]'s position, blurred, with a
 * translucent tint, a bright rim and a soft specular glint on top.
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 28.dp,
    shadowElevation: Dp = 10.dp,
    tintAlpha: Float = if (LocalIsDarkTheme.current) 0.18f else 0.40f,
    content: @Composable BoxScope.() -> Unit,
) {
    val wallpaper = LocalGlassWallpaper.current
    val rootSize = LocalGlassRootSize.current
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    var origin by remember { mutableStateOf(Offset.Zero) }

    if (wallpaper == null) {
        Box(
            modifier = modifier
                .shadow(elevation = shadowElevation, shape = shape, clip = false)
                .clip(shape)
                .background(tint),
            content = content,
        )
        return
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .shadow(elevation = shadowElevation, shape = shape, clip = false)
            .clip(shape),
    ) {
        // The real backdrop, sampled at this panel's window position.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded),
        ) {
            translate(-origin.x, -origin.y) {
                drawGlassWallpaper(
                    wallpaper = wallpaper,
                    size = Size(rootSize.width.toFloat(), rootSize.height.toFloat()),
                )
            }
        }
        // Frost tint: brighter towards the top edge like real frosted glass.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = tintAlpha),
                            tint.copy(alpha = tintAlpha * 0.72f),
                        ),
                    ),
                ),
        )
        // Specular glint across the surface.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.03f),
                        ),
                        start = Offset.Zero,
                        end = Offset(1200f, 900f),
                    ),
                ),
        )
        // Bright rim, dimmer along the bottom.
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (LocalIsDarkTheme.current) 0.30f else 0.75f),
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = if (LocalIsDarkTheme.current) 0.14f else 0.35f),
                        ),
                    ),
                    shape = shape,
                ),
        )
        content()
    }
}
