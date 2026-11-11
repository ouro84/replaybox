package com.replaybox.app.ui

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.replaybox.app.ui.PlayerKeys.Action
import com.replaybox.app.ui.PlayerKeys.Focus
import org.junit.Test

/** Remote keys over the player (decisions Q1, Q28; UX grilling 2026-11-11, B3). */
class PlayerKeysTest {

    private fun actions(keyCode: Int): List<Action> =
        listOf(Focus.HIDDEN, Focus.CONTROLS, Focus.ROW, Focus.PANEL).map { PlayerKeys.action(keyCode, it) }

    @Test
    fun `Left seeks back unless a row button or Options has focus`() {
        assertThat(actions(KeyEvent.KEYCODE_DPAD_LEFT))
            .containsExactly(Action.SEEK_BACK, Action.SEEK_BACK, Action.NONE, Action.NONE).inOrder()
    }

    @Test
    fun `Right seeks forward unless a row button or Options has focus`() {
        assertThat(actions(KeyEvent.KEYCODE_DPAD_RIGHT))
            .containsExactly(Action.SEEK_FORWARD, Action.SEEK_FORWARD, Action.NONE, Action.NONE).inOrder()
    }

    @Test
    fun `OK plays or pauses with the controls hidden or shown, CENTER and ENTER alike`() {
        val expected = listOf(Action.PLAY_PAUSE, Action.PLAY_PAUSE, Action.NONE, Action.NONE)
        assertThat(actions(KeyEvent.KEYCODE_DPAD_CENTER)).containsExactlyElementsIn(expected).inOrder()
        assertThat(actions(KeyEvent.KEYCODE_ENTER)).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `Down shows the controls, then enters the row`() {
        assertThat(actions(KeyEvent.KEYCODE_DPAD_DOWN))
            .containsExactly(Action.SHOW, Action.ENTER_ROW, Action.NONE, Action.NONE).inOrder()
    }

    @Test
    fun `Up shows the controls, and leaves the row`() {
        assertThat(actions(KeyEvent.KEYCODE_DPAD_UP))
            .containsExactly(Action.SHOW, Action.SHOW, Action.LEAVE_ROW, Action.NONE).inOrder()
    }

    @Test
    fun `CC toggles the subtitles whatever has focus`() {
        assertThat(actions(KeyEvent.KEYCODE_CAPTIONS)).containsExactly(
            Action.TOGGLE_SUBTITLES, Action.TOGGLE_SUBTITLES, Action.TOGGLE_SUBTITLES, Action.TOGGLE_SUBTITLES,
        ).inOrder()
    }

    @Test
    fun `MENU opens Options whatever has focus`() {
        assertThat(actions(KeyEvent.KEYCODE_MENU))
            .containsExactly(Action.OPTIONS, Action.OPTIONS, Action.OPTIONS, Action.OPTIONS).inOrder()
    }

    @Test
    fun `media keys and Back are left alone`() {
        val none = listOf(Action.NONE, Action.NONE, Action.NONE, Action.NONE)
        assertThat(actions(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)).containsExactlyElementsIn(none).inOrder()
        assertThat(actions(KeyEvent.KEYCODE_BACK)).containsExactlyElementsIn(none).inOrder()
    }

    @Test
    fun `nothing shown is HIDDEN`() {
        assertThat(PlayerKeys.focus(controlsVisible = false, optionsOpen = false, focusInRow = false)).isEqualTo(Focus.HIDDEN)
    }

    @Test
    fun `controls shown with no row button focused is CONTROLS`() {
        assertThat(PlayerKeys.focus(controlsVisible = true, optionsOpen = false, focusInRow = false)).isEqualTo(Focus.CONTROLS)
    }

    @Test
    fun `controls shown with a row button focused is ROW`() {
        assertThat(PlayerKeys.focus(controlsVisible = true, optionsOpen = false, focusInRow = true)).isEqualTo(Focus.ROW)
    }

    @Test
    fun `Options open is PANEL whatever else`() {
        for (controlsVisible in listOf(false, true)) {
            for (focusInRow in listOf(false, true)) {
                assertThat(PlayerKeys.focus(controlsVisible, optionsOpen = true, focusInRow = focusInRow)).isEqualTo(Focus.PANEL)
            }
        }
    }

    @Test
    fun `stale row focus is ignored once hidden`() {
        assertThat(PlayerKeys.focus(controlsVisible = false, optionsOpen = false, focusInRow = true)).isEqualTo(Focus.HIDDEN)
    }
}
