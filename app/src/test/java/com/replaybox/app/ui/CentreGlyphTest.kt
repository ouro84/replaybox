package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.app.ui.CentreGlyph.Kind
import org.junit.Test

/** Centre play/pause glyph over the video (UX grilling 2026-11-11, B2). */
class CentreGlyphTest {

    private fun of(
        playWhenReady: Boolean = true,
        callSilenced: Boolean = false,
        buffering: Boolean = false,
        ended: Boolean = false,
        resumePrompt: Boolean = false,
        nextUp: Boolean = false,
        optionsOpen: Boolean = false,
    ) = CentreGlyph.of(playWhenReady, callSilenced, buffering, ended, resumePrompt, nextUp, optionsOpen)

    @Test
    fun `paused shows the pause glyph`() {
        assertThat(of(playWhenReady = false)).isEqualTo(Kind.PAUSED)
    }

    @Test
    fun `a call holding playback silent reads paused`() {
        assertThat(of(playWhenReady = true, callSilenced = true)).isEqualTo(Kind.PAUSED)
    }

    @Test
    fun `playing shows nothing`() {
        assertThat(of()).isEqualTo(Kind.NONE)
    }

    @Test
    fun `the end, the resume prompt, Episode suivant and Options hide it`() {
        assertThat(of(playWhenReady = false, ended = true)).isEqualTo(Kind.NONE)
        assertThat(of(playWhenReady = false, resumePrompt = true)).isEqualTo(Kind.NONE)
        assertThat(of(playWhenReady = false, nextUp = true)).isEqualTo(Kind.NONE)
        assertThat(of(playWhenReady = false, optionsOpen = true)).isEqualTo(Kind.NONE)
    }

    @Test
    fun `buffering while playing shows the ring`() {
        assertThat(of(playWhenReady = true, buffering = true)).isEqualTo(Kind.LOADING)
        assertThat(of(playWhenReady = true, buffering = true, optionsOpen = true)).isEqualTo(Kind.LOADING)
        assertThat(of(playWhenReady = true, buffering = true, nextUp = true)).isEqualTo(Kind.LOADING)
        assertThat(of(playWhenReady = true, buffering = true, resumePrompt = true)).isEqualTo(Kind.NONE)
    }

    @Test
    fun `paused wins over buffering`() {
        assertThat(of(playWhenReady = false, buffering = true)).isEqualTo(Kind.PAUSED)
    }

    @Test
    fun `paused under Options or Episode suivant while buffering shows nothing`() {
        // A quality or audio change in Options while paused rebuffers: a paused viewer is not waiting.
        assertThat(of(playWhenReady = false, buffering = true, optionsOpen = true)).isEqualTo(Kind.NONE)
        assertThat(of(playWhenReady = false, buffering = true, nextUp = true)).isEqualTo(Kind.NONE)
        assertThat(of(playWhenReady = true, callSilenced = true, buffering = true, optionsOpen = true)).isEqualTo(Kind.NONE)
        assertThat(of(playWhenReady = false, buffering = true, ended = true)).isEqualTo(Kind.NONE)
    }

    @Test
    fun `resuming flashes play`() {
        assertThat(CentreGlyph.flashesPlay(Kind.PAUSED, Kind.NONE, playWhenReady = true)).isTrue()
        // Options opened while paused: the glyph goes, playback does not resume.
        assertThat(CentreGlyph.flashesPlay(Kind.PAUSED, Kind.NONE, playWhenReady = false)).isFalse()
        // Resumed into a stall: the ring takes the centre, no flash under it.
        assertThat(CentreGlyph.flashesPlay(Kind.PAUSED, Kind.LOADING, playWhenReady = true)).isFalse()
        assertThat(CentreGlyph.flashesPlay(Kind.NONE, Kind.NONE, playWhenReady = true)).isFalse()
    }
}
