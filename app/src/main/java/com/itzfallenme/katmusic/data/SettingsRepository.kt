package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val store = JsonStore(
        context = context,
        fileName = "katmusic-settings.json",
        serializer = AppSettings.serializer(),
        defaultValue = { AppSettings() }
    )

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        store.save(next)
    }

    fun set(settings: AppSettings) = update { settings }

    /** Forces the latest settings to disk, e.g. when the app is backgrounded. */
    fun flush() = store.flush()
}
