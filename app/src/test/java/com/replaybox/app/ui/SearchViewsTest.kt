package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.show
import com.replaybox.core.model.Genre
import org.junit.Test

class SearchViewsTest {

    private val profilage = show("p", "Profilage", Genre.CRIME_THRILLER)
    private val pros = show("x", "Les Pros", Genre.ENTERTAINMENT)
    private val hits = SearchHits("pro", listOf(ChannelHits("tf1", listOf(profilage, pros))))

    private fun view(
        query: String,
        filter: SearchFilter = SearchFilter(),
        channelIds: Set<String>? = null,
        hits: SearchHits? = null,
        browse: BrowseResult? = null,
        searching: Boolean = false,
    ) = SearchViews.of(query, filter, channelIds, hits, browse, searching)

    @Test
    fun emptyQueryWithoutFilterShowsTheHint() {
        assertThat(view("").status).isEqualTo(SearchStatus.HINT)
        assertThat(view("   ", hits = hits).status).isEqualTo(SearchStatus.HINT)
    }

    @Test
    fun oneCharacterAlreadySearches() {
        val one = SearchHits("p", listOf(ChannelHits("tf1", listOf(profilage))))

        val shown = view("p", hits = one)

        assertThat(shown.status).isNull()
        assertThat(shown.best?.show).isEqualTo(profilage)
    }

    @Test
    fun theBestResultIsNotRepeatedInTheGrid() {
        val shown = view("pro", hits = hits)

        assertThat(shown.best?.show).isEqualTo(profilage)
        assertThat(shown.grid.map { it.show }).containsExactly(pros)
    }

    @Test
    fun withoutAPrefixMatchEveryResultIsInTheGrid() {
        val shown = view("ros", hits = SearchHits("ros", listOf(ChannelHits("tf1", listOf(pros)))))

        assertThat(shown.best).isNull()
        assertThat(shown.grid.map { it.show }).containsExactly(pros)
    }

    @Test
    fun filtersApplyToTheSearchResults() {
        val shown = view("pro", filter = SearchFilter(genre = Genre.ENTERTAINMENT), hits = hits)

        assertThat(shown.best).isNull() // « Les Pros » only contains the query
        assertThat(shown.grid.map { it.show }).containsExactly(pros)
    }

    @Test
    fun aChaineGroupFilterUsesItsChannelIds() {
        val twoChannels = SearchHits("pro", listOf(ChannelHits("tf1", listOf(profilage)), ChannelHits("m6", listOf(pros))))

        val shown = view("pro", filter = SearchFilter(groupId = ProviderGroups.M6), channelIds = setOf("m6"), hits = twoChannels)

        assertThat(shown.best).isNull()
        assertThat(shown.grid.map { it.show }).containsExactly(pros)
    }

    @Test
    fun noHitYetIsLoadingNeverEmpty() {
        assertThat(view("pro").status).isEqualTo(SearchStatus.LOADING)
        val none = SearchHits("zz", listOf(ChannelHits("tf1", emptyList())))
        assertThat(view("zz", hits = none, searching = true).status).isEqualTo(SearchStatus.LOADING)
        assertThat(view("zz", hits = none).status).isEqualTo(SearchStatus.EMPTY)
    }

    @Test
    fun previousResultsStayWhileTheNextQueryRuns() {
        val shown = view("prof", hits = hits, searching = true)

        assertThat(shown.status).isNull()
        assertThat(shown.best?.show).isEqualTo(profilage)
    }

    @Test
    fun staleEmptyHitsReadAsLoading() {
        val none = SearchHits("zz", listOf(ChannelHits("tf1", emptyList())))

        assertThat(view("zzz", hits = none).status).isEqualTo(SearchStatus.LOADING)
    }

    @Test
    fun everyProviderFailingIsUnavailable() {
        val failed = SearchHits("pro", listOf(ChannelHits("tf1", null), ChannelHits("france-2", null)))

        assertThat(view("pro", hits = failed).status).isEqualTo(SearchStatus.UNAVAILABLE)
        assertThat(view("pro", hits = failed, searching = true).status).isEqualTo(SearchStatus.LOADING)
    }

    @Test
    fun oneProviderFailingShowsTheOthers() {
        val partial = SearchHits("pro", listOf(ChannelHits("tf1", listOf(profilage)), ChannelHits("france-2", null)))

        val shown = view("pro", hits = partial)

        assertThat(shown.status).isNull()
        assertThat(shown.best?.show).isEqualTo(profilage)
    }

    @Test
    fun emptyQueryWithAFilterShowsTheFilteredGrid() {
        val filter = SearchFilter(genre = Genre.CRIME_THRILLER)
        val browse = BrowseResult(filter, listOf(SearchEntry(profilage, "tf1")))

        val shown = view("", filter = filter, browse = browse)

        assertThat(shown.status).isNull()
        assertThat(shown.best).isNull()
        assertThat(shown.grid.map { it.show }).containsExactly(profilage)
    }

    @Test
    fun filteredGridStatesAreLoadingThenEmpty() {
        val filter = SearchFilter(groupId = ProviderGroups.M6)

        assertThat(view("", filter = filter).status).isEqualTo(SearchStatus.LOADING)
        assertThat(view("", filter = filter, browse = BrowseResult(filter, emptyList())).status).isEqualTo(SearchStatus.EMPTY)
        // The previous filter's list stays on screen while the new one is read.
        val previous = BrowseResult(SearchFilter(genre = Genre.CRIME_THRILLER), listOf(SearchEntry(profilage, "tf1")))
        assertThat(view("", filter = filter, browse = previous).grid.map { it.show }).containsExactly(profilage)
    }
}
