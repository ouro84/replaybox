package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutClassTest {

    @Test
    fun tvIsAlwaysTv() {
        assertThat(LayoutClass.of(mobile = false, heightDp = 300)).isEqualTo(LayoutClass.TV)
        assertThat(LayoutClass.of(mobile = false, heightDp = 1000)).isEqualTo(LayoutClass.TV)
    }

    @Test
    fun mobileSplitsAt480() {
        assertThat(LayoutClass.of(mobile = true, heightDp = 479)).isEqualTo(LayoutClass.COMPACT)
        assertThat(LayoutClass.of(mobile = true, heightDp = 411)).isEqualTo(LayoutClass.COMPACT)
        assertThat(LayoutClass.of(mobile = true, heightDp = 480)).isEqualTo(LayoutClass.TABLET)
        assertThat(LayoutClass.of(mobile = true, heightDp = 617)).isEqualTo(LayoutClass.TABLET)
    }
}
