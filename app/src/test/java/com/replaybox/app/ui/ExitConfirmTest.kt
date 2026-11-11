package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ShowId
import org.junit.Test

class ExitConfirmTest {

    private val show = Screen.ShowDetail(ShowId("s"), "S")
    private val player = Screen.Player(EpisodeId("e"), "E")

    @Test
    fun asksOnlyFromTheTopOfHome() {
        assertThat(ExitConfirm.shows(listOf(Screen.Home), homeAtTop = true)).isTrue()
        // Home scrolls back to its top first: that Back is not an exit.
        assertThat(ExitConfirm.shows(listOf(Screen.Home), homeAtTop = false)).isFalse()
    }

    @Test
    fun neverAsksAwayFromHome() {
        assertThat(ExitConfirm.shows(listOf(Screen.Home, Screen.Search), homeAtTop = true)).isFalse()
        assertThat(ExitConfirm.shows(listOf(Screen.Home, show), homeAtTop = true)).isFalse()
        assertThat(ExitConfirm.shows(listOf(Screen.Home, show, player), homeAtTop = true)).isFalse()
    }
}
