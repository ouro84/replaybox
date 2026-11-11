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
            assertThat((m.width - RailMetrics.INSET * 2).value * s).isAtLeast(RailMetrics.MIN_TOUCH_DP)
        }
    }

    @Test
    fun tabletItemIsAtLeast48Dp() {
        val m = RailMetrics.of(mobile = true, heightDp = 800)
        assertThat(m.item.value).isAtLeast(RailMetrics.MIN_TOUCH_DP)
        assertThat((m.width - RailMetrics.INSET * 2).value).isAtLeast(RailMetrics.MIN_TOUCH_DP)
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
        for (m in mobileCases) assertThat(m.itemsTop.value).isAtLeast((m.item + RailMetrics.GAP).value)
    }

    @Test
    fun destinationsNeverMove() {
        // The back slot (one item + the gap) comes out of the top padding: Accueil stays put.
        for (m in mobileCases) {
            assertThat(m.topPadding(showBack = true) + m.item + RailMetrics.GAP).isEqualTo(m.topPadding(showBack = false))
            assertThat(m.topPadding(showBack = true)).isAtLeast(0.dp)
        }
        assertThat(RailMetrics.Tv.topPadding(showBack = false)).isEqualTo(72.dp)
    }

    @Test
    fun boxGeometryConstants() {
        assertThat(RailMetrics.GAP).isEqualTo(8.dp)
        assertThat(RailMetrics.INSET).isEqualTo(12.dp)
        assertThat(RailMetrics.Tv.width).isEqualTo(RailMetrics.Tv.item + RailMetrics.INSET * 2)
    }

    @Test
    fun railFitsTheCanvas() {
        val inset = MobileInsets.vertical.value
        fun stack(m: RailMetrics) = inset + m.itemsTop.value + 4 * m.item.value + 3 * RailMetrics.GAP.value
        // Phones draw on the compact canvas; its 540 dp height is the design floor.
        for (h in compactHeights) {
            val m = RailMetrics.of(mobile = true, heightDp = h)
            assertThat(stack(m)).isAtMost(CompactCanvas.HEIGHT_DP - inset)
        }
        // Tablets draw unscaled: pin the shortest tablet window (480 dp).
        val tabletFloor = LayoutClass.COMPACT_MAX_HEIGHT_DP
        assertThat(stack(RailMetrics.of(mobile = true, heightDp = tabletFloor))).isAtMost(tabletFloor - inset)
    }

    @Test
    fun iconSitsCentredInThePill() {
        assertThat(RailMetrics.Tv.iconStart).isEqualTo(12.dp)
        for (m in mobileCases) assertThat(m.iconStart * 2 + m.icon).isEqualTo(m.item)
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
