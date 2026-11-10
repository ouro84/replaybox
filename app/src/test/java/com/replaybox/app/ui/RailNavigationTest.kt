package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RailNavigationTest {

    @Test
    fun everyDestinationSitsOnHome() {
        assertThat(RailNavigation.stackFor(RailDestination.HOME)).containsExactly(Screen.Home)
        assertThat(RailNavigation.stackFor(RailDestination.SEARCH)).containsExactly(Screen.Home, Screen.Search).inOrder()
        assertThat(RailNavigation.stackFor(RailDestination.GENRES)).containsExactly(Screen.Home, Screen.Genres).inOrder()
        assertThat(RailNavigation.stackFor(RailDestination.SETTINGS)).containsExactly(Screen.Home, Screen.Settings).inOrder()
    }

    @Test
    fun theDestinationOnScreenIsNoMove() {
        assertThat(RailNavigation.next(listOf(Screen.Home, Screen.Search), RailDestination.SEARCH)).isNull()
        assertThat(RailNavigation.next(listOf(Screen.Home), RailDestination.HOME)).isNull()
    }

    @Test
    fun anotherDestinationResetsTheStack() {
        assertThat(RailNavigation.next(listOf(Screen.Home, Screen.Search), RailDestination.GENRES))
            .containsExactly(Screen.Home, Screen.Genres).inOrder()
        assertThat(RailNavigation.next(listOf(Screen.Home, Screen.Settings), RailDestination.HOME))
            .containsExactly(Screen.Home)
    }
}
