package com.itzfallenme.katmusic.data.local

/** How accent colours are picked. Shared so both platforms read the same value. */
enum class AccentMode(val storageValue: String) {
    MANUAL("manual"),
    DYNAMIC("dynamic"),
    MONOCHROME("monochrome");

    companion object {
        fun fromStorage(value: String?): AccentMode =
            entries.firstOrNull { it.storageValue == value } ?: MANUAL
    }
}

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
