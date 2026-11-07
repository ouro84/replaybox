package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubtitleStyleTest {

    @Test
    fun `default is large text on a semi-opaque band`() {
        val style = SubtitleStyle()
        assertThat(style.size).isEqualTo(SubtitleSize.LARGE)
        assertThat(style.background).isEqualTo(SubtitleBackground.SEMI)
    }

    @Test
    fun `sizes grow from the Media3 default`() {
        assertThat(SubtitleSize.NORMAL.fraction).isWithin(1e-4f).of(0.0533f)
        assertThat(SubtitleSize.LARGE.fraction).isGreaterThan(SubtitleSize.NORMAL.fraction)
        assertThat(SubtitleSize.XLARGE.fraction).isGreaterThan(SubtitleSize.LARGE.fraction)
    }

    @Test
    fun `outline has no band, the bands use the ground colour`() {
        assertThat(SubtitleStyle(background = SubtitleBackground.OUTLINE).backgroundArgb).isEqualTo(0)
        assertThat(SubtitleStyle(background = SubtitleBackground.OUTLINE).outlined).isTrue()
        val semi = SubtitleStyle(background = SubtitleBackground.SEMI)
        assertThat(semi.outlined).isFalse()
        assertThat(semi.backgroundArgb ushr 24).isEqualTo(0xB3)
        assertThat(semi.backgroundArgb and 0xFFFFFF).isEqualTo(0x0E1013)
        assertThat(SubtitleStyle(background = SubtitleBackground.OPAQUE).backgroundArgb ushr 24).isEqualTo(0xFF)
    }

    @Test
    fun `encode and decode round trip`() {
        for (size in SubtitleSize.values()) for (background in SubtitleBackground.values()) {
            val style = SubtitleStyle(size, background)
            assertThat(SubtitleStyle.decode(style.size.name, style.background.name)).isEqualTo(style)
        }
    }

    @Test
    fun `unknown or missing stored values fall back to the default`() {
        assertThat(SubtitleStyle.decode(null, null)).isEqualTo(SubtitleStyle())
        assertThat(SubtitleStyle.decode("HUGE", "NEON")).isEqualTo(SubtitleStyle())
        assertThat(SubtitleStyle.decode("NORMAL", null)).isEqualTo(SubtitleStyle(size = SubtitleSize.NORMAL))
    }
}
