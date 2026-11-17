package com.itzfallenme.katmusic.shared.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun ApplySystemBars(isDark: Boolean) {
    // A desktop window owns its own decorations; there are no inset-styled
    // status/navigation bars to tint.
}

@Composable
actual fun rememberSystemDynamicScheme(isDark: Boolean, amoled: Boolean): ColorScheme? {
    // Desktop has no wallpaper-derived system palette (this is Android's
    // Monet). Returning null routes the theme to the KatMusic palettes.
    return null
}

actual fun platformDisplayName(): String {
    val os = System.getProperty("os.name").orEmpty()
    val version = System.getProperty("os.version").orEmpty()
    val arch = System.getProperty("os.arch").orEmpty()
    return listOf(os, version, arch).filter { it.isNotBlank() }.joinToString(" ")
}
