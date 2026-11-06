package com.replaybox.app.ui

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Remote keys over the player (decisions Q1, Q28). */
class PlayerKeysTest {

    @Test
    fun `hidden controls - arrows seek, OK plays or pauses, CC toggles subtitles`() {
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_DPAD_LEFT, controlsVisible = false)).isEqualTo(PlayerKeys.Action.SEEK_BACK)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_DPAD_RIGHT, controlsVisible = false)).isEqualTo(PlayerKeys.Action.SEEK_FORWARD)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_DPAD_CENTER, controlsVisible = false)).isEqualTo(PlayerKeys.Action.PLAY_PAUSE)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_CAPTIONS, controlsVisible = false)).isEqualTo(PlayerKeys.Action.TOGGLE_SUBTITLES)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_DPAD_UP, controlsVisible = false)).isEqualTo(PlayerKeys.Action.SHOW)
    }

    @Test
    fun `shown controls - the D-pad belongs to the buttons, CC still toggles`() {
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_DPAD_LEFT, controlsVisible = true)).isEqualTo(PlayerKeys.Action.NONE)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_DPAD_CENTER, controlsVisible = true)).isEqualTo(PlayerKeys.Action.NONE)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_CAPTIONS, controlsVisible = true)).isEqualTo(PlayerKeys.Action.TOGGLE_SUBTITLES)
    }

    @Test
    fun `MENU opens Options whether the controls are shown or not`() {
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_MENU, controlsVisible = false)).isEqualTo(PlayerKeys.Action.OPTIONS)
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_MENU, controlsVisible = true)).isEqualTo(PlayerKeys.Action.OPTIONS)
    }

    @Test
    fun `media keys are left to the media session`() {
        assertThat(PlayerKeys.action(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, controlsVisible = false)).isEqualTo(PlayerKeys.Action.NONE)
    }
}
