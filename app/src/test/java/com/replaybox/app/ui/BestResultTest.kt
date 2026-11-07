package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.show
import org.junit.Test

class BestResultTest {

    private fun entry(id: String, title: String) = SearchEntry(show(id, title), "tf1")

    @Test
    fun anExactTitleIsTheBestResult() {
        val ranked = listOf(entry("p", "Profilage"), entry("x", "Les Pros"))

        assertThat(BestResult.pick("profilage", ranked)?.show?.id?.value).isEqualTo("p")
    }

    @Test
    fun aTitleStartingWithTheQueryIsTheBestResult() {
        val ranked = listOf(entry("p", "Profilage"))

        assertThat(BestResult.pick("PRO", ranked)?.show?.id?.value).isEqualTo("p")
    }

    @Test
    fun matchingIgnoresCaseAccentsAndOuterSpaces() {
        val ranked = listOf(entry("c", "César Wagner"))

        assertThat(BestResult.pick("  cesar ", ranked)?.show?.id?.value).isEqualTo("c")
    }

    @Test
    fun aTitleMerelyContainingTheQueryIsNotHighlighted() {
        val ranked = listOf(entry("m", "Capitaine Marleau"))

        assertThat(BestResult.pick("marleau", ranked)).isNull()
    }

    @Test
    fun onlyTheTopRankedResultCounts() {
        // Whatever the ranking put first decides; a later prefix match is not promoted over it.
        val ranked = listOf(entry("m", "Capitaine Marleau"), entry("x", "Marleau, l'enquête"))

        assertThat(BestResult.pick("marleau", ranked)).isNull()
    }

    @Test
    fun noQueryOrNoResultMeansNoHighlight() {
        assertThat(BestResult.pick("", listOf(entry("p", "Profilage")))).isNull()
        assertThat(BestResult.pick("pro", emptyList())).isNull()
    }
}
