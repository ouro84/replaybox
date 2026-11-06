package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The resume prompt names the position like the player's own clock, never "0 min". */
class PlayheadTest {

    @Test
    fun `under an hour reads minutes and seconds`() {
        assertThat(Playhead.format(45_000)).isEqualTo("0:45")
        assertThat(Playhead.format(754_000)).isEqualTo("12:34")
    }

    @Test
    fun `an hour or more reads hours minutes and seconds`() {
        assertThat(Playhead.format(3_725_000)).isEqualTo("1:02:05")
    }
}
