package com.itzfallenme.katmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Builds a dark Material 3 style palette from one seed colour. Tones are derived in
 * HSL space so any artwork - neon pink, deep teal, muddy brown - produces a usable,
 * low-chroma dark surface set with a legible accent.
 */
object ColorEngine {

    fun hueOf(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min
        if (delta < 0.0001f) return 220f
        val h = when (max) {
            r -> ((g - b) / delta + if (g < b) 6f else 0f)
            g -> ((b - r) / delta + 2f)
            else -> ((r - g) / delta + 4f)
        } / 6f
        return h * 360f
    }

    fun hsl(h: Float, s: Float, l: Float): Color {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f)
        )
    }

    fun darkScheme(seed: Color, amoled: Boolean, neutralChroma: Float = 0.06f): ColorScheme {
        val h = hueOf(seed)
        val h2 = (h + 40f) % 360f
        val h3 = (h + 320f) % 360f
        val surface = if (amoled) Color(0f, 0f, 0f) else hsl(h, neutralChroma, 0.055f)
        val surfaceLow = hsl(h, neutralChroma, 0.085f)
        val surfaceHigh = hsl(h, neutralChroma + 0.02f, 0.13f)
        return darkColorScheme(
            primary = hsl(h, 0.72f, 0.76f),
            onPrimary = hsl(h, 0.5f, 0.14f),
            primaryContainer = hsl(h, 0.38f, 0.32f),
            onPrimaryContainer = hsl(h, 0.85f, 0.9f),
            inversePrimary = hsl(h, 0.6f, 0.4f),
            secondary = hsl(h2, 0.34f, 0.74f),
            onSecondary = hsl(h2, 0.3f, 0.15f),
            secondaryContainer = hsl(h2, 0.25f, 0.3f),
            onSecondaryContainer = hsl(h2, 0.6f, 0.9f),
            tertiary = hsl(h3, 0.5f, 0.74f),
            onTertiary = hsl(h3, 0.4f, 0.15f),
            tertiaryContainer = hsl(h3, 0.3f, 0.3f),
            onTertiaryContainer = hsl(h3, 0.6f, 0.9f),
            background = surface,
            onBackground = hsl(h, 0.08f, 0.92f),
            surface = surface,
            onSurface = hsl(h, 0.08f, 0.92f),
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = hsl(h, 0.1f, 0.75f),
            surfaceTint = hsl(h, 0.72f, 0.76f),
            inverseSurface = hsl(h, 0.1f, 0.9f),
            inverseOnSurface = hsl(h, 0.1f, 0.15f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = hsl(h, 0.1f, 0.42f),
            outlineVariant = hsl(h, 0.08f, 0.24f),
            scrim = Color.Black,
            surfaceBright = hsl(h, neutralChroma + 0.02f, if (amoled) 0.1f else 0.19f),
            surfaceDim = if (amoled) Color(0f, 0f, 0f) else hsl(h, neutralChroma, 0.035f),
            surfaceContainer = surfaceLow,
            surfaceContainerHigh = hsl(h, neutralChroma, 0.1f),
            surfaceContainerHighest = hsl(h, neutralChroma + 0.01f, 0.14f),
            surfaceContainerLow = if (amoled) Color(0f, 0f, 0f) else hsl(h, neutralChroma, 0.04f),
            surfaceContainerLowest = Color(0f, 0f, 0f)
        )
    }

    fun lightScheme(seed: Color): ColorScheme {
        val h = hueOf(seed)
        val h2 = (h + 40f) % 360f
        val h3 = (h + 320f) % 360f
        return androidx.compose.material3.lightColorScheme(
            primary = hsl(h, 0.55f, 0.38f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, 0.6f, 0.9f),
            onPrimaryContainer = hsl(h, 0.6f, 0.18f),
            secondary = hsl(h2, 0.3f, 0.4f),
            onSecondary = Color.White,
            tertiary = hsl(h3, 0.4f, 0.4f),
            onTertiary = Color.White,
            background = hsl(h, 0.2f, 0.985f),
            surface = hsl(h, 0.2f, 0.985f),
            surfaceVariant = hsl(h, 0.15f, 0.92f),
            onSurfaceVariant = hsl(h, 0.1f, 0.35f),
            surfaceContainer = hsl(h, 0.16f, 0.96f),
            surfaceContainerHigh = hsl(h, 0.16f, 0.93f),
            surfaceContainerLow = hsl(h, 0.2f, 0.98f),
            outline = hsl(h, 0.1f, 0.55f)
        )
    }
}

val KatTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
)

@Composable
fun KatMusicTheme(
    seed: Color = Color(0xFF8AB4F8),
    amoled: Boolean = false,
    light: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (light) ColorEngine.lightScheme(seed) else ColorEngine.darkScheme(seed, amoled)
    MaterialTheme(
        colorScheme = scheme,
        typography = KatTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun isDark(): Boolean = isSystemInDarkTheme()
