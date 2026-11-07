package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import org.junit.Test

class SearchStateTest {

    private fun show(id: String) = Show(ShowId(id), id, null, null, emptySet())

    @Test
    fun returningWithTheSameQueryDoesNotSearchAgain() {
        val state = SearchUiState()
        state.query = "pekin"
        state.searchedQuery = "pekin"

        assertThat(state.needsSearch(fromRetry = false)).isFalse()
    }

    @Test
    fun aNewQueryOrARetrySearches() {
        val state = SearchUiState()
        state.query = "pekin"
        state.searchedQuery = "peki"

        assertThat(state.needsSearch(fromRetry = false)).isTrue()
        state.searchedQuery = "pekin"
        assertThat(state.needsSearch(fromRetry = true)).isTrue()
    }

    @Test
    fun focusReturnsToTheOpenedResult() {
        val state = SearchUiState()
        state.results = listOf(show("a"), show("b"))
        state.focusedId = "b"

        assertThat(state.initialFocus()).isEqualTo("b")
    }

    @Test
    fun focusGoesToTheFieldWhenNothingWasOpened() {
        val state = SearchUiState()
        assertThat(state.initialFocus()).isNull()

        state.results = listOf(show("a"))
        state.focusedId = "gone"
        assertThat(state.initialFocus()).isNull()
    }
}
