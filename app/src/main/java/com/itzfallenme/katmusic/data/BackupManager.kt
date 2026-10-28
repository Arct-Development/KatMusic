package com.itzfallenme.katmusic.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One file that carries the whole user state of the app. */
@Serializable
data class BackupBundle(
    val app: String = "KatMusic",
    val format: Int = 1,
    val createdAt: Long = 0L,
    val library: LibraryData = LibraryData(),
    val settings: AppSettings = AppSettings()
)

sealed interface RestoreResult {
    data class Success(
        val likes: Int,
        val playlists: Int,
        val history: Int,
        val settingsRestored: Boolean
    ) : RestoreResult

    data class Failure(val message: String) : RestoreResult
}

/**
 * Writes and reads the library + settings as a single JSON document so users can move
 * their likes, playlists, history and preferences between devices.
 */
class BackupManager(
    private val library: LibraryRepository,
    private val settings: SettingsRepository
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun export(): String = json.encodeToString(
        BackupBundle.serializer(),
        BackupBundle(
            createdAt = System.currentTimeMillis(),
            library = library.state.value,
            settings = settings.state.value
        )
    )

    fun fileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "katmusic-backup-$stamp.json"
    }

    fun restore(text: String): RestoreResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return RestoreResult.Failure("That file is empty")

        val root = runCatching {
            json.parseToJsonElement(trimmed) as? JsonObject
        }.getOrNull() ?: return RestoreResult.Failure("That file is not a KatMusic backup")

        // Accept a full bundle, a bare library export or a bare settings export so older
        // or hand-edited files still import.
        val bundle = runCatching { json.decodeFromString(BackupBundle.serializer(), trimmed) }
            .getOrNull()
        if (bundle != null) {
            library.replaceAll(bundle.library)
            settings.set(bundle.settings)
            return RestoreResult.Success(
                likes = bundle.library.liked.size,
                playlists = bundle.library.playlists.size,
                history = bundle.library.history.size,
                settingsRestored = true
            )
        }

        if (root.keys.contains("liked")) {
            val data = runCatching { json.decodeFromString(LibraryData.serializer(), trimmed) }
                .getOrNull() ?: return RestoreResult.Failure("The library data is damaged")
            library.replaceAll(data)
            return RestoreResult.Success(
                likes = data.liked.size,
                playlists = data.playlists.size,
                history = data.history.size,
                settingsRestored = false
            )
        }

        if (root.keys.contains("seedColor") || root.keys.contains("highQualityStream")) {
            val appSettings = runCatching { json.decodeFromString(AppSettings.serializer(), trimmed) }
                .getOrNull() ?: return RestoreResult.Failure("The settings data is damaged")
            settings.set(appSettings)
            val counts = library.state.value
            return RestoreResult.Success(
                likes = counts.liked.size,
                playlists = counts.playlists.size,
                history = counts.history.size,
                settingsRestored = true
            )
        }

        val appName = runCatching { root["app"]?.jsonPrimitive?.content }.getOrNull()
        return RestoreResult.Failure(
            if (appName != null) "This backup was made by $appName, not KatMusic"
            else "No KatMusic library or settings found in that file"
        )
    }
}
