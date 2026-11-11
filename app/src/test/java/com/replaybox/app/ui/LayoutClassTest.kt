package com.replaybox.app.ui

import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.ceil

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

    @Test
    fun `a 48 dp real target on the Pixel 7 canvas`() {
        val canvas = CompactCanvas.canvasDp(48f, mobile = true, heightDp = 411)
        assertThat(canvas).isWithin(0.01f).of(63.07f)
        // Rounded up to whole canvas dp, it still lands on at least 48 real dp (64 x 0.7611 = 48.7).
        assertThat(ceil(canvas) * CompactCanvas.scale(411)).isAtLeast(48f)
        // The Pixel 7 actually reports 383 dp in landscape (the hidden status bar is still subtracted).
        assertThat(ceil(CompactCanvas.canvasDp(48f, mobile = true, heightDp = 383)) * CompactCanvas.scale(383)).isAtLeast(48f)
    }

    @Test
    fun `no scaling off the compact canvas`() {
        assertThat(CompactCanvas.canvasDp(48f, mobile = false, heightDp = 540)).isEqualTo(48f)
        assertThat(CompactCanvas.canvasDp(48f, mobile = true, heightDp = 800)).isEqualTo(48f)
    }

    @Test
    fun undefinedHeightIsNeverCompact() {
        // Configuration.SCREEN_HEIGHT_DP_UNDEFINED is 0: scaling by it would give a zero density.
        assertThat(LayoutClass.of(mobile = true, heightDp = 0)).isEqualTo(LayoutClass.TABLET)
        val system = Density(density = 2.625f, fontScale = 1f)
        assertThat(CompactCanvas.density(system, mobile = true, heightDp = 0)).isSameInstanceAs(system)
    }
}
