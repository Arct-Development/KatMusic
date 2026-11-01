package com.itzfallenme.katmusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itzfallenme.katmusic.data.local.AccentMode
import com.itzfallenme.katmusic.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    val uiState = themeRepository.uiState

    fun setManualAccent(color: Color) = viewModelScope.launch {
        themeRepository.setManualAccent(color)
    }

    fun setMode(mode: AccentMode) = viewModelScope.launch {
        themeRepository.setMode(mode)
    }

    fun setAmoled(enabled: Boolean) = viewModelScope.launch {
        themeRepository.setAmoled(enabled)
    }

    fun setThemeMode(mode: com.itzfallenme.katmusic.data.local.ThemeMode) = viewModelScope.launch {
        themeRepository.setThemeMode(mode)
    }

    fun setKatMusicTheme(enabled: Boolean) = viewModelScope.launch {
        themeRepository.setKatMusicTheme(enabled)
    }

    fun setLegacyFriendlyMode(enabled: Boolean) = viewModelScope.launch {
        themeRepository.setLiquidGlass(!enabled)
        themeRepository.setMd3NavBar(enabled)
    }

    fun setLiquidGlass(enabled: Boolean) = viewModelScope.launch {
        themeRepository.setLiquidGlass(enabled)
    }

    fun setMd3NavBar(enabled: Boolean) = viewModelScope.launch {
        themeRepository.setMd3NavBar(enabled)
    }
}
