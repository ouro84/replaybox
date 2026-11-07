package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import org.junit.Test

/** « Tout voir » grid: recency (day first listed) then title, across Chaînes, from the cache only. */
class GenreGridTest {

    private val day = 86_400_000L

    private fun entry(id: String, title: String, channel: String, firstSeenAt: Long, vararg genres: Genre) =
        ChannelShow(show(id, title, *genres).copy(firstSeenAt = firstSeenAt), channel)

    @Test
    fun newestDayFirstThenTitle() {
        val items = listOf(
            entry("a", "Zorro", "tf1", 0),
            entry("b", "Astrid", "france-2", 0),
            entry("c", "Mortel", "m6", 2 * day + 10),
            entry("d", "Été indien", "tf1", day),
            entry("e", "Balthazar", "tf1", 2 * day + 5),
        )
        val sorted = GenreGrid.sort(items).map { it.show.id.value }
        assertThat(sorted).containsExactly("e", "c", "d", "b", "a").inOrder()
    }

    @Test
    fun sameDayIgnoresRefreshOrder() {
        // One refresh writes the Chaînes seconds apart: same day, so title decides.
        val items = listOf(
            entry("late", "Alpha", "m6", 50_000),
            entry("early", "Beta", "tf1", 1_000),
        )
        assertThat(GenreGrid.sort(items).map { it.show.id.value }).containsExactly("late", "early").inOrder()
    }

    @Test
    fun titleOrderIgnoresCaseAndAccents() {
        val items = listOf(
            entry("1", "zone interdite", "m6", 0),
            entry("2", "Éléphant", "tf1", 0),
            entry("3", "Alpha", "tf1", 0),
        )
        assertThat(GenreGrid.sort(items).map { it.show.id.value }).containsExactly("3", "2", "1").inOrder()
    }

    @Test
    fun filterThenDedupe() {
        val items = listOf(
            entry("t", "HPI", "tf1", 0, Genre.SERIES),
            entry("f", "Hpi", "france-2", 0, Genre.SERIES),
            entry("x", "Astrid", "france-3", 0, Genre.SERIES),
            entry("y", "Koh-Lanta", "tf1", 0, Genre.ENTERTAINMENT),
        )
        // Every Chaîne: the duplicate is kept once (first Chaîne wins), other Genres are out.
        val all = GenreGrid.arrange(items, Genre.SERIES, channelIds = null)
        assertThat(all.map { it.show.id.value }).containsExactly("x", "t").inOrder()
        // France TV only: its own copy of the duplicate survives the filter.
        val ftv = GenreGrid.arrange(items, Genre.SERIES, channelIds = setOf("france-2", "france-3"))
        assertThat(ftv.map { it.show.id.value }).containsExactly("x", "f").inOrder()
    }

    @Test
    fun storeReadsTheCacheOnly() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("k", "Kids", Genre.YOUTH, Genre.SERIES), show("s", "Série", Genre.SERIES)), 0)
            dao.putListing("france-2", listOf(show("p", "Plus belle la vie", Genre.SERIES)), day)
            val provider = FakeProvider()
            val catalog = CatalogStore(dao)

            val grid = catalog.genreGrid(Genre.SERIES, listOf("tf1", "france-2"))

            // Newest day first; Jeunesse stays exclusive (Genres.canonical) on this read too.
            assertThat(grid.map { it.show.id.value }).containsExactly("p", "s").inOrder()
            assertThat(grid.map { it.channelId }).containsExactly("france-2", "tf1").inOrder()
            assertThat(catalog.genreGrid(Genre.SERIES, listOf("tf1")).map { it.show.id.value }).containsExactly("s")
            assertThat(provider.showsCalls).isEqualTo(0)
        }
    }

    @Test
    fun firstSeenSurvivesARefresh() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            dao.putListing("tf1", listOf(show("old", "Zèbre", Genre.SERIES)), 0)
            dao.putListing("tf1", listOf(show("new", "Abeille", Genre.SERIES), show("old", "Zèbre", Genre.SERIES)), day)
            val grid = catalog.genreGrid(Genre.SERIES, listOf("tf1"))
            assertThat(grid.map { it.show.id.value }).containsExactly("new", "old").inOrder()
        }
    }
}
