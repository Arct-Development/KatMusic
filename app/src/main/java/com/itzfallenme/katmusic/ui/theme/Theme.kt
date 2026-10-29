package com.itzfallenme.katmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Shared flag telling composite content whether the active palette is dark. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Builds the app palette from one seed hue, in HSL space - the same approach the
 * LastWave design language uses. Pastel accents, near-black tinted surfaces and a
 * neutral ramp that keeps album art and glass highlights readable on top.
 */
object ColorEngine {

    private const val CHROMA_PRIMARY = 30
    private const val CHROMA_SECONDARY = 14
    private const val CHROMA_TERTIARY = 22
    private const val CHROMA_NEUTRAL = 5
    private const val CHROMA_NEUTRAL_VARIANT = 8

    fun hueOf(color: Color): Int {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == min) return 0
        val d = max - min
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } / 6f
        return (((h * 360f).toInt() % 360) + 360) % 360
    }

    fun hsl(h: Int, s: Int, l: Int): Color {
        val hf = (((h % 360) + 360) % 360) / 360f
        val sf = s.coerceIn(0, 100) / 100f
        val lf = l.coerceIn(0, 100) / 100f
        if (sf == 0f) return Color(lf, lf, lf)
        val q = if (lf < 0.5f) lf * (1 + sf) else lf + sf - lf * sf
        val p = 2 * lf - q
        fun hue(t: Float): Float {
            var tt = t
            if (tt < 0f) tt += 1f
            if (tt > 1f) tt -= 1f
            return when {
                tt < 1f / 6f -> p + (q - p) * 6f * tt
                tt < 1f / 2f -> q
                tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
                else -> p
            }
        }
        return Color(hue(hf + 1f / 3f), hue(hf), hue(hf - 1f / 3f))
    }

    fun darkScheme(seed: Color, amoled: Boolean): ColorScheme {
        val h = hueOf(seed)
        val hSecondary = (h + 20) % 360
        val hTertiary = (h + 60) % 360
        val bg = if (amoled) 0 else 6
        // Wider steps between container levels: rows sink into the page, cards
        // lift out of it, and the active pill clearly sits on top.
        val container = if (amoled) 4 else 12
        val containerHigh = if (amoled) 9 else 19
        val containerHighest = if (amoled) 14 else 26
        return darkColorScheme(
            primary = hsl(h, CHROMA_PRIMARY + 6, 85),
            onPrimary = hsl(h, CHROMA_PRIMARY, 14),
            primaryContainer = hsl(h, CHROMA_PRIMARY + 2, if (amoled) 26 else 32),
            onPrimaryContainer = hsl(h, CHROMA_PRIMARY - 3, 92),
            inversePrimary = hsl(h, CHROMA_PRIMARY, 40),
            secondary = hsl(hSecondary, CHROMA_SECONDARY, 80),
            onSecondary = hsl(hSecondary, CHROMA_SECONDARY, 16),
            secondaryContainer = hsl(hSecondary, CHROMA_SECONDARY, if (amoled) 22 else 28),
            onSecondaryContainer = hsl(hSecondary, CHROMA_SECONDARY - 4, 90),
            tertiary = hsl(hTertiary, CHROMA_TERTIARY, 80),
            onTertiary = hsl(hTertiary, CHROMA_TERTIARY, 16),
            tertiaryContainer = hsl(hTertiary, CHROMA_TERTIARY, if (amoled) 22 else 28),
            onTertiaryContainer = hsl(hTertiary, CHROMA_TERTIARY - 6, 90),
            error = hsl(0, 45, 80),
            onError = hsl(0, 45, 16),
            errorContainer = hsl(0, 30, 25),
            onErrorContainer = hsl(0, 25, 88),
            background = hsl(h, CHROMA_NEUTRAL, bg),
            onBackground = hsl(h, CHROMA_NEUTRAL_VARIANT, 90),
            surface = hsl(h, CHROMA_NEUTRAL, bg),
            onSurface = hsl(h, CHROMA_NEUTRAL_VARIANT, 90),
            surfaceContainerLowest = hsl(h, CHROMA_NEUTRAL, (bg - 2).coerceAtLeast(0)),
            surfaceContainerLow = hsl(h, CHROMA_NEUTRAL, container),
            surfaceContainer = hsl(h, CHROMA_NEUTRAL, container),
            surfaceContainerHigh = hsl(h, CHROMA_NEUTRAL, containerHigh),
            surfaceContainerHighest = hsl(h, CHROMA_NEUTRAL, containerHighest),
            surfaceVariant = hsl(h, CHROMA_NEUTRAL_VARIANT, containerHigh + 4),
            onSurfaceVariant = hsl(h, CHROMA_NEUTRAL_VARIANT, 82),
            surfaceBright = hsl(h, CHROMA_NEUTRAL + 2, if (amoled) 14 else 24),
            surfaceDim = hsl(h, CHROMA_NEUTRAL, (bg - 2).coerceAtLeast(0)),
            surfaceTint = hsl(h, CHROMA_PRIMARY, 82),
            outline = hsl(h, CHROMA_NEUTRAL_VARIANT, 64),
            outlineVariant = hsl(h, CHROMA_NEUTRAL_VARIANT, 32),
            inverseSurface = hsl(h, CHROMA_NEUTRAL_VARIANT, 90),
            inverseOnSurface = hsl(h, CHROMA_NEUTRAL_VARIANT, 20),
            scrim = Color.Black,
        )
    }

    fun lightScheme(seed: Color): ColorScheme {
        val h = hueOf(seed)
        val hSecondary = (h + 20) % 360
        val hTertiary = (h + 60) % 360
        val cP = CHROMA_PRIMARY + 15
        val cS = CHROMA_SECONDARY + 8
        val cT = CHROMA_TERTIARY + 12
        return lightColorScheme(
            primary = hsl(h, cP, 40),
            onPrimary = Color.White,
            primaryContainer = hsl(h, cP, 90),
            onPrimaryContainer = hsl(h, cP, 10),
            secondary = hsl(hSecondary, cS, 40),
            onSecondary = Color.White,
            secondaryContainer = hsl(hSecondary, cS, 90),
            onSecondaryContainer = hsl(hSecondary, cS, 10),
            tertiary = hsl(hTertiary, cT, 40),
            onTertiary = Color.White,
            tertiaryContainer = hsl(hTertiary, cT, 90),
            onTertiaryContainer = hsl(hTertiary, cT, 10),
            error = hsl(0, 70, 40),
            onError = Color.White,
            errorContainer = hsl(0, 70, 90),
            onErrorContainer = hsl(0, 70, 12),
            background = hsl(h, 4, 98),
            onBackground = hsl(h, 6, 10),
            surface = hsl(h, 4, 98),
            onSurface = hsl(h, 6, 10),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 5, 96),
            surfaceContainer = hsl(h, 5, 93),
            surfaceContainerHigh = hsl(h, 5, 89),
            surfaceContainerHighest = hsl(h, 5, 85),
            surfaceVariant = hsl(h, 6, 90),
            onSurfaceVariant = hsl(h, 6, 30),
            outline = hsl(h, 6, 50),
            outlineVariant = hsl(h, 6, 80),
            inverseSurface = hsl(h, 6, 20),
            inverseOnSurface = hsl(h, 6, 95),
            inversePrimary = hsl(h, cP, 80),
            scrim = Color.Black,
        )
    }

    fun hueDelta(a: Int, b: Int): Int = abs(a - b).let { minOf(it, 360 - it) }
}

