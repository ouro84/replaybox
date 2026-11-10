package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.app.ui.PlayerTouch.Seek
import com.replaybox.app.ui.PlayerTouch.Tap
import org.junit.Test

class PlayerTouchTest {

    @Test
    fun tapShowsHiddenControls() {
        assertThat(PlayerTouch.tap(controlsVisible = false, optionsOpen = false, prompt = false)).isEqualTo(Tap.SHOW)
    }

    @Test
    fun tapHidesShownControls() {
        assertThat(PlayerTouch.tap(controlsVisible = true, optionsOpen = false, prompt = false)).isEqualTo(Tap.HIDE)
    }

    @Test
    fun tapClosesOptions() {
        assertThat(PlayerTouch.tap(controlsVisible = false, optionsOpen = true, prompt = false)).isEqualTo(Tap.CLOSE_OPTIONS)
    }

    @Test
    fun promptOrNextUpIgnoresTaps() {
        assertThat(PlayerTouch.tap(controlsVisible = false, optionsOpen = false, prompt = true)).isEqualTo(Tap.NONE)
        assertThat(PlayerTouch.doubleTap(x = 10f, width = 100f, optionsOpen = false, prompt = true)).isEqualTo(Seek.NONE)
    }

    @Test
    fun doubleTapSidesSeek() {
        assertThat(PlayerTouch.doubleTap(x = 10f, width = 100f, optionsOpen = false, prompt = false)).isEqualTo(Seek.BACK)
        assertThat(PlayerTouch.doubleTap(x = 49.9f, width = 100f, optionsOpen = false, prompt = false)).isEqualTo(Seek.BACK)
        assertThat(PlayerTouch.doubleTap(x = 50f, width = 100f, optionsOpen = false, prompt = false)).isEqualTo(Seek.FORWARD)
        assertThat(PlayerTouch.doubleTap(x = 99f, width = 100f, optionsOpen = false, prompt = false)).isEqualTo(Seek.FORWARD)
    }

    @Test
    fun doubleTapDoesNothingOverOptions() {
        assertThat(PlayerTouch.doubleTap(x = 90f, width = 100f, optionsOpen = true, prompt = false)).isEqualTo(Seek.NONE)
    }
}
