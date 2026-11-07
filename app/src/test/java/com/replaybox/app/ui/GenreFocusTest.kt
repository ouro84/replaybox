package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import org.junit.Test

/** Focus on arrival and on return for the Genres page and the « Tout voir » grid. */
class GenreFocusTest {

    private fun entry(id: String) = ChannelShow(Show(ShowId(id), id, null, null, setOf(Genre.SERIES)), "tf1")

    @Test
    fun genresPageFocusesTheFirstTileThenTheOpenedOne() {
        val state = GenresUiState()
        assertThat(state.initialFocus()).isEqualTo(Genre.SERIES)
        state.openedGenre = Genre.DOCUMENTARY
        assertThat(state.initialFocus()).isEqualTo(Genre.DOCUMENTARY)
    }

    @Test
    fun gridFocusesTheFirstProgrammeOnArrival() {
        val state = GenreGridUiState(Genre.SERIES)
        state.items = listOf(entry("a"), entry("b"))
        assertThat(state.initialFocus()).isEqualTo("a")
    }

    @Test
    fun gridReturnsToTheOpenedProgrammeOrChip() {
        val state = GenreGridUiState(Genre.SERIES)
        state.items = listOf(entry("a"), entry("b"))
        state.focusKey = "b"
        assertThat(state.initialFocus()).isEqualTo("b")
        state.focusKey = GenreGridUiState.chipKey("tf1")
        assertThat(state.initialFocus()).isEqualTo("chip:tf1")
    }

    @Test
    fun emptyGridFocusesTheSelectedChip() {
        val state = GenreGridUiState(Genre.SERIES)
        state.items = emptyList()
        state.focusKey = "gone"
        assertThat(state.initialFocus()).isEqualTo("chip:all")
        state.groupId = "francetv"
        assertThat(state.initialFocus()).isEqualTo("chip:francetv")
    }
}
