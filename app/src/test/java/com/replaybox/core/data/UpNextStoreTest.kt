package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream
import com.replaybox.core.model.StreamType
import com.replaybox.core.model.UpNextItem
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.delay
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * CatalogStore paths of M4 « À suivre » on the fake DAO: Épisode suivant across Saisons, the
 * Programme hero, the Home rail and its dismissals, the pinned refresh. No network.
 */
class UpNextStoreTest {

    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private val hour = 3_600_000L

    /** Saisons per Programme and Épisodes per Saison; counts calls and concurrency. */
    private class SeasonedProvider(
        var seasonsByShow: Map<ShowId, List<Season>> = emptyMap(),
        var episodesBySeason: Map<SeasonId, List<Episode>> = emptyMap(),
        private val failing: Set<ShowId> = emptySet(),
        private val delayMs: Long = 0,
    ) : Provider {
        override val channel = Channel("tf1", "TF1")
        var seasonsCalls = 0
        var episodesCalls = 0
        private var running = 0
        var maxRunning = 0

        override suspend fun shows(genre: Genre?): List<Show> = emptyList()

        override suspend fun seasons(show: ShowId): List<Season> {
            seasonsCalls += 1
            running += 1
            maxRunning = maxOf(maxRunning, running)
            try {
                delay(delayMs)
                if (show in failing) throw IOException("down")
                return seasonsByShow[show].orEmpty()
            } finally {
                running -= 1
            }
        }

        override suspend fun episodes(season: SeasonId?): List<Episode> {
            episodesCalls += 1
            return episodesBySeason[season].orEmpty()
        }

        override suspend fun search(query: String): List<Show> = emptyList()
        override suspend fun stream(episode: EpisodeId): Stream = Stream("https://cdn/x.mpd", StreamType.DASH)
    }

    private fun season(show: String, n: Int?) =
        Season(SeasonId("$show::${n ?: ""}"), ShowId(show), n, n?.let { "Saison $it" } ?: "Divers")

    private fun ep(id: String, season: Season, number: Int? = null, broadcastAt: Instant? = null) =
        Episode(EpisodeId(id), season.id, id, null, null, 3000, null, broadcastAt = broadcastAt, number = number)

    // Profilage: listed newest Saison first, newest Épisode first (TF1).
    private val p1 = season("profilage", 1)
    private val p2 = season("profilage", 2)
    private val profilageSeasons = listOf(p2, p1)
    private val profilageEpisodes = mapOf(
        p1.id to listOf(ep("p-s1e2", p1, 2), ep("p-s1e1", p1, 1)),
        p2.id to listOf(ep("p-s2e2", p2, 2), ep("p-s2e1", p2, 1)),
    )

    private fun provider() = SeasonedProvider(mapOf(ShowId("profilage") to profilageSeasons), profilageEpisodes)

    private fun store(dao: FakeCatalogDao) = CatalogStore(dao, clock = { now.toEpochMilli() })

    private fun context(show: String, episode: String) = ProgressContext(ShowId(show), show, episode, null)

    // ---- Épisode suivant ----

    @Test
    fun `next Episode follows the play order within the Saison`() {
        blocking {
            val next = store(FakeCatalogDao()).nextEpisode(provider(), ShowId("profilage"), EpisodeId("p-s1e1"))
            assertThat(next?.episode?.id?.value).isEqualTo("p-s1e2")
            assertThat(next?.season).isEqualTo(p1)
        }
    }

    @Test
    fun `after the last Episode of a Saison, the first of the next Saison, loaded and cached`() {
        blocking {
            val dao = FakeCatalogDao()
            val next = store(dao).nextEpisode(provider(), ShowId("profilage"), EpisodeId("p-s1e2"))
            assertThat(next?.episode?.id?.value).isEqualTo("p-s2e1")
            assertThat(next?.season?.number).isEqualTo(2)
            assertThat(dao.episodes(p2.id.value)).isNotEmpty()
        }
    }

    @Test
    fun `no next Episode after the last Saison or in an unnumbered Saison`() {
        blocking {
            assertThat(store(FakeCatalogDao()).nextEpisode(provider(), ShowId("profilage"), EpisodeId("p-s2e2"))).isNull()
            val magazine = season("jt", null)
            val jt = SeasonedProvider(
                mapOf(ShowId("jt") to listOf(magazine)),
                mapOf(magazine.id to listOf(ep("jt-new", magazine), ep("jt-old", magazine))),
            )
            assertThat(store(FakeCatalogDao()).nextEpisode(jt, ShowId("jt"), EpisodeId("jt-new"))).isNull()
        }
    }

