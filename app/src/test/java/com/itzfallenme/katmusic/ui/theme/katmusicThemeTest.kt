package com.itzfallenme.katmusic.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KatMusicThemeTest {

    @Test
    fun katMusicDarkScheme_usesBrandColors() {
        val scheme = PaletteBuilder.buildKatMusicDarkScheme(amoled = false)

        assertThat(scheme.primary).isEqualTo(Color(0xFFC9FB00))
        assertThat(scheme.onPrimary).isEqualTo(Color(0xFF012226))
        assertThat(scheme.surface).isEqualTo(Color(0xFF012226))
        assertThat(scheme.background).isEqualTo(Color(0xFF01181B))
    }

    @Test
    fun katMusicDarkScheme_amoled_pureBlackSurfaces() {
        val scheme = PaletteBuilder.buildKatMusicDarkScheme(amoled = true)

        assertThat(scheme.primary).isEqualTo(Color(0xFFC9FB00))
        assertThat(scheme.onPrimary).isEqualTo(Color(0xFF012226))
        assertThat(scheme.background).isEqualTo(Color.Black)
        assertThat(scheme.surface).isEqualTo(Color.Black)
        assertThat(scheme.surfaceContainerLowest).isEqualTo(Color.Black)
        assertThat(scheme.surfaceContainerLow).isEqualTo(Color.Black)
    }

    @Test
    fun katMusicLightScheme_usesBrandHighlights() {
        val scheme = PaletteBuilder.buildKatMusicLightScheme()

        assertThat(scheme.onPrimaryContainer).isEqualTo(Color(0xFF012226))
        assertThat(scheme.inversePrimary).isEqualTo(Color(0xFFC9FB00))
        assertThat(scheme.inverseSurface).isEqualTo(Color(0xFF012226))
    }
}
