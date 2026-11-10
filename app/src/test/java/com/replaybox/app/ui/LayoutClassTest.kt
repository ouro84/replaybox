package com.replaybox.app.ui

import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutClassTest {

    @Test
    fun tvIsAlwaysTv() {
        assertThat(LayoutClass.of(mobile = false, heightDp = 300)).isEqualTo(LayoutClass.TV)
        assertThat(LayoutClass.of(mobile = false, heightDp = 1000)).isEqualTo(LayoutClass.TV)
    }

    @Test
    fun mobileSplitsAt480() {
        assertThat(LayoutClass.of(mobile = true, heightDp = 479)).isEqualTo(LayoutClass.COMPACT)
        assertThat(LayoutClass.of(mobile = true, heightDp = 411)).isEqualTo(LayoutClass.COMPACT)
        assertThat(LayoutClass.of(mobile = true, heightDp = 480)).isEqualTo(LayoutClass.TABLET)
        assertThat(LayoutClass.of(mobile = true, heightDp = 617)).isEqualTo(LayoutClass.TABLET)
    }

    @Test
    fun compactScaleFitsTheTvCanvas() {
        assertThat(CompactCanvas.scale(411)).isWithin(1e-4f).of(411f / 540f)
        assertThat(CompactCanvas.scale(600)).isEqualTo(1f)
    }

    @Test
    fun compactTextIsNinetyPercentOfTv() {
        assertThat(CompactCanvas.fontScale(1f, 411) * CompactCanvas.scale(411)).isWithin(1e-4f).of(0.9f)
        // The viewer's own font size still applies on top.
        assertThat(CompactCanvas.fontScale(1.3f, 411) * CompactCanvas.scale(411)).isWithin(1e-4f).of(1.17f)
    }

    @Test
    fun textBoxesGrowWithTheirText() {
        // Text is 0.9 of TV size on a 0.76 canvas: a fixed text box must grow by the same ratio.
        assertThat(CompactCanvas.textRatio(411)).isWithin(1e-4f).of(0.9f / (411f / 540f))
        assertThat(CompactCanvas.textRatio(600)).isWithin(1e-4f).of(0.9f)
    }

    @Test
    fun onlyCompactChangesTheDensity() {
        // Same instance outside compact: the box and tablets get the system density untouched,
        // and the class can flip without swapping the composition tree (one provider for all).
        val system = Density(density = 2.625f, fontScale = 1f)
        assertThat(CompactCanvas.density(system, mobile = false, heightDp = 300)).isSameInstanceAs(system)
        assertThat(CompactCanvas.density(system, mobile = true, heightDp = 617)).isSameInstanceAs(system)
        val compact = CompactCanvas.density(system, mobile = true, heightDp = 411)
        assertThat(compact.density).isWithin(1e-4f).of(2.625f * 411f / 540f)
        assertThat(compact.fontScale).isWithin(1e-4f).of(CompactCanvas.fontScale(1f, 411))
    }

    @Test
    fun textRatioIsOneOutsideCompact() {
        assertThat(CompactCanvas.textRatio(mobile = false, heightDp = 300)).isEqualTo(1f)
        assertThat(CompactCanvas.textRatio(mobile = true, heightDp = 617)).isEqualTo(1f)
        assertThat(CompactCanvas.textRatio(mobile = true, heightDp = 411)).isWithin(1e-4f).of(CompactCanvas.textRatio(411))
    }
}
