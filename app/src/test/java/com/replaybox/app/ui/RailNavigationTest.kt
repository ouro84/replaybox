package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ShowId
import org.junit.Test

class RailNavigationTest {

    private val show = Screen.ShowDetail(ShowId("s"), "S")
    private val grid = Screen.GenreGrid(Genre.SERIES)
    private val player = Screen.Player(EpisodeId("e"), "E")

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

    @Test
    fun destinationFollowsTheRootMostRailScreen() {
        assertThat(RailNavigation.destinationFor(listOf(Screen.Home))).isEqualTo(RailDestination.HOME)
        assertThat(RailNavigation.destinationFor(listOf(Screen.Home, show))).isEqualTo(RailDestination.HOME)
        for (stack in listOf(
            listOf(Screen.Home, Screen.Genres),
            listOf(Screen.Home, Screen.Genres, grid),
            listOf(Screen.Home, grid),
            listOf(Screen.Home, Screen.Genres, grid, show),
        )) assertThat(RailNavigation.destinationFor(stack)).isEqualTo(RailDestination.GENRES)
        assertThat(RailNavigation.destinationFor(listOf(Screen.Home, Screen.Search, show))).isEqualTo(RailDestination.SEARCH)
        for (stack in listOf(
            listOf(Screen.Home, Screen.Settings, Screen.SubtitleSettings),
            listOf(Screen.Home, Screen.Settings, Screen.ProviderDetail("tf1")),
            listOf(Screen.Home, Screen.Settings, Screen.About),
        )) assertThat(RailNavigation.destinationFor(stack)).isEqualTo(RailDestination.SETTINGS)
    }

    @Test
    fun noRailInThePlayer() {
        assertThat(RailNavigation.destinationFor(listOf(Screen.Home, show, player))).isNull()
        assertThat(RailNavigation.destinationFor(listOf(Screen.Home, Screen.Search, show, player))).isNull()
    }

    @Test
    fun backArrowEverywhereButHomeRoot() {
        assertThat(RailNavigation.showsBack(listOf(Screen.Home))).isFalse()
        assertThat(RailNavigation.showsBack(listOf(Screen.Home, Screen.Search))).isTrue()
        assertThat(RailNavigation.showsBack(listOf(Screen.Home, show))).isTrue()
        assertThat(RailNavigation.showsBack(listOf(Screen.Home, Screen.Settings, Screen.About))).isTrue()
        assertThat(RailNavigation.showsBack(listOf(Screen.Home, show, player))).isFalse()
    }

    @Test
    fun highlightedDestinationFromASubScreenReturnsToItsRoot() {
        assertThat(RailNavigation.next(listOf(Screen.Home, Screen.Search, show), RailDestination.SEARCH))
            .containsExactly(Screen.Home, Screen.Search).inOrder()
        assertThat(RailNavigation.next(listOf(Screen.Home, show), RailDestination.HOME))
            .containsExactly(Screen.Home)
        assertThat(RailNavigation.next(listOf(Screen.Home, Screen.Settings, Screen.About), RailDestination.SETTINGS))
            .containsExactly(Screen.Home, Screen.Settings).inOrder()
        assertThat(RailNavigation.next(listOf(Screen.Home, grid), RailDestination.GENRES))
            .containsExactly(Screen.Home, Screen.Genres).inOrder()
    }

    @Test
    fun returningKeepsTheDestinationState() {
        assertThat(RailNavigation.keepsState(listOf(Screen.Home, Screen.Search, show), RailDestination.SEARCH)).isTrue()
        assertThat(RailNavigation.keepsState(listOf(Screen.Home, Screen.Genres, grid), RailDestination.GENRES)).isTrue()
        assertThat(RailNavigation.keepsState(listOf(Screen.Home, grid), RailDestination.GENRES)).isFalse()
        assertThat(RailNavigation.keepsState(listOf(Screen.Home, Screen.Search), RailDestination.GENRES)).isFalse()
        assertThat(RailNavigation.keepsState(listOf(Screen.Home, Screen.Search), RailDestination.SEARCH)).isFalse()
    }
}