    // ---- Programme hero ----

    @Test
    fun `hero without history plays the first Episode of the first Saison`() {
        blocking {
            val view = store(FakeCatalogDao()).programmeView(provider(), ShowId("profilage"), profilageSeasons, emptyList())
            val target = view.target as HeroTarget.Play
            assertThat(target.episode.id.value).isEqualTo("p-s1e1")
            assertThat(target.season).isEqualTo(p1)
            assertThat(view.watchedSeasons).isEmpty()
        }
    }

    @Test
    fun `hero plays the next unwatched Episode across Saisons and checks the watched Saison`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = store(dao)
            catalog.markWatched(EpisodeId("p-s1e1"), 3000, context("profilage", "p-s1e1"))
            catalog.markWatched(EpisodeId("p-s1e2"), 3000, context("profilage", "p-s1e2"))
            val view = catalog.programmeView(provider(), ShowId("profilage"), profilageSeasons, emptyList())
            val target = view.target as HeroTarget.Play
            assertThat(target.episode.id.value).isEqualTo("p-s2e1")
            assertThat(target.season).isEqualTo(p2)
            assertThat(view.watchedSeasons).containsExactly(p1.id)
        }
    }

    @Test
    fun `hero resumes the Episode in progress, in its Saison`() {
        blocking {
            val catalog = store(FakeCatalogDao())
            catalog.saveProgress(EpisodeId("p-s2e1"), 600, 3000, context("profilage", "p-s2e1"))
            val view = catalog.programmeView(provider(), ShowId("profilage"), profilageSeasons, emptyList())
            assertThat(view.target).isInstanceOf(HeroTarget.Resume::class.java)
            assertThat(view.target?.season).isEqualTo(p2)
        }
    }

    // ---- « À suivre » ----

    private fun pinnedDao(vararg shows: String): FakeCatalogDao {
        val dao = FakeCatalogDao()
        blocking {
            dao.putListing("tf1", shows.map { show(it, it.replaceFirstChar(Char::uppercase)) }, 0)
            shows.forEachIndexed { index, id -> dao.putFavorite(id, index.toLong()) }
            dao.putSeasons("profilage", profilageSeasons, now.toEpochMilli())
            for ((seasonId, listed) in profilageEpisodes) dao.putEpisodes(seasonId.value, listed, now.toEpochMilli())
        }
        return dao
    }

    private val jt = season("jt", null)

    private fun putJt(dao: FakeCatalogDao, vararg episodes: Episode) = blocking {
        dao.putSeasons("jt", listOf(jt), now.toEpochMilli())
        dao.putEpisodes(jt.id.value, episodes.toList(), now.toEpochMilli())
    }

    @Test
    fun `a pinned series offers its next Episode once one was watched`() {
        blocking {
            val dao = pinnedDao("profilage")
            val catalog = store(dao)
            assertThat(catalog.upNext(listOf("tf1"))).isEmpty()
            catalog.markWatched(EpisodeId("p-s1e2"), 3000, context("profilage", "p-s1e2"))
            val item = catalog.upNext(listOf("tf1")).single() as UpNextItem.NewEpisode
            assertThat(item.episodeId.value).isEqualTo("p-s2e1")
            assertThat(item.seasonNumber).isEqualTo(2)
            assertThat(item.showTitle).isEqualTo("Profilage")
            assertThat(item.channelId).isEqualTo("tf1")
        }
    }

    @Test
    fun `in progress wins over the new Episode of the same Programme, newest event first`() {
        blocking {
            val dao = pinnedDao("profilage", "jt")
            putJt(dao, ep("jt-1", jt, broadcastAt = now.minus(Duration.ofHours(30))))
            val catalog = store(dao)
            catalog.markWatched(EpisodeId("p-s1e2"), 3000, context("profilage", "p-s1e2"))
            catalog.saveProgress(EpisodeId("p-s2e1"), 600, 3000, context("profilage", "p-s2e1"))
            val rail = catalog.upNext(listOf("tf1"))
            assertThat(rail.map { it.episodeId.value }).containsExactly("p-s2e1", "jt-1").inOrder()
            assertThat(rail.first()).isInstanceOf(UpNextItem.InProgress::class.java)
        }
    }

    @Test
    fun `a pinned magazine offers its newest unwatched Episode of the week only`() {
        blocking {
            val dao = pinnedDao("jt")
            putJt(
                dao,
                ep("jt-old", jt, broadcastAt = now.minus(Duration.ofDays(9))),
                ep("jt-mon", jt, broadcastAt = now.minus(Duration.ofDays(4))),
            )
            assertThat(store(dao).upNext(listOf("tf1")).map { it.episodeId.value }).containsExactly("jt-mon")
            // A Programme no longer listed on an active Chaîne is left out.
            assertThat(store(dao).upNext(listOf("tmc"))).isEmpty()
        }
    }

    @Test
    fun `a dismissed new Episode stays hidden until a newer one appears`() {
        blocking {
            val dao = pinnedDao("jt")
            putJt(dao, ep("jt-mon", jt, broadcastAt = now.minus(Duration.ofDays(4))))
            val catalog = store(dao)
            catalog.removeFromUpNext(catalog.upNext(listOf("tf1")).single())
            assertThat(catalog.upNext(listOf("tf1"))).isEmpty()
            putJt(
                dao,
                ep("jt-tue", jt, broadcastAt = now.minus(Duration.ofDays(3))),
                ep("jt-mon", jt, broadcastAt = now.minus(Duration.ofDays(4))),
            )
            assertThat(catalog.upNext(listOf("tf1")).map { it.episodeId.value }).containsExactly("jt-tue")
        }
    }

    @Test
    fun `removing an Episode in progress of a pinned series does not offer it back`() {
        blocking {
            val dao = pinnedDao("profilage")
            val catalog = store(dao)
            catalog.markWatched(EpisodeId("p-s1e2"), 3000, context("profilage", "p-s1e2"))
            catalog.saveProgress(EpisodeId("p-s2e1"), 600, 3000, context("profilage", "p-s2e1"))
            catalog.removeFromUpNext(catalog.upNext(listOf("tf1")).single())
            assertThat(catalog.upNext(listOf("tf1"))).isEmpty()
            assertThat(catalog.resumeAtFor(EpisodeId("p-s2e1"))).isNull()
        }
    }

    // ---- Pinned refresh ----

    @Test
    fun `refresh fetches only stale pinned Programmes and keeps the cache on failure`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("profilage", "Profilage"), show("tracker", "Tracker"), show("soeurs", "Soeurs")), 0)
            listOf("profilage", "tracker", "soeurs").forEach { dao.putFavorite(it, 0) }
            val fresh = now.toEpochMilli() - hour
            dao.putSeasons("tracker", listOf(season("tracker", 1)), fresh)
            val oldSoeurs = listOf(season("soeurs", 1))
            dao.putSeasons("soeurs", oldSoeurs, now.toEpochMilli() - CatalogStore.TTL_MS - 1)
            val network = SeasonedProvider(
                mapOf(ShowId("profilage") to profilageSeasons),
                profilageEpisodes,
                failing = setOf(ShowId("soeurs")),
            )
            val catalog = store(dao)
            val refreshed = catalog.refreshPinned(listOf("tf1"), { network })
            assertThat(refreshed).isEqualTo(1)
            assertThat(network.seasonsCalls).isEqualTo(2) // profilage (never cached) and soeurs (stale)
            assertThat(dao.seasons("profilage")).isEqualTo(profilageSeasons)
            assertThat(dao.episodes(p2.id.value).map { it.id.value }).containsExactly("p-s2e2", "p-s2e1")
            assertThat(dao.seasons("soeurs")).isEqualTo(oldSoeurs)
            assertThat(catalog.updates.value).isEqualTo(1)
        }
    }

    @Test
    fun `refresh runs at most three Programmes at once`() {
        blocking {
            val dao = FakeCatalogDao()
            val ids = (1..7).map { "show$it" }
            dao.putListing("tf1", ids.map { show(it, it) }, 0)
            ids.forEach { dao.putFavorite(it, 0) }
            val network = SeasonedProvider(delayMs = 20)
            store(dao).refreshPinned(listOf("tf1"), { network })
            assertThat(network.seasonsCalls).isEqualTo(7)
            assertThat(network.maxRunning).isEqualTo(3)
        }
    }
}
