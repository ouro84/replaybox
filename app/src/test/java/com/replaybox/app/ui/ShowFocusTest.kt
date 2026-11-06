package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Programme page focus order: where focus lands once content is ready. */
class ShowFocusTest {

    @Test
    fun `on error Reessayer comes first`() {
        assertThat(showFocusCandidates(focusedKey = "ep:e1", failed = true).first()).isEqualTo("show:retry")
    }

    @Test
    fun `otherwise the remembered key, then play, then pin`() {
        assertThat(showFocusCandidates(focusedKey = "ep:e1", failed = false))
            .containsExactly("ep:e1", "show:play", "show:pin").inOrder()
        assertThat(showFocusCandidates(focusedKey = null, failed = false))
            .containsExactly("show:play", "show:pin").inOrder()
    }
}
