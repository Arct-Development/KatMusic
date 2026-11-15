package com.itzfallenme.katmusic.shared.settings

import com.itzfallenme.katmusic.data.local.AccentMode
import com.itzfallenme.katmusic.data.local.ThemeMode
import com.itzfallenme.katmusic.data.repository.ThemeUiState
import com.itzfallenme.katmusic.ui.theme.PaletteBuilder
import kotlinx.coroutines.flow.StateFlow

/** Appearance settings the shared UI edits on both platforms. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val useCustomFont: Boolean = true,
    val useBoldFont: Boolean = false,
    val accentMode: AccentMode = AccentMode.MANUAL,
    val accentColorHex: String = "#168BFF",
    /** KatMusic Signature (logo-derived) palette instead of a seeded one. */
    val signatureTheme: Boolean = true,
    val liquidGlass: Boolean = true,
    val md3NavBar: Boolean = false,
    val hideNavTabs: Boolean = false,
    val volume: Float = 1f,
)

/**
 * Maps appearance settings onto the exact ThemeUiState the theme consumes.
 * One implementation, used by desktop today and by Android's ThemeRepository
 * for the settings the shared UI owns.
 */
fun AppSettings.toThemeUiState(): ThemeUiState {
    val darkScheme = when {
        signatureTheme -> PaletteBuilder.buildKatMusicDarkScheme(amoled, liquidGlass)
        accentMode == AccentMode.MONOCHROME -> PaletteBuilder.buildMonochromeDarkScheme(amoled, liquidGlass)
        else -> PaletteBuilder.buildDarkScheme(accentColorHex, amoled, liquidGlass)
    }
    val lightScheme = when {
        signatureTheme -> PaletteBuilder.buildKatMusicLightScheme(liquidGlass)
        accentMode == AccentMode.MONOCHROME -> PaletteBuilder.buildMonochromeLightScheme(liquidGlass)
        else -> PaletteBuilder.buildLightScheme(accentColorHex, liquidGlass)
    }
    return ThemeUiState(
        darkColorScheme = darkScheme,
        lightColorScheme = lightScheme,
        themeMode = themeMode,
        colorScheme = if (themeMode == ThemeMode.LIGHT) lightScheme else darkScheme,
        amoled = amoled,
        mode = accentMode,
        accentColorHex = accentColorHex,
        useCustomFont = useCustomFont,
        useBoldFont = useBoldFont,
        liquidGlass = liquidGlass,
        md3NavBar = md3NavBar,
        hideNavTabs = hideNavTabs,
        isKatMusicTheme = signatureTheme,
    )
}

/**
 * Persisted appearance state. Each platform supplies the storage (DataStore on
 * Android, java.util.prefs on desktop); the settings themselves and the theme
 * mapping above are shared.
 */
interface SettingsStore {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}
