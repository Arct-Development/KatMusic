package com.itzfallenme.katmusic.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<Int> = emptyList(),
    val levels: List<Float> = emptyList(),
    val presetNames: List<String> = emptyList(),
    val preset: String = "Flat",
    val bassBoost: Int = 0,
    val virtualizer: Int = 0
)

/** Wraps the platform equalizer effects for the current audio session. */
class EqualizerManager {

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var sessionId: Int = 0

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bass = BassBoost(0, audioSessionId)
            virtualizer = Virtualizer(0, audioSessionId)
        }
        val eq = equalizer ?: return
        val bands = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 }
        val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        val current = eq.currentPreset
        val levels = (0 until eq.numberOfBands).map {
            val band = eq.getBandLevel(it.toShort())
            val range = eq.bandLevelRange
            val normalized = (band - range[0]).toFloat() / (range[1] - range[0]).toFloat()
            normalized * 24f - 12f
        }
        _state.value = EqState(
            available = true,
            enabled = eq.enabled,
            bands = bands,
            levels = levels,
            presetNames = presets,
            preset = if (current == 0.toShort()) "Flat" else presets.getOrElse(current.toInt()) { "Custom" }
        )
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(index.toShort())
            val levels = (0 until eq.numberOfBands).map {
                val band = eq.getBandLevel(it.toShort())
                val range = eq.bandLevelRange
                ((band - range[0]).toFloat() / (range[1] - range[0]).toFloat()) * 24f - 12f
            }
            _state.value = _state.value.copy(
                preset = eq.getPresetName(index.toShort()),
                levels = levels
            )
        }
    }

    fun setBandLevel(band: Int, decibels: Float) {
        val eq = equalizer ?: return
        runCatching {
            val range = eq.bandLevelRange
            val value = (decibels.coerceIn(-12f, 12f) / 24f * (range[1] - range[0]).toFloat() +
                range[0])
            eq.setBandLevel(band.toShort(), value.toInt().toShort())
            _state.value = _state.value.copy(
                preset = "Custom",
                levels = _state.value.levels.toMutableList().apply { set(band, decibels) }
            )
        }
    }

    fun setBassBoost(strength: Int) {
        bass?.setStrength(strength.toShort())
        bass?.enabled = strength > 0
        _state.value = _state.value.copy(bassBoost = strength)
    }

    fun setVirtualizer(strength: Int) {
        virtualizer?.setStrength(strength.toShort())
        virtualizer?.enabled = strength > 0
        _state.value = _state.value.copy(virtualizer = strength)
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bass = null
        virtualizer = null
        sessionId = 0
        _state.value = EqState()
    }
}
