package com.itzfallenme.katmusic.shared.settings

import android.content.Context
import android.content.SharedPreferences
import com.itzfallenme.katmusic.data.local.AccentMode
import com.itzfallenme.katmusic.data.local.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android persistence for the shared [AppSettings]. SharedPreferences stores
 * the appearance and volume settings owned by the shared UI.
 */
class AndroidSettingsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().apply {
            putString(KEY_THEME_MODE, next.themeMode.name)
            putBoolean(KEY_AMOLED, next.amoled)
            putBoolean(KEY_CUSTOM_FONT, next.useCustomFont)
            putBoolean(KEY_BOLD_FONT, next.useBoldFont)
            putString(KEY_ACCENT_MODE, next.accentMode.name)
            putString(KEY_ACCENT_COLOR, next.accentColorHex)
            putBoolean(KEY_SIGNATURE, next.signatureTheme)
            putBoolean(KEY_LIQUID_GLASS, next.liquidGlass)
            putBoolean(KEY_MD3_NAV, next.md3NavBar)
            putBoolean(KEY_HIDE_NAV_TABS, next.hideNavTabs)
            putFloat(KEY_VOLUME, next.volume)
        }.apply()
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = prefs.getString(KEY_THEME_MODE, null)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            amoled = prefs.getBoolean(KEY_AMOLED, defaults.amoled),
            useCustomFont = prefs.getBoolean(KEY_CUSTOM_FONT, defaults.useCustomFont),
            useBoldFont = prefs.getBoolean(KEY_BOLD_FONT, defaults.useBoldFont),
            accentMode = prefs.getString(KEY_ACCENT_MODE, null)
                ?.let { runCatching { AccentMode.valueOf(it) }.getOrNull() }
                ?: defaults.accentMode,
            accentColorHex = prefs.getString(KEY_ACCENT_COLOR, defaults.accentColorHex)
                ?: defaults.accentColorHex,
            signatureTheme = prefs.getBoolean(KEY_SIGNATURE, defaults.signatureTheme),
            liquidGlass = prefs.getBoolean(KEY_LIQUID_GLASS, defaults.liquidGlass),
            md3NavBar = prefs.getBoolean(KEY_MD3_NAV, defaults.md3NavBar),
            hideNavTabs = prefs.getBoolean(KEY_HIDE_NAV_TABS, defaults.hideNavTabs),
            volume = prefs.getFloat(KEY_VOLUME, defaults.volume),
        )
    }

    private companion object {
        const val FILE_NAME = "katmusic_shared_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AMOLED = "amoled"
        const val KEY_CUSTOM_FONT = "custom_font"
        const val KEY_BOLD_FONT = "bold_font"
        const val KEY_ACCENT_MODE = "accent_mode"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_SIGNATURE = "signature_theme"
        const val KEY_LIQUID_GLASS = "liquid_glass"
        const val KEY_MD3_NAV = "md3_nav_bar"
        const val KEY_HIDE_NAV_TABS = "hide_nav_tabs"
        const val KEY_VOLUME = "volume"
    }
}