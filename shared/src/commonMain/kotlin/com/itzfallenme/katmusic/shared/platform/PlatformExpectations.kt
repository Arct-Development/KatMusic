package com.itzfallenme.katmusic.shared.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Platform hooks for things the theme genuinely cannot do in common code:
 * drawing into system bars (Android) and reading the wallpaper-derived
 * dynamic colour scheme (Android 12+). The desktop implementations are the
 * real behaviour of a desktop window: there are no system bars and no
 * wallpaper palette, so both are genuinely absent rather than stubbed.
 */
@Composable
expect fun ApplySystemBars(isDark: Boolean)

@Composable
expect fun rememberSystemDynamicScheme(isDark: Boolean, amoled: Boolean): ColorScheme?

/** Human-readable platform name, used by the shared About section. */
expect fun platformDisplayName(): String
