package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ShowId
import org.junit.Test

/** Home rails: "Reprendre" from local Progression, Genre rails from the cache. */
class HomeRailsTest {

    private fun context(show: String, episode: String) =
        ProgressContext(ShowId(show), show.uppercase(), episode, "https://img/$episode.jpg")

    @Test
    fun `continue watching keeps the latest unfinished Episode per Programme`() {
        blocking {
            var now = 1_000L
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao) { now }
            dao.putListing("tf1", listOf(show("koh", "Koh-Lanta").copy(backdropUrl = "https://img/koh-bg.jpg")), 0)

            catalog.saveProgress(EpisodeId("koh-e1"), 600, 3600, context("koh", "e1"))
            now += 10
            catalog.saveProgress(EpisodeId("koh-e2"), 900, 3600, context("koh", "e2"))
            now += 10
            catalog.saveProgress(EpisodeId("wag-e1"), 1200, 5400, context("wagner", "w1"))
            now += 10
            // Finished: leaves the rail.
            catalog.saveProgress(EpisodeId("fin-e1"), 3500, 3600, context("finished", "f1"))
            // Saved before schema v2, no Programme context: stays out.
            dao.putProgress(ProgressEntity("legacy", 600, 3600, now + 1))

            val items = catalog.continueWatching(listOf("tf1"))
            assertThat(items.map { it.episodeId.value }).containsExactly("wag-e1", "koh-e2").inOrder()
            val koh = items.last()
            assertThat(koh.channelId).isEqualTo("tf1")
            assertThat(koh.backdropUrl).isEqualTo("https://img/koh-bg.jpg")
            assertThat(koh.thumbnailUrl).isEqualTo("https://img/e2.jpg")
            assertThat(koh.remainingSeconds).isEqualTo(2700)
        }
    }

    @Test
    fun `a context-less save keeps the recorded Programme context`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))
            catalog.saveProgress(EpisodeId("e1"), 700, 3600)
            val row = dao.progress("e1")!!
            assertThat(row.positionSeconds).isEqualTo(700)
            assertThat(row.showId).isEqualTo("koh")
            assertThat(row.episodeTitle).isEqualTo("e1")
        }
    }

    @Test
    fun `removing from continue watching forgets the playhead`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))
            catalog.removeFromContinueWatching(EpisodeId("e1"))
            assertThat(catalog.continueWatching(emptyList())).isEmpty()
            assertThat(catalog.resumeAtFor(EpisodeId("e1"))).isNull()
        }
    }

    @Test
    fun `genre rails interleave Chaines in glossary order and merge duplicates`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            dao.putListing(
                "tf1",
                listOf(
                    show("t1", "Balthazar", Genre.CRIME_THRILLER, Genre.SERIES),
                    show("t2", "HPI", Genre.CRIME_THRILLER),
                    show("t3", "Koh-Lanta", Genre.ENTERTAINMENT),
                ),
                0,
            )
            dao.putListing(
                "france-2",
                listOf(
                    show("f1", "Astrid et Raphaëlle", Genre.CRIME_THRILLER),
                    show("f2", "Hpi", Genre.CRIME_THRILLER),
                ),
                0,
            )

            val rails = catalog.genreRails(listOf("tf1", "france-2"))
            assertThat(rails.map { it.first })
                .containsExactly(Genre.SERIES, Genre.CRIME_THRILLER, Genre.ENTERTAINMENT).inOrder()
            val crime = rails.first { it.first == Genre.CRIME_THRILLER }.second
            assertThat(crime.map { it.show.id.value }).containsExactly("t1", "f1", "t2").inOrder()
            assertThat(crime.map { it.channelId }).containsExactly("tf1", "france-2", "tf1").inOrder()
        }
    }

    @Test
    fun `genre rails keep their order when a Chaine reshuffles its listing`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            val a = show("a", "Alpha", Genre.SERIES)
            val b = show("b", "Beta", Genre.SERIES)
            val c = show("c", "Gamma", Genre.SERIES)
            dao.putListing("tf1", listOf(a, b, c), 0)
            // Same evening, editorial reshuffle plus a newcomer.
            dao.putListing("tf1", listOf(c, show("d", "Delta", Genre.SERIES), a, b), 1_000)
            val sameDay = catalog.genreRails(listOf("tf1")).single().second.map { it.show.id.value }
            assertThat(sameDay).containsExactly("a", "b", "c", "d").inOrder()
            // Next day: the newcomer of that day leads, the rest stays put.
            dao.putListing("tf1", listOf(show("e", "Epsilon", Genre.SERIES), b, a), 86_400_000L + 5)
            val nextDay = catalog.genreRails(listOf("tf1")).single().second.map { it.show.id.value }
            assertThat(nextDay).containsExactly("e", "a", "b").inOrder()
        }
    }

    @Test
    fun `favorites carry their Chaine`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            dao.putListing("france-3", listOf(show("r", "Des Racines et des Ailes")), 0)
            catalog.toggleFavorite(ShowId("r"))
            val favorites = catalog.favoriteChannelShows(listOf("tf1", "france-3"))
            assertThat(favorites.single().channelId).isEqualTo("france-3")
        }
    }

    @Test
    fun `a newer finished Episode does not hide an older unfinished one`() {
        blocking {
            var now = 1_000L
            val catalog = CatalogStore(FakeCatalogDao()) { now }
            catalog.saveProgress(EpisodeId("pitch-nov"), 45, 239, context("pitch", "nov"))
            now += 10
            // Finished afterwards: the Programme stays in Reprendre on the unfinished one.
            catalog.saveProgress(EpisodeId("pitch-oct"), 239, 239, context("pitch", "oct"))

            val items = catalog.continueWatching(listOf("france-2"))
            assertThat(items.map { it.episodeId.value }).containsExactly("pitch-nov")
        }
    }
}
