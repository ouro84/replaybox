package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The mobile timeline (B4): where a touch lands on the bar, and where the thumb sits. */
class TimelineScrubTest {

    @Test
    fun `x maps linearly to the position`() {
        assertThat(TimelineScrub.positionAt(50f, 200f, 3_600_000)).isEqualTo(900_000L)
        assertThat(TimelineScrub.positionAt(0f, 200f, 3_600_000)).isEqualTo(0L)
        assertThat(TimelineScrub.positionAt(200f, 200f, 3_600_000)).isEqualTo(3_600_000L)
    }

    @Test
    fun `x outside the bar clamps`() {
        assertThat(TimelineScrub.positionAt(-30f, 200f, 60_000)).isEqualTo(0L)
        assertThat(TimelineScrub.positionAt(260f, 200f, 60_000)).isEqualTo(60_000L)
    }

    @Test
    fun `unknown duration maps to nothing`() {
        assertThat(TimelineScrub.positionAt(50f, 200f, 0)).isNull()
        assertThat(TimelineScrub.positionAt(50f, 200f, -1)).isNull()
        assertThat(TimelineScrub.positionAt(50f, 0f, 60_000)).isNull()
    }

    @Test
    fun `the thumb follows the position, clamped`() {
        assertThat(TimelineScrub.xOf(30_000, 60_000, 200f)).isEqualTo(100f)
        assertThat(TimelineScrub.xOf(90_000, 60_000, 200f)).isEqualTo(200f)
        assertThat(TimelineScrub.xOf(-5, 60_000, 200f)).isEqualTo(0f)
        assertThat(TimelineScrub.xOf(30_000, 0, 200f)).isEqualTo(0f)
    }

    @Test
    fun `the touch area absorbs the gaps it covers`() {
        // Pixel 7 canvas: a 68 dp touch area around a 28 dp clock line leaves 20 dp each side,
        // more than the 14 dp and 16 dp box gaps: nothing left to add.
        assertThat(TimelineScrub.gapOutside(gap = 14f, touch = 68f, content = 28f)).isEqualTo(0f)
        assertThat(TimelineScrub.gapOutside(gap = 16f, touch = 68f, content = 28f)).isEqualTo(0f)
        // Tablet: 48 dp around the 22 dp line leaves 13 dp; the rest of each box gap stays.
        assertThat(TimelineScrub.gapOutside(gap = 14f, touch = 48f, content = 22f)).isEqualTo(1f)
        assertThat(TimelineScrub.gapOutside(gap = 16f, touch = 48f, content = 22f)).isEqualTo(3f)
    }

    @Test
    fun `x and position round-trip`() {
        val position = TimelineScrub.positionAt(73f, 200f, 60_000)!!
        assertThat(TimelineScrub.xOf(position, 60_000, 200f)).isWithin(0.01f).of(73f)
    }
}
