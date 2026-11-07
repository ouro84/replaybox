package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Épisode suivant shows over the last 10 s while the video plays on. */
class NextUpWindowTest {

    private val duration = 2_700_000L

    @Test
    fun `hidden before the last ten seconds`() {
        assertThat(NextUpWindow.visible(duration - 10_001, duration, cancelled = false, hasNext = true)).isFalse()
    }

    @Test
    fun `shown from duration minus ten seconds to the end`() {
        assertThat(NextUpWindow.visible(duration - 10_000, duration, cancelled = false, hasNext = true)).isTrue()
        assertThat(NextUpWindow.visible(duration, duration, cancelled = false, hasNext = true)).isTrue()
    }

    @Test
    fun `never without a next Episode, once cancelled, or with an unknown duration`() {
        assertThat(NextUpWindow.visible(duration - 5_000, duration, cancelled = false, hasNext = false)).isFalse()
        assertThat(NextUpWindow.visible(duration - 5_000, duration, cancelled = true, hasNext = true)).isFalse()
        assertThat(NextUpWindow.visible(0, 0, cancelled = false, hasNext = true)).isFalse()
    }

    @Test
    fun `a cancel holds inside the window and is forgotten after seeking back`() {
        assertThat(NextUpWindow.stillCancelled(duration - 4_000, duration, cancelled = true)).isTrue()
        assertThat(NextUpWindow.stillCancelled(duration - 60_000, duration, cancelled = true)).isFalse()
        assertThat(NextUpWindow.stillCancelled(duration - 4_000, duration, cancelled = false)).isFalse()
    }

    @Test
    fun `a resume landing inside the window shows the panel at once`() {
        assertThat(NextUpWindow.visible(duration - 3_000, duration, cancelled = false, hasNext = true)).isTrue()
        assertThat(NextUpWindow.secondsLeft(duration - 3_000, duration)).isEqualTo(3)
    }

    @Test
    fun `countdown is the time left, rounded up, capped at ten`() {
        assertThat(NextUpWindow.secondsLeft(duration - 10_000, duration)).isEqualTo(10)
        assertThat(NextUpWindow.secondsLeft(duration - 9_200, duration)).isEqualTo(10)
        assertThat(NextUpWindow.secondsLeft(duration - 800, duration)).isEqualTo(1)
        assertThat(NextUpWindow.secondsLeft(duration, duration)).isEqualTo(0)
        assertThat(NextUpWindow.secondsLeft(0, duration)).isEqualTo(10)
    }
}
