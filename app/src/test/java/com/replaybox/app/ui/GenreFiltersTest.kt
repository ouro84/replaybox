package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GenreFiltersTest {

    private val groups = listOf(
        ProviderGroup("tf1", "TF1+", listOf("tf1", "tmc"), account = null),
        ProviderGroup("francetv", "France TV", listOf("france-2", "france-3"), account = null),
        ProviderGroup("m6", "M6+", listOf("m6", "w9"), account = null),
    )

    @Test
    fun m6ChipOnlyWhenItsChainesAreListed() {
        val withoutM6 = GenreFilters.available(groups, listOf("tf1", "tmc", "france-2", "france-3"))
        assertThat(withoutM6.map { it.groupId }).containsExactly(null, "tf1", "francetv").inOrder()

        val withM6 = GenreFilters.available(groups, listOf("tf1", "tmc", "france-2", "france-3", "m6", "w9"))
        assertThat(withM6.map { it.groupId }).containsExactly(null, "tf1", "francetv", "m6").inOrder()
        assertThat(withM6.first().channelIds).hasSize(6)
        assertThat(withM6.last().channelIds).containsExactly("m6", "w9").inOrder()
    }

    @Test
    fun aGroupKeepsOnlyItsListedChaines() {
        val filters = GenreFilters.available(groups, listOf("tf1", "france-3"))
        assertThat(filters.first { it.groupId == "francetv" }.channelIds).containsExactly("france-3")
    }

    @Test
    fun aRememberedGroupThatIsGoneFallsBackToTout() {
        val filters = GenreFilters.available(groups, listOf("tf1", "france-2"))
        assertThat(GenreFilters.selected(filters, "m6").groupId).isNull()
        assertThat(GenreFilters.selected(filters, "tf1").groupId).isEqualTo("tf1")
    }
}