/** Rounded, optimistic type scale: bold headlines, calm body, wide-tracked labels. */
val KatTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.4).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
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
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

/** Expressive corner scale, matching the app's card and dock shapes. */
// Material's Shapes container is typed to CornerBasedShape, so stock components
// keep the rounded-corner tokens; the expressive silhouette is applied by the
// app's own cards, buttons and sheets.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(17.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

val HeroShape = SmoothCornerShape(32.dp, 0.6f)
val DockShape = SmoothCornerShape(30.dp, 0.6f)
val CardShape = SmoothCornerShape(26.dp, 0.55f)
val ArtworkShape = SmoothCornerShape(22.dp, 0.55f)
val PillShape = RoundedCornerShape(percent = 50)

@Composable
fun KatMusicTheme(
    seed: Color = Color(0xFF8AB4F8),
    amoled: Boolean = false,
    light: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = !light
    val context = LocalContext.current
    val dynamic = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrNull()
    } else null
    val scheme = dynamic?.let { base ->
        if (isDark && amoled) base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black
        ) else base
    } ?: if (isDark) ColorEngine.darkScheme(seed, amoled) else ColorEngine.lightScheme(seed)
    MaterialTheme(
        colorScheme = scheme,
        typography = KatTypography,
        shapes = AppShapes
    ) {
        CompositionLocalProvider(LocalIsDarkTheme provides isDark, content = content)
    }
}

@Composable
fun isDark(): Boolean = isSystemInDarkTheme()
