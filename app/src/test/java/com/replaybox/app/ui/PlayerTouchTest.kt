package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.app.ui.PlayerTouch.Seek
import com.replaybox.app.ui.PlayerTouch.Tap
import org.junit.Test

class PlayerTouchTest {

    /** A still, quick press: under the slop and the long-press timeout. */
    private fun tap(optionsOpen: Boolean, prompt: Boolean, movedPx: Float = 0f, heldMs: Long = 100L) =
        PlayerTouch.tap(optionsOpen, prompt, movedPx = movedPx, heldMs = heldMs, slopPx = SLOP, longPressMs = LONG_PRESS)

    private fun doubleTap(
        x: Float,
        optionsOpen: Boolean = false,
        prompt: Boolean = false,
        movedPx: Float = 0f,
        heldMs: Long = 100L,
        firstMovedPx: Float = 0f,
        firstHeldMs: Long = 100L,
    ) = PlayerTouch.doubleTap(
        x, width = 100f, optionsOpen, prompt,
        movedPx = movedPx, heldMs = heldMs, firstMovedPx = firstMovedPx, firstHeldMs = firstHeldMs,
        slopPx = SLOP, longPressMs = LONG_PRESS,
    )

    @Test
    fun tapTogglesPlayback() {
        assertThat(tap(optionsOpen = false, prompt = false)).isEqualTo(Tap.TOGGLE_PLAY)
    }

    @Test
    fun tapClosesOptions() {
        assertThat(tap(optionsOpen = true, prompt = false)).isEqualTo(Tap.CLOSE_OPTIONS)
    }

    @Test
    fun promptOrNextUpIgnoresTaps() {
        assertThat(tap(optionsOpen = false, prompt = true)).isEqualTo(Tap.NONE)
        assertThat(tap(optionsOpen = true, prompt = true)).isEqualTo(Tap.NONE)
        assertThat(doubleTap(x = 10f, optionsOpen = false, prompt = true)).isEqualTo(Seek.NONE)
    }

    @Test
    fun doubleTapSidesSeek() {
        assertThat(doubleTap(x = 10f, optionsOpen = false, prompt = false)).isEqualTo(Seek.BACK)
        assertThat(doubleTap(x = 49.9f, optionsOpen = false, prompt = false)).isEqualTo(Seek.BACK)
        assertThat(doubleTap(x = 50f, optionsOpen = false, prompt = false)).isEqualTo(Seek.FORWARD)
        assertThat(doubleTap(x = 99f, optionsOpen = false, prompt = false)).isEqualTo(Seek.FORWARD)
    }

    @Test
    fun doubleTapDoesNothingOverOptions() {
        assertThat(doubleTap(x = 90f, optionsOpen = true, prompt = false)).isEqualTo(Seek.NONE)
    }

    @Test
    fun aSwipeIsNoTap() {
        // A stray swipe, a thumb sliding on the glass, the status bar pulled in immersive mode.
        assertThat(tap(optionsOpen = false, prompt = false, movedPx = SLOP + 1f)).isEqualTo(Tap.NONE)
        assertThat(tap(optionsOpen = true, prompt = false, movedPx = SLOP + 1f)).isEqualTo(Tap.NONE)
        assertThat(tap(optionsOpen = false, prompt = false, movedPx = SLOP)).isEqualTo(Tap.TOGGLE_PLAY)
    }

    @Test
    fun aLongHoldIsNoTap() {
        // A thumb resting on the screen.
        assertThat(tap(optionsOpen = false, prompt = false, heldMs = LONG_PRESS + 1)).isEqualTo(Tap.NONE)
        assertThat(tap(optionsOpen = false, prompt = false, heldMs = LONG_PRESS)).isEqualTo(Tap.TOGGLE_PLAY)
    }

    @Test
    fun aSwipeOrHoldAsTheSecondTapDoesNotSeek() {
        assertThat(doubleTap(x = 90f, movedPx = SLOP + 1f)).isEqualTo(Seek.NONE)
        assertThat(doubleTap(x = 10f, heldMs = LONG_PRESS + 1)).isEqualTo(Seek.NONE)
    }

    @Test
    fun aSwipeOrHoldAsTheFirstTapDoesNotSeek() {
        // A swipe followed within the double-tap window by a still tap is no double-tap.
        assertThat(doubleTap(x = 90f, firstMovedPx = SLOP + 1f)).isEqualTo(Seek.NONE)
        assertThat(doubleTap(x = 10f, firstHeldMs = LONG_PRESS + 1)).isEqualTo(Seek.NONE)
        assertThat(doubleTap(x = 90f, firstMovedPx = SLOP, firstHeldMs = LONG_PRESS)).isEqualTo(Seek.FORWARD)
    }

    private companion object {
        const val SLOP = 18f
        const val LONG_PRESS = 400L
    }
}
