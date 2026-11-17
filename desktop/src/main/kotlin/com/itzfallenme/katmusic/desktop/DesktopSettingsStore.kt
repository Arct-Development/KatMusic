package com.itzfallenme.katmusic.desktop

import com.itzfallenme.katmusic.data.local.AccentMode
import com.itzfallenme.katmusic.data.local.ThemeMode
import com.itzfallenme.katmusic.shared.settings.AppSettings
import com.itzfallenme.katmusic.shared.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * Desktop settings storage. Android persists the same [AppSettings] through
 * DataStore; here the platform store is java.util.prefs, so the settings
 * themselves and the theme mapping stay shared.
 */
class DesktopSettingsStore : SettingsStore {

    private val prefs: Preferences = Preferences.userRoot().node("com/itzfallenme/katmusic")
    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        persist(next)
    }

    fun equalizerEnabled(): Boolean = prefs.getBoolean("equalizerEnabled", false)

    fun equalizerGains(): FloatArray = prefs.get("equalizerGains", "")
        .split(',')
        .mapNotNull { it.trim().toFloatOrNull() }
        .let { gains ->
            FloatArray(EQUALIZER_BAND_COUNT) { index -> gains.getOrElse(index) { 0f } }
        }

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.putBoolean("equalizerEnabled", enabled)
        runCatching { prefs.flush() }
    }

    fun setEqualizerGains(gains: FloatArray) {
        prefs.put("equalizerGains", gains.joinToString(","))
        runCatching { prefs.flush() }
    }

    /** Folders the user added to the library, in the order they were added. */
    fun libraryFolders(): List<String> =
        prefs.get("libraryFolders", "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun addLibraryFolder(path: String) {
        val folders = (libraryFolders() + path).distinct()
        prefs.put("libraryFolders", folders.joinToString("\n"))
        runCatching { prefs.flush() }
    }

    private companion object {
        const val EQUALIZER_BAND_COUNT = 10
    }

    private fun load(): AppSettings = AppSettings(
        themeMode = ThemeMode.fromStorage(prefs.get("themeMode", ThemeMode.SYSTEM.storageValue)),
        amoled = prefs.getBoolean("amoled", false),
        useCustomFont = prefs.getBoolean("useCustomFont", true),
        useBoldFont = prefs.getBoolean("useBoldFont", false),
        accentMode = AccentMode.fromStorage(prefs.get("accentMode", AccentMode.MANUAL.storageValue)),
        accentColorHex = prefs.get("accentColorHex", "#168BFF"),
        signatureTheme = prefs.getBoolean("signatureTheme", true),
        liquidGlass = prefs.getBoolean("liquidGlass", true),
        md3NavBar = prefs.getBoolean("md3NavBar", false),
        hideNavTabs = prefs.getBoolean("hideNavTabs", false),
    )

    private fun persist(settings: AppSettings) {
        prefs.put("themeMode", settings.themeMode.storageValue)
        prefs.putBoolean("amoled", settings.amoled)
        prefs.putBoolean("useCustomFont", settings.useCustomFont)
        prefs.putBoolean("useBoldFont", settings.useBoldFont)
        prefs.put("accentMode", settings.accentMode.storageValue)
        prefs.put("accentColorHex", settings.accentColorHex)
        prefs.putBoolean("signatureTheme", settings.signatureTheme)
        prefs.putBoolean("liquidGlass", settings.liquidGlass)
        prefs.putBoolean("md3NavBar", settings.md3NavBar)
        prefs.putBoolean("hideNavTabs", settings.hideNavTabs)
        runCatching { prefs.flush() }
    }
}
