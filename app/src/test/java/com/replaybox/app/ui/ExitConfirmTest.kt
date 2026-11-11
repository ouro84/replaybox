package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExitConfirmTest {

    @Test
    fun scrolledDownBackReturnsToTheTop() {
        // Not an exit: Home first scrolls back to its top.
        assertThat(ExitConfirm.onHomeBack(atTop = false)).isEqualTo(HomeBack.SCROLL_TO_TOP)
    }

    @Test
    fun fromTheTopBackAsksToLeave() {
        assertThat(ExitConfirm.onHomeBack(atTop = true)).isEqualTo(HomeBack.ASK)
    }
}
