package com.itzfallenme.katmusic.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KatMusicThemeTest {

    @Test
    fun katMusicDarkScheme_usesBrandColors() {
        val scheme = PaletteBuilder.buildKatMusicDarkScheme(amoled = false)

        assertThat(scheme.primary).isEqualTo(Color(0xFF168BFF))
        assertThat(scheme.onPrimary).isEqualTo(Color(0xFF001A35))
        assertThat(scheme.surface).isEqualTo(Color(0xFF081426))
        assertThat(scheme.background).isEqualTo(Color(0xFF050B18))
    }

    @Test
    fun katMusicDarkScheme_amoled_pureBlackSurfaces() {
        val scheme = PaletteBuilder.buildKatMusicDarkScheme(amoled = true)

        assertThat(scheme.primary).isEqualTo(Color(0xFF168BFF))
        assertThat(scheme.onPrimary).isEqualTo(Color(0xFF001A35))
        assertThat(scheme.background).isEqualTo(Color.Black)
        assertThat(scheme.surface).isEqualTo(Color.Black)
        assertThat(scheme.surfaceContainerLowest).isEqualTo(Color.Black)
        assertThat(scheme.surfaceContainerLow).isEqualTo(Color.Black)
    }

    @Test
    fun katMusicLightScheme_usesBrandHighlights() {
        val scheme = PaletteBuilder.buildKatMusicLightScheme()

        assertThat(scheme.onPrimaryContainer).isEqualTo(Color(0xFF001D3D))
        assertThat(scheme.inversePrimary).isEqualTo(Color(0xFF8DBEFF))
        assertThat(scheme.inverseSurface).isEqualTo(Color(0xFF071426))
    }
}
