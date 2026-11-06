package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.show
import com.replaybox.core.model.Genre
import com.replaybox.core.data.FakeProvider
import com.replaybox.core.data.blocking
import com.replaybox.core.model.Channel
import org.junit.Test

/** SPEC §Recherche: fusion by normalized title, first Chaîne wins. */
class SearchFusionTest {

    @Test
    fun `merges providers deduplicating normalized titles`() {
        val tf1 = listOf(
            show("tf1-marleau", "Capitaine Marleau", Genre.CRIME_THRILLER),
            show("tf1-tracker", "Tracker"),
        )
        val ftv = listOf(
            show("ftv-marleau", "CAPITAINE MARLEAU", Genre.SERIES),
            show("ftv-code-rouge", "Code Rouge"),
        )
        val fused = SearchFusion.fuse("marleau", listOf(tf1, ftv))
        assertThat(fused.map { it.id.value }).containsExactly("tf1-marleau")

        val all = SearchFusion.fuse("e", listOf(tf1, ftv))
        assertThat(all.map { it.id.value }).containsExactly("tf1-marleau", "tf1-tracker", "ftv-code-rouge").inOrder()
    }

    @Test
    fun `ignores results not matching the query`() {
        val results = SearchFusion.fuse("zzz", listOf(listOf(show("a", "Alpha"))))
        assertThat(results).isEmpty()
    }

    @Test
    fun `accent differences do not split results`() {
        val one = listOf(show("a", "César Wagner"))
        val two = listOf(show("b", "cesar wagner"))
        assertThat(SearchFusion.fuse("cesar", listOf(one, two)).map { it.id.value })
            .containsExactly("a")
    }

    @Test
    fun `origins credit the Provider whose result was kept`() {
        val tf1 = listOf(show("tf1-marleau", "Capitaine Marleau"), show("tf1-chrono", "Enquête Chrono"))
        val ftv = listOf(show("ftv-marleau", "Capitaine Marleau"), show("ftv-vera", "Les enquêtes de Vera"))
        val results = listOf(tf1, ftv)
        val fused = SearchFusion.fuse("e", results)

        val origins = SearchFusion.origins(fused, listOf("tf1", "france-3"), results)

        assertThat(origins.mapKeys { it.key.value }).containsExactly(
            "tf1-marleau", "tf1",
            "tf1-chrono", "tf1",
            "ftv-vera", "france-3",
        )
    }

    @Test
    fun `every Provider failing is a failure, not an empty result`() {
        val hits = blocking {
            SearchFusion.search(
                listOf(
                    FakeProvider(channel = Channel("tf1", "TF1+"), searchError = java.io.IOException("offline")),
                    FakeProvider(channel = Channel("ftv", "France TV"), searchError = java.io.IOException("offline")),
                ),
                "tracker",
            )
        }
        assertThat(hits.failed).isTrue()
        assertThat(hits.shows).isEmpty()
    }

    @Test
    fun `one Provider failing keeps the other's results`() {
        val hits = blocking {
            SearchFusion.search(
                listOf(
                    FakeProvider(channel = Channel("tf1", "TF1+"), showsResult = listOf(show("t", "Tracker"))),
                    FakeProvider(channel = Channel("ftv", "France TV"), searchError = java.io.IOException("offline")),
                ),
                "tracker",
            )
        }
        assertThat(hits.failed).isFalse()
        assertThat(hits.shows.map { it.title }).containsExactly("Tracker")
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `cancellation is not swallowed as a failure`() {
        blocking {
            SearchFusion.search(
                listOf(FakeProvider(searchError = kotlinx.coroutines.CancellationException("new query"))),
                "tracker",
            )
        }
    }
}
