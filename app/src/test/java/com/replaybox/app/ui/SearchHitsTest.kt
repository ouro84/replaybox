package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.show
import com.replaybox.core.model.Genre
import org.junit.Test

class SearchHitsTest {

    private val hits = SearchHits(
        query = "mar",
        channels = listOf(
            ChannelHits("tf1", listOf(show("tf1-marleau", "Capitaine Marleau", Genre.CRIME_THRILLER))),
            ChannelHits(
                "france-3",
                listOf(show("ftv-marleau", "Capitaine Marleau", Genre.SERIES), show("ftv-mar", "Mariages", Genre.DOCUMENTARY)),
            ),
            ChannelHits("m6", null),
        ),
    )

    @Test
    fun withoutFilterTheFirstChaineWinsTheFusion() {
        val entries = hits.filtered(channelIds = null, genre = null)

        assertThat(entries.map { it.show.id.value }).containsExactly("ftv-mar", "tf1-marleau").inOrder()
        assertThat(entries.first { it.show.id.value == "tf1-marleau" }.channelId).isEqualTo("tf1")
    }

    @Test
    fun aChaineGroupFiltersBeforeTheFusion() {
        // France TV keeps its own copy of a Programme TF1 also lists.
        val entries = hits.filtered(channelIds = setOf("france-3"), genre = null)

        assertThat(entries.map { it.show.id.value }).containsExactly("ftv-mar", "ftv-marleau").inOrder()
        assertThat(entries.map { it.channelId }.distinct()).containsExactly("france-3")
    }

    @Test
    fun aGenreKeepsOnlyItsProgrammes() {
        val entries = hits.filtered(channelIds = null, genre = Genre.SERIES)

        assertThat(entries.map { it.show.id.value }).containsExactly("ftv-marleau")
    }

    @Test
    fun bothFiltersCombine() {
        assertThat(hits.filtered(setOf("tf1"), Genre.DOCUMENTARY)).isEmpty()
        assertThat(hits.filtered(setOf("tf1"), Genre.CRIME_THRILLER).map { it.show.id.value }).containsExactly("tf1-marleau")
    }

    @Test
    fun failedOnlyWhenEveryChaineFailed() {
        assertThat(hits.failed).isFalse()
        assertThat(SearchHits("x", listOf(ChannelHits("tf1", null), ChannelHits("m6", null))).failed).isTrue()
        assertThat(SearchHits("", emptyList()).failed).isFalse()
    }
}
