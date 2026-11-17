package com.itzfallenme.katmusic.data.repository

import androidx.compose.material3.ColorScheme
import com.itzfallenme.katmusic.data.local.AccentMode
import com.itzfallenme.katmusic.data.local.ThemeMode

/**
 * Everything the shared theme needs to render a screen. Lives in the shared
 * module so Android (whose ThemeRepository produces it) and the desktop app
 * (whose settings store produces it) describe appearance with one type.
 */
data class ThemeUiState(
    val darkColorScheme: ColorScheme,
    val lightColorScheme: ColorScheme,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorScheme: ColorScheme,
    val amoled: Boolean = false,
    val mode: AccentMode = AccentMode.MANUAL,
    /** Raw hex of the manually-picked accent (preset or custom), independent
     *  of [colorScheme] — colorScheme.primary is a generated M3 tone of this
     *  seed, not the seed itself, so it can't be compared back against a
     *  preset's hex to know which one is selected. This can. */
    val accentColorHex: String = "#168BFF",
    val useCustomFont: Boolean = true,
    val useBoldFont: Boolean = false,
    val liquidGlass: Boolean = true,
    val md3NavBar: Boolean = false,
    val hideNavTabs: Boolean = false,
    val isNowPlayingThemed: Boolean = false,
    val isKatMusicTheme: Boolean = false,
)
