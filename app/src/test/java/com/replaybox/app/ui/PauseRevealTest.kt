package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A pause reveals the player controls (grilling 2026-11-10, Q1). */
class PauseRevealTest {

    private fun shows(
        playWhenReady: Boolean = false,
        endOfMedia: Boolean = false,
        resumePrompt: Boolean = false,
        nextUp: Boolean = false,
        optionsOpen: Boolean = false,
    ) = PauseReveal.shows(playWhenReady, endOfMedia, resumePrompt, nextUp, optionsOpen)

    @Test
    fun `a pause with nothing else on screen shows the controls`() {
        assertThat(shows()).isTrue()
    }

    @Test
    fun `resuming never shows them`() {
        assertThat(shows(playWhenReady = true)).isFalse()
    }

    @Test
    fun `the end of the Episode is not a pause`() {
        assertThat(shows(endOfMedia = true)).isFalse()
    }

    @Test
    fun `the Reprendre prompt keeps the screen`() {
        assertThat(shows(resumePrompt = true)).isFalse()
    }

    @Test
    fun `the Episode suivant panel keeps the screen`() {
        assertThat(shows(nextUp = true)).isFalse()
    }

    @Test
    fun `an open Options panel stays open`() {
        assertThat(shows(optionsOpen = true)).isFalse()
    }
}
