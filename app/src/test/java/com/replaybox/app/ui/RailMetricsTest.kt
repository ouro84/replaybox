package com.replaybox.app.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RailMetricsTest {

    private val compactHeights = listOf(320, 360, 383, 411, 479)
    private val mobileCases = compactHeights.map { RailMetrics.of(mobile = true, heightDp = it) } +
        RailMetrics.of(mobile = true, heightDp = 800)

    @Test
    fun boxKeepsTodaysRail() {
        assertThat(RailMetrics.Tv).isEqualTo(RailMetrics(48.dp, 24.dp, 72.dp, 72.dp, 104.dp))
        assertThat(RailMetrics.of(mobile = false, heightDp = 540)).isEqualTo(RailMetrics.Tv)
        assertThat(RailMetrics.of(mobile = false, heightDp = 411)).isEqualTo(RailMetrics.Tv)
        assertThat(RailMetrics.of(mobile = false, heightDp = 0)).isEqualTo(RailMetrics.Tv)
    }

    @Test
    fun compactItemIsAtLeast48RealDp() {
        for (h in compactHeights) {
            val m = RailMetrics.of(mobile = true, heightDp = h)
            val s = CompactCanvas.scale(h)
            assertThat(m.item.value * s).isAtLeast(RailMetrics.MIN_TOUCH_DP)
            assertThat((m.width - 24.dp).value * s).isAtLeast(RailMetrics.MIN_TOUCH_DP)
        }
    }

    @Test
    fun tabletItemIsAtLeast48Dp() {
        val m = RailMetrics.of(mobile = true, heightDp = 800)
        assertThat(m.item.value).isAtLeast(48f)
        assertThat((m.width - 24.dp).value).isAtLeast(48f)
    }

    @Test
    fun contentClearsTheRail() {
        val cases = listOf(
            RailMetrics.Tv,
            RailMetrics.of(mobile = true, heightDp = 411),
            RailMetrics.of(mobile = true, heightDp = 320),
            RailMetrics.of(mobile = true, heightDp = 800),
        )
        for (m in cases) assertThat((m.contentStart - GUTTER).value).isAtLeast(m.width.value)
    }

    @Test
    fun backSlotFitsAboveTheItems() {
        for (m in mobileCases) assertThat(m.itemsTop.value).isAtLeast((m.item + 8.dp).value)
    }

    @Test
    fun railFitsTheCanvas() {
        for (h in compactHeights) {
            val m = RailMetrics.of(mobile = true, heightDp = h)
            assertThat(16f + m.itemsTop.value + 4 * m.item.value + 3 * 8f).isAtMost(540f - 16f)
        }
    }

    @Test
    fun pixel7Metrics() {
        // Full 1080 px height (411 dp).
        assertThat(RailMetrics.of(mobile = true, heightDp = 411))
            .isEqualTo(RailMetrics(item = 64.dp, icon = 32.dp, width = 88.dp, itemsTop = 72.dp, contentStart = 120.dp))
        // What the Pixel 7 really reports in landscape (screenHeightDp 383: the status bar is
        // subtracted even when hidden), measured 2026-11-11.
        assertThat(RailMetrics.of(mobile = true, heightDp = 383))
            .isEqualTo(RailMetrics(item = 72.dp, icon = 36.dp, width = 96.dp, itemsTop = 80.dp, contentStart = 128.dp))
    }
}
