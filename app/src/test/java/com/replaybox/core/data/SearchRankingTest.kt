package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Local search ranking (plan M3 step 2): prefix, then contains, then recent Épisodes. */
class SearchRankingTest {

    @Test
    fun prefixBeforeContains() {
        val ranked = SearchRanking.rank("exp", listOf(show("experts", "Les Experts"), show("expedition", "Expédition X")))
        assertThat(ranked.map { it.id.value }).containsExactly("expedition", "experts").inOrder()
    }

    @Test
    fun tiesBrokenByRecentEpisodes() {
        val few = show("few", "Profilage").copy(recentEpisodeCount = 2)
        val many = show("many", "Profils").copy(recentEpisodeCount = 9)
        assertThat(SearchRanking.rank("prof", listOf(few, many)).map { it.id.value })
            .containsExactly("many", "few").inOrder()

        val first = show("first", "Profilage").copy(recentEpisodeCount = 3)
        val second = show("second", "Profils").copy(recentEpisodeCount = 3)
        assertThat(SearchRanking.rank("prof", listOf(first, second)).map { it.id.value })
            .containsExactly("first", "second").inOrder()
    }

    @Test
    fun accentAndCaseInsensitive() {
        assertThat(SearchRanking.rank("EXPE", listOf(show("x", "Expédition X"))).map { it.id.value })
            .containsExactly("x")
    }

    @Test
    fun nonMatchesAreDropped() {
        assertThat(SearchRanking.rank("zzz", listOf(show("a", "Alpha")))).isEmpty()
        assertThat(SearchRanking.rank("  ", listOf(show("a", "Alpha")))).isEmpty()
    }
}
