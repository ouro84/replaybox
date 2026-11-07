package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Expired Épisodes leave Reprendre and the Programme rows; « Marquer comme vu / non vu ». */
class WatchedExpiryTest {

    private val now: Instant = Instant.parse("2026-11-07T08:00:00Z")

    private fun context(show: String, episode: String) =
        ProgressContext(ShowId(show), show.uppercase(), episode, "https://img/$episode.jpg")

    private fun cached(id: String, season: String = "s1", until: Instant? = null, duration: Int? = null) =
        episode(id, season).copy(availableUntil = until, durationSeconds = duration)

    private fun store(dao: FakeCatalogDao) = CatalogStore(dao) { now.toEpochMilli() }

    // ---- Expiry on Reprendre ----

    @Test
    fun `a Reprendre card carries the cached end of availability`() {
        blocking {
            val dao = FakeCatalogDao()
            val until = now.plus(Duration.ofDays(2))
            dao.putEpisodes("s1", listOf(cached("e1", until = until)), now.toEpochMilli())
            val catalog = store(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))

            assertThat(catalog.continueWatching(emptyList()).single().availableUntil).isEqualTo(until)
        }
    }

    @Test
    fun `uncachedEpisodeStaysWithoutExpiry`() {
        blocking {
            val catalog = store(FakeCatalogDao())
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))

            val item = catalog.continueWatching(emptyList()).single()
            assertThat(item.availableUntil).isNull()
        }
    }

    @Test
    fun `an expired Episode leaves Reprendre but keeps its Progression`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putEpisodes("s1", listOf(cached("e1", until = now.minusSeconds(60))), now.toEpochMilli())
            val catalog = store(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))

            assertThat(catalog.continueWatching(emptyList())).isEmpty()
            assertThat(catalog.resumeFor(ShowId("koh"), emptyList())).isNull()
            assertThat(dao.progress("e1")).isNotNull()
        }
    }

    @Test
    fun `an expired newest Episode does not hide an older one of its Programme`() {
        blocking {
            var clock = now.toEpochMilli()
            val dao = FakeCatalogDao()
            dao.putEpisodes("s1", listOf(cached("e1"), cached("e2", until = now.minusSeconds(60))), clock)
            val catalog = CatalogStore(dao) { clock }
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))
            clock += 10
            catalog.saveProgress(EpisodeId("e2"), 600, 3600, context("koh", "e2"))

            assertThat(catalog.continueWatching(emptyList()).map { it.episodeId.value }).containsExactly("e1")
            assertThat(catalog.resumeFor(ShowId("koh"), emptyList())?.episodeId?.value).isEqualTo("e1")
        }
    }

    // ---- Expiry on the Programme rows ----

    @Test
    fun `cached expired Episodes are not listed`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putEpisodes(
                "s1",
                listOf(cached("gone", until = now.minusSeconds(1)), cached("live", until = now.plusSeconds(3600)), cached("open")),
                now.toEpochMilli(),
            )
            val listed = store(dao).episodes(FakeProvider(), SeasonId("s1"))

            assertThat(listed.map { it.id.value }).containsExactly("live", "open").inOrder()
        }
    }

    @Test
    fun `fetched expired Episodes are not listed`() {
        blocking {
            val provider = FakeProvider(episodesResult = listOf(cached("gone", until = now.minusSeconds(1)), cached("live")))
            val listed = store(FakeCatalogDao()).episodes(provider, SeasonId("s1"))

            assertThat(listed.map { it.id.value }).containsExactly("live")
        }
    }

    // ---- Marquer comme vu / non vu ----

    @Test
    fun `marking watched stores 100 percent and leaves Reprendre`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = store(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, 3600, context("koh", "e1"))

            catalog.markWatched(EpisodeId("e1"), 3600)

            val row = dao.progress("e1")!!
            assertThat(row.positionSeconds).isEqualTo(3600)
            assertThat(row.showId).isEqualTo("koh")
            assertThat(catalog.continueWatching(emptyList())).isEmpty()
            assertThat(catalog.episodeProgress(listOf(EpisodeId("e1"))).getValue(EpisodeId("e1")).watched).isTrue()
        }
    }

    @Test
    fun `marking an unstarted Episode watched records its Programme context`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = store(dao)

            catalog.markWatched(EpisodeId("e1"), 1800, context("koh", "e1"))

            val row = dao.progress("e1")!!
            assertThat(row.positionSeconds).isEqualTo(1800)
            assertThat(row.durationSeconds).isEqualTo(1800)
            assertThat(row.showId).isEqualTo("koh")
            assertThat(row.updatedAt).isEqualTo(now.toEpochMilli())
        }
    }

    @Test
    fun `an unknown duration falls back to the saved row then the cached Episode`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putEpisodes("s1", listOf(cached("e2", duration = 2400)), now.toEpochMilli())
            val catalog = store(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, 3000, context("koh", "e1"))

            catalog.markWatched(EpisodeId("e1"), null)
            catalog.markWatched(EpisodeId("e2"), null, context("koh", "e2"))

            assertThat(dao.progress("e1")!!.positionSeconds).isEqualTo(3000)
            assertThat(dao.progress("e2")!!.positionSeconds).isEqualTo(2400)
        }
    }

    @Test
    fun `without any duration the row still reads watched`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = store(dao)
            catalog.saveProgress(EpisodeId("e1"), 600, null, context("koh", "e1"))
            catalog.markWatched(EpisodeId("e2"), null, context("koh", "e2"))
            catalog.markWatched(EpisodeId("e1"), null)

            val progress = catalog.episodeProgress(listOf(EpisodeId("e1"), EpisodeId("e2")))
            assertThat(progress.getValue(EpisodeId("e1")).watched).isTrue()
            assertThat(progress.getValue(EpisodeId("e2")).watched).isTrue()
            assertThat(catalog.continueWatching(emptyList())).isEmpty()
            assertThat(catalog.resumeAtFor(EpisodeId("e1"))).isNull()
        }
    }

    @Test
    fun `marking unwatched forgets the Progression`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = store(dao)
            catalog.markWatched(EpisodeId("e1"), 1800, context("koh", "e1"))
            val before = catalog.updates.value

            catalog.markUnwatched(EpisodeId("e1"))

            assertThat(dao.progress("e1")).isNull()
            assertThat(catalog.episodeProgress(listOf(EpisodeId("e1")))).isEmpty()
            assertThat(catalog.updates.value).isGreaterThan(before)
        }
    }
}
