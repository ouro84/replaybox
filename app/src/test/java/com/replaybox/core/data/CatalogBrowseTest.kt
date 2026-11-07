package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

/** Search with an empty query and an active filter: the cached grid, like « Tout voir ». */
class CatalogBrowseTest {

    private val day = 86_400_000L

    @Test
    fun aGenreAloneListsItsProgrammesOnEveryGivenChaine() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("s", "Série", Genre.SERIES), show("d", "Docu", Genre.DOCUMENTARY)), 0)
            dao.putListing("france-2", listOf(show("p", "Plus belle la vie", Genre.SERIES)), day)
            val provider = FakeProvider()
            val catalog = CatalogStore(dao)

            val grid = catalog.browse(listOf("tf1", "france-2"), Genre.SERIES)

            assertThat(grid.map { it.show.id.value }).containsExactly("p", "s").inOrder()
            assertThat(provider.showsCalls).isEqualTo(0)
        }
    }

    @Test
    fun noGenreListsEveryProgrammeOfTheChaines() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("s", "Série", Genre.SERIES), show("d", "Docu", Genre.DOCUMENTARY)), 0)
            dao.putListing("france-2", listOf(show("p", "Plus belle la vie", Genre.SERIES)), day)
            val catalog = CatalogStore(dao)

            val grid = catalog.browse(listOf("tf1"), genre = null)

            assertThat(grid.map { it.show.id.value }).containsExactly("d", "s").inOrder()
            assertThat(grid.map { it.channelId }.distinct()).containsExactly("tf1")
        }
    }

    @Test
    fun theSameTitleOnTwoChainesIsListedOnce() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("a", "Capitaine Marleau", Genre.SERIES)), 0)
            dao.putListing("france-3", listOf(show("b", "CAPITAINE MARLEAU", Genre.SERIES)), 0)
            val catalog = CatalogStore(dao)

            assertThat(catalog.browse(listOf("tf1", "france-3"), null).map { it.show.id.value }).containsExactly("a")
        }
    }
}
