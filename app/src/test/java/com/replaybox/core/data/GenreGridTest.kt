package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import org.junit.Test
import java.time.Instant

/**
 * « Tout voir » grid: recency (Show.recencyAt, newcomers by first-seen day) over the last
 * 30 days by Paris day, then title, across Chaînes, from the cache only.
 */
class GenreGridTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L

    /** Saturday 2026-11-07, 17:30 in Paris. */
    private val now = Instant.parse("2026-11-07T16:30:00Z").toEpochMilli()

    private fun entry(
        id: String,
        title: String,
        channel: String,
        recencyAt: Long? = null,
        firstSeenAt: Long = 0,
        vararg genres: Genre,
    ) = ChannelShow(show(id, title, *genres).copy(recencyAt = recencyAt, firstSeenAt = firstSeenAt), channel)

    private fun ids(items: List<ChannelShow>) = items.map { it.show.id.value }

    @Test
    fun recentDayFirstThenTitleThenUndated() {
        val items = listOf(
            entry("a", "Zorro", "tf1"),
            entry("b", "Astrid", "france-2"),
            entry("c", "Mortel", "m6", recencyAt = now - 2 * hour),
            entry("d", "Été indien", "tf1", recencyAt = now - day),
            entry("e", "Balthazar", "tf1", recencyAt = now - hour),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("e", "c", "d", "b", "a").inOrder()
    }

    @Test
    fun sameParisDayIgnoresTheInstant() {
        // One refresh writes the Chaînes seconds apart: same day, so title decides.
        val items = listOf(
            entry("late", "Beta", "m6", recencyAt = now),
            entry("early", "Alpha", "tf1", recencyAt = now - 10 * hour),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("early", "late").inOrder()
    }

    @Test
    fun daysAreParisDaysNotUtcDays() {
        // 23:30 UTC on Friday is 00:30 on Saturday in Paris: same day as Saturday noon.
        val friday2330Utc = Instant.parse("2026-11-06T23:30:00Z").toEpochMilli()
        val saturdayNoon = Instant.parse("2026-11-07T11:00:00Z").toEpochMilli()
        val fridayNoon = Instant.parse("2026-11-06T11:00:00Z").toEpochMilli()
        val items = listOf(
            entry("fri", "Aaa", "m6", recencyAt = fridayNoon),
            entry("night", "Zzz", "m6", recencyAt = friday2330Utc),
            entry("sat", "Mmm", "tf1", recencyAt = saturdayNoon),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("sat", "night", "fri").inOrder()
    }

    @Test
    fun olderThanTheWindowRanksByTitleWithTheUndated() {
        val items = listOf(
            entry("old", "Noël sur la 5e avenue", "m6", recencyAt = now - 300 * day),
            entry("undated", "Astrid", "france-2"),
            entry("edge", "Zèbre", "m6", recencyAt = now - GenreGrid.RECENT_DAYS * day),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("edge", "undated", "old").inOrder()
    }

    @Test
    fun aDateAheadCountsAsToday() {
        val items = listOf(
            entry("ahead", "Zorro", "france-2", recencyAt = now + 3 * day),
            entry("today", "Alpha", "m6", recencyAt = now - hour),
            entry("yesterday", "Beta", "m6", recencyAt = now - day),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("today", "ahead", "yesterday").inOrder()
    }

    @Test
    fun theFirstListingSaysNothing() {
        // Fresh install: every Programme shares the first-seen instant, so only titles order them.
        val installed = now - hour
        val items = listOf(
            entry("z", "Zorro", "tf1", firstSeenAt = installed),
            entry("a", "Astrid", "france-2", firstSeenAt = installed + 5_000),
            entry("m", "Mortel", "m6", recencyAt = now - 3 * day, firstSeenAt = installed + 9_000),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("m", "a", "z").inOrder()
    }

    @Test
    fun firstSeenNeverRanks() {
        // Seen on the Bbox: a few Programmes listed the day before made the whole Index
        // « new today »; undated Programmes now always follow the dated ones, by title.
        val installed = now - 10 * day
        val items = listOf(
            entry("philo", "#Philo", "tf1", firstSeenAt = now - hour),
            entry("old", "Astrid", "tf1", firstSeenAt = installed),
            entry("dated", "Un baiser pour Noël", "tf1", recencyAt = now - hour, firstSeenAt = now - hour),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("dated", "philo", "old").inOrder()
    }

    @Test
    fun titleOrderIgnoresCaseAndAccents() {
        val items = listOf(
            entry("1", "zone interdite", "m6"),
            entry("2", "Éléphant", "tf1"),
            entry("3", "Alpha", "tf1"),
        )
        assertThat(ids(GenreGrid.sort(items, now))).containsExactly("3", "2", "1").inOrder()
    }

    @Test
    fun recencyMergeKeepsTheLatest() {
        assertThat(GenreGrid.mergeRecency(null, null)).isNull()
        assertThat(GenreGrid.mergeRecency(5, null)).isEqualTo(5)
        assertThat(GenreGrid.mergeRecency(null, 7)).isEqualTo(7)
        assertThat(GenreGrid.mergeRecency(9, 7)).isEqualTo(9)
        assertThat(GenreGrid.mergeRecency(7, 9)).isEqualTo(9)
    }

    @Test
    fun filterThenDedupe() {
        val items = listOf(
            entry("t", "HPI", "tf1", genres = arrayOf(Genre.SERIES)),
            entry("f", "Hpi", "france-2", genres = arrayOf(Genre.SERIES)),
            entry("x", "Astrid", "france-3", genres = arrayOf(Genre.SERIES)),
            entry("y", "Koh-Lanta", "tf1", genres = arrayOf(Genre.ENTERTAINMENT)),
        )
        // Every Chaîne: the duplicate is kept once (first Chaîne wins), other Genres are out.
        val all = GenreGrid.arrange(items, Genre.SERIES, channelIds = null, now = now)
        assertThat(ids(all)).containsExactly("x", "t").inOrder()
        // France TV only: its own copy of the duplicate survives the filter.
        val ftv = GenreGrid.arrange(items, Genre.SERIES, channelIds = setOf("france-2", "france-3"), now = now)
        assertThat(ids(ftv)).containsExactly("x", "f").inOrder()
    }

    @Test
    fun storeReadsTheCacheOnly() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing(
                "tf1",
                listOf(
                    show("k", "Kids", Genre.YOUTH, Genre.SERIES).copy(recencyAt = now),
                    show("s", "Série", Genre.SERIES),
                ),
                now - 2 * day,
            )
            dao.putListing("france-2", listOf(show("p", "Plus belle la vie", Genre.SERIES).copy(recencyAt = now - day)), now - day)
            val provider = FakeProvider()
            val catalog = CatalogStore(dao, clock = { now })

            val grid = catalog.genreGrid(Genre.SERIES, listOf("tf1", "france-2"))

            // Most recent first; Jeunesse stays exclusive (Genres.canonical) on this read too.
            assertThat(ids(grid)).containsExactly("p", "s").inOrder()
            assertThat(grid.map { it.channelId }).containsExactly("france-2", "tf1").inOrder()
            assertThat(ids(catalog.genreGrid(Genre.SERIES, listOf("tf1")))).containsExactly("s")
            assertThat(provider.showsCalls).isEqualTo(0)
        }
    }

    @Test
    fun recencySurvivesARefreshThatOmitsIt() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao, clock = { now })
            // TF1 counted recent Épisodes yesterday, none today: yesterday's date stays.
            dao.putListing(
                "tf1",
                listOf(show("koh", "Koh-Lanta", Genre.SERIES).copy(recencyAt = now - day), show("abc", "Abeille", Genre.SERIES)),
                now - day,
            )
            dao.putListing("tf1", listOf(show("koh", "Koh-Lanta", Genre.SERIES), show("abc", "Abeille", Genre.SERIES)), now)
            val grid = catalog.genreGrid(Genre.SERIES, listOf("tf1"))
            assertThat(ids(grid)).containsExactly("koh", "abc").inOrder()
            assertThat(grid.first().show.recencyAt).isEqualTo(now - day)
        }
    }

    @Test
    fun undatedProgrammesKeepTitleOrderAcrossRefreshes() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao, clock = { now })
            dao.putListing("france-2", listOf(show("old", "Abeille", Genre.SERIES)), now - 3 * day)
            dao.putListing("france-2", listOf(show("new", "Zèbre", Genre.SERIES), show("old", "Abeille", Genre.SERIES)), now - day)
            dao.putListing("france-2", listOf(show("old", "Abeille", Genre.SERIES), show("new", "Zèbre", Genre.SERIES)), now)
            val grid = catalog.genreGrid(Genre.SERIES, listOf("france-2"))
            assertThat(ids(grid)).containsExactly("old", "new").inOrder()
        }
    }
}
