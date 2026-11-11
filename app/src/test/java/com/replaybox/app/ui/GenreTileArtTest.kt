package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Genre tile cover: crop per image shape, and the name always on 92 % ground or more. */
class GenreTileArtTest {

    /** The tile in dp: 112 tall, the name 16 dp off the bottom, scrim lead 8 dp and ramp 24 dp. */
    private val height = 112f
    private val lead = 8f
    private val ramp = 24f

    /** Ground alpha the gradient draws at [y], interpolating linearly between stops. */
    private fun alphaAt(stops: List<Pair<Float, Float>>, y: Float): Float {
        val f = y / height
        val after = stops.indexOfFirst { it.first >= f }
        if (after <= 0) return stops.first().second
        val (f0, a0) = stops[after - 1]
        val (f1, a1) = stops[after]
        return if (f1 == f0) a1 else a0 + (a1 - a0) * (f - f0) / (f1 - f0)
    }

    @Test
    fun backdropStaysCentredAndPosterIsHeldHigh() {
        assertThat(GenreTileArt.verticalBias(1920f, 1080f)).isEqualTo(0f)
        assertThat(GenreTileArt.verticalBias(600f, 800f)).isEqualTo(GenreTileArt.POSTER_BIAS)
        assertThat(GenreTileArt.verticalBias(800f, 800f)).isEqualTo(0f)
        assertThat(GenreTileArt.verticalBias(0f, 0f)).isEqualTo(0f)
    }

    @Test
    fun oneLineNameSitsOnTheFloorAndTheTopStaysClear() {
        val nameTop = height - 16f - 26f
        val stops = GenreTileArt.scrimStops(height, nameTop, lead, ramp)
        assertThat(stops.first()).isEqualTo(0f to 0f)
        assertThat(stops.last()).isEqualTo(1f to 1f)
        for (y in nameTop.toInt()..height.toInt()) {
            assertThat(alphaAt(stops, y.toFloat())).isAtLeast(GenreTileArt.NAME_FLOOR)
        }
        assertThat(alphaAt(stops, 20f)).isLessThan(GenreTileArt.SOFT)
    }

    @Test
    fun twoLineNameStillSitsOnTheFloor() {
        val nameTop = height - 16f - 52f
        val stops = GenreTileArt.scrimStops(height, nameTop, lead, ramp)
        for (y in nameTop.toInt()..height.toInt()) {
            assertThat(alphaAt(stops, y.toFloat())).isAtLeast(GenreTileArt.NAME_FLOOR)
        }
        assertThat(stops.map { it.first }).isInOrder()
    }

    @Test
    fun nameReachingTheTopPutsTheFloorEverywhere() {
        val stops = GenreTileArt.scrimStops(height, nameTop = 4f, lead = lead, ramp = ramp)
        assertThat(stops).containsExactly(0f to GenreTileArt.NAME_FLOOR, 1f to 1f).inOrder()
        assertThat(GenreTileArt.scrimStops(0f, 0f, lead, ramp).first().second).isEqualTo(GenreTileArt.NAME_FLOOR)
    }

    @Test
    fun noScrimUntilTheCoverHasLoaded() {
        // A pending or failed cover leaves the plain text tile, whatever the fade reads.
        assertThat(GenreTileArt.scrimAlpha(loaded = false, fromMemory = false, fade = 0.5f)).isNull()
        assertThat(GenreTileArt.scrimAlpha(loaded = false, fromMemory = true, fade = 1f)).isNull()
    }

    @Test
    fun aMemoryHitShowsTheFullScrimAtOnce() {
        // Coil does not crossfade a memory hit, so the scrim must not fade in under visible art.
        assertThat(GenreTileArt.scrimAlpha(loaded = true, fromMemory = true, fade = 0f)).isEqualTo(1f)
    }

    @Test
    fun aDiskOrNetworkLoadFadesTheScrimInWithTheArt() {
        assertThat(GenreTileArt.scrimAlpha(loaded = true, fromMemory = false, fade = 0.4f)).isEqualTo(0.4f)
    }
}
