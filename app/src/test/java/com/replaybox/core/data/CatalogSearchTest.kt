package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import org.junit.Test

/** SPEC §Recherche: France TV search is a normalized-title cache filter. */
class CatalogSearchTest {

    @Test
    fun `searches cached listings across channels by normalized title`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("france-2", listOf(show("f2-a", "César Wagner")), 0)
            dao.putListing("franceinfo", listOf(show("fi-b", "CÉSAR WAGNER INFO"), show("fi-c", "Autre")), 0)
            val store = CatalogStore(dao)

            val hits = store.searchShows(listOf("france-2", "franceinfo"), "cesar")
            assertThat(hits.map { it.id.value }).containsExactly("f2-a", "fi-b")

            // The store itself has no minimum length (the search UI enforces
            // the 3-char rule); any substring matches.
            assertThat(store.searchShows(listOf("france-2"), "ce").map { it.id.value })
                .containsExactly("f2-a")
            assertThat(store.searchShows(listOf("france-2"), "absent")).isEmpty()
        }
    }

    @Test
    fun `empty query yields nothing`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("france-2", listOf(show("f2-a", "Alpha")), 0)
            assertThat(CatalogStore(dao).searchShows(listOf("france-2"), "  ")).isEmpty()
        }
    }

    @Test
    fun `unlisted channels contribute nothing`() {
        blocking {
            val dao = FakeCatalogDao()
            val store = CatalogStore(dao)
            assertThat(store.searchShows(listOf("missing", Channel("x", "X").id), "alpha")).isEmpty()
        }
    }

    @Test
    fun `cached search is ranked, prefix matches first`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("experts", "Les Experts"), show("expedition", "Expédition X")), 0)
            val hits = CatalogStore(dao).searchShows(listOf("tf1"), "exp")
            assertThat(hits.map { it.id.value }).containsExactly("expedition", "experts").inOrder()
        }
    }
}
