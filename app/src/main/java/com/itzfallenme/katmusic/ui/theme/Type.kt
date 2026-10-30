@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.itzfallenme.katmusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.itzfallenme.katmusic.R

/**
 * Figtree, bundled as a real variable font (res/font/figtree.ttf) rather than
 * resolved by device font name, so every device renders the same face.
 *
 * The two type scales below are two readings of one typeface: [KatMusicDisplay]
 * leans on the heavier end of the weight axis for a confident, poster-like
 * feel, while [KatMusicText] walks down to the light end for a quieter,
 * editorial reading surface. Both keep Figtree's tall x-height, which is what
 * makes dense track lists stay legible at 13-15sp.
 *
 * Only the `wght` axis is used. Figtree exposes wght 200-800 and nothing else,
 * so asking for width or optical-size axes (which the previous face had) would
 * be silently ignored at best - the scale is built from weight alone and
 * derives presence from size, tracking and colour instead.
 */
private fun figtree(weight: Int, tracking: Float = 0f): FontFamily = FontFamily(
    Font(
        R.font.figtree,
        weight = when {
            weight >= 700 -> FontWeight.Bold
            weight >= 600 -> FontWeight.SemiBold
            weight >= 500 -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    ),
)

/** Heavier reading: headings and anything that has to hold a screen together. */
val KatMusicTypography = Typography(
    displayLarge = TextStyle(fontFamily = figtree(720), fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = figtree(700), fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.0).sp),
    displaySmall = TextStyle(fontFamily = figtree(680), fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = figtree(700), fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = figtree(660), fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = figtree(640), fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = figtree(640), fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = figtree(600), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = figtree(560), fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = figtree(440), fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = figtree(420), fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = figtree(400), fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontFamily = figtree(620), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = figtree(600), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = figtree(580), fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
)

/** Lighter reading: the quieter, editorial variant of the same face. */
val KatMusicThinTypography = Typography(
    displayLarge = TextStyle(fontFamily = figtree(300), fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1.6).sp),
    displayMedium = TextStyle(fontFamily = figtree(300), fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.2).sp),
    displaySmall = TextStyle(fontFamily = figtree(320), fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1.0).sp),
    headlineLarge = TextStyle(fontFamily = figtree(340), fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = figtree(330), fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = figtree(330), fontWeight = FontWeight.Normal, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = figtree(360), fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = figtree(420), fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = figtree(440), fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = figtree(360), fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = figtree(350), fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = figtree(340), fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = figtree(500), fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    labelMedium = TextStyle(fontFamily = figtree(480), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontFamily = figtree(460), fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
)

/**
 * The "Use Application Font" toggle's off-state: plain platform default,
 * with the same sizes/weights as [KatMusicTypography] so switching the toggle
 * changes only the typeface, not the layout metrics.
 */
val SystemTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 62.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)
