package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.show
import com.replaybox.core.model.Genre
import org.junit.Test

class SearchStateTest {

    private val profilage = SearchEntry(show("p", "Profilage"), "tf1")
    private val pros = SearchEntry(show("x", "Les Pros"), "tf1")
    private val withBest = SearchView(best = profilage, grid = listOf(pros), status = null)
    private val gridOnly = SearchView(best = null, grid = listOf(pros), status = null)

    @Test
    fun returningWithTheSameQueryDoesNotSearchAgain() {
        val state = SearchUiState()
        state.query = "pekin"
        state.hits = SearchHits("pekin", emptyList())

        assertThat(state.needsSearch(fromRetry = false)).isFalse()
    }

    @Test
    fun aNewQueryOrARetrySearches() {
        val state = SearchUiState()
        state.query = "pekin"
        state.hits = SearchHits("peki", emptyList())

        assertThat(state.needsSearch(fromRetry = false)).isTrue()
        state.hits = SearchHits("pekin", emptyList())
        assertThat(state.needsSearch(fromRetry = true)).isTrue()
    }

    @Test
    fun anEmptyQueryNeverSearches() {
        val state = SearchUiState()
        state.query = "  "

        assertThat(state.needsSearch(fromRetry = false)).isFalse()
    }

    @Test
    fun firstVisitFocusesTheAKey() {
        assertThat(SearchUiState().initialFocus(SearchView.HINT, resumeShown = false)).isEqualTo(SearchFocus.KEY_A)
    }

    @Test
    fun focusReturnsToTheOpenedResult() {
        val state = SearchUiState()
        state.focus(SearchFocus.result("x"))

        assertThat(state.initialFocus(withBest, resumeShown = false)).isEqualTo(SearchFocus.result("x"))
    }

    @Test
    fun focusReturnsToTheKeyOrChipFocusedLast() {
        val state = SearchUiState()
        state.focus("key:R")
        assertThat(state.initialFocus(withBest, resumeShown = false)).isEqualTo("key:R")

        state.focus(SearchFocus.genreChip(Genre.DRAMA))
        assertThat(state.initialFocus(withBest, resumeShown = false)).isEqualTo(SearchFocus.genreChip(Genre.DRAMA))
    }

    @Test
    fun aVanishedTargetFallsBackToTheAKey() {
        val state = SearchUiState()
        state.focus(SearchFocus.result("gone"))
        assertThat(state.initialFocus(withBest, resumeShown = false)).isEqualTo(SearchFocus.KEY_A)

        state.focus(SearchFocus.RETRY)
        assertThat(state.initialFocus(withBest, resumeShown = false)).isEqualTo(SearchFocus.KEY_A)
    }

    @Test
    fun reprendreFinishedFallsBackToEpisodes() {
        val state = SearchUiState()
        state.focus(SearchFocus.BEST_RESUME)

        assertThat(state.initialFocus(withBest, resumeShown = true)).isEqualTo(SearchFocus.BEST_RESUME)
        assertThat(state.initialFocus(withBest, resumeShown = false)).isEqualTo(SearchFocus.BEST_EPISODES)
        assertThat(state.initialFocus(gridOnly, resumeShown = false)).isEqualTo(SearchFocus.KEY_A)
    }

    @Test
    fun rightEntersOnTheBestResultThenTheGridThenRetryThenTheChip() {
        val state = SearchUiState()

        assertThat(state.rightEntry(withBest, resumeShown = true)).isEqualTo(SearchFocus.BEST_RESUME)
        assertThat(state.rightEntry(withBest, resumeShown = false)).isEqualTo(SearchFocus.BEST_EPISODES)
        assertThat(state.rightEntry(gridOnly, resumeShown = false)).isEqualTo(SearchFocus.result("x"))
        assertThat(state.rightEntry(SearchView.status(SearchStatus.UNAVAILABLE), resumeShown = false)).isEqualTo(SearchFocus.RETRY)
        assertThat(state.rightEntry(SearchView.HINT, resumeShown = false)).isEqualTo(SearchFocus.groupChip(null))
        state.filter = SearchFilter(groupId = ProviderGroups.TF1)
        assertThat(state.rightEntry(SearchView.status(SearchStatus.EMPTY), resumeShown = false))
            .isEqualTo(SearchFocus.groupChip(ProviderGroups.TF1))
    }

    @Test
    fun rightReturnsToTheRightColumnElementFocusedLast() {
        val state = SearchUiState()
        state.focus(SearchFocus.result("x"))
        state.focus("key:F")

        assertThat(state.rightEntry(withBest, resumeShown = false)).isEqualTo(SearchFocus.result("x"))
    }

    @Test
    fun typingForgetsTheRightColumnElement() {
        val state = SearchUiState()
        state.focus(SearchFocus.result("x"))
        state.focus("key:F")

        state.type(KeyboardKey.Letter('F'))

        assertThat(state.query).isEqualTo("f")
        assertThat(state.rightEntry(withBest, resumeShown = false)).isEqualTo(SearchFocus.BEST_EPISODES)
    }

    @Test
    fun aKeyThatChangesNothingKeepsTheRightColumnElement() {
        val state = SearchUiState()
        state.focus(SearchFocus.result("x"))

        state.type(KeyboardKey.Delete) // nothing to delete

        assertThat(state.rightEntry(withBest, resumeShown = false)).isEqualTo(SearchFocus.result("x"))
    }

    @Test
    fun focusTracksTheKeyboardAndTheRightColumn() {
        val state = SearchUiState()
        assertThat(state.inRightColumn).isFalse()
        assertThat(state.lastKey).isEqualTo(SearchFocus.KEY_A)

        state.focus("key:M")
        assertThat(state.lastKey).isEqualTo("key:M")
        assertThat(state.inRightColumn).isFalse()

        state.focus(SearchFocus.groupChip(null))
        assertThat(state.inRightColumn).isTrue()
        assertThat(state.lastKey).isEqualTo("key:M")
        assertThat(state.focusKey).isEqualTo(SearchFocus.groupChip(null))
    }

    @Test
    fun setQueryKeepsWhatWasTyped() {
        val state = SearchUiState()

        assertThat(state.setQuery("Pékin Express")).isTrue()
        assertThat(state.query).isEqualTo("Pékin Express")
    }

    @Test
    fun setQueryCapsAtMaxLength() {
        val state = SearchUiState()

        state.setQuery("a".repeat(200))

        assertThat(state.query).hasLength(SearchKeyboard.MAX_LENGTH)
    }

    @Test
    fun setQuerySameTextIsNoChange() {
        val state = SearchUiState()
        state.setQuery("nar")

        assertThat(state.setQuery("nar")).isFalse()
    }

    @Test
    fun opensKeyboardOnEntryOnlyWhenEmpty() {
        val state = SearchUiState()
        assertThat(state.opensKeyboardOnEntry).isTrue()

        state.setQuery("nar")

        assertThat(state.opensKeyboardOnEntry).isFalse()
    }
}
