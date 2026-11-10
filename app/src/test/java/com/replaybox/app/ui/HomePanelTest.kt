package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomePanelTest {

    private val focusedCard = Featured("Naruto", null, null, null)
    private val billboard = Featured("Bel & bien", null, null, null)

    @Test
    fun boxFollowsFocusFirst() {
        assertThat(panelFeatured(mobile = false, focused = focusedCard, billboard = billboard)).isEqualTo(focusedCard)
        assertThat(panelFeatured(mobile = false, focused = null, billboard = billboard)).isEqualTo(billboard)
    }

    @Test
    fun mobileShowsOnlyTheBillboard() {
        assertThat(panelFeatured(mobile = true, focused = focusedCard, billboard = billboard)).isEqualTo(billboard)
        assertThat(panelFeatured(mobile = true, focused = focusedCard, billboard = null)).isNull()
    }
}
