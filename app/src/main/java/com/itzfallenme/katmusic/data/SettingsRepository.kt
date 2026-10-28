package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val file = File(context.filesDir, "katmusic-settings.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings = runCatching {
        if (file.exists()) json.decodeFromString<AppSettings>(file.readText()) else AppSettings()
    }.getOrDefault(AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(AppSettings.serializer(), next))
            tmp.renameTo(file)
        }
    }

    fun set(settings: AppSettings) = update { settings }
}
