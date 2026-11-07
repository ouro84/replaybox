package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import org.junit.Test
import java.io.IOException

/**
 * ADR 0002 / SPEC §Cache: TTL 12 h, background refresh, degraded mode serves
 * the stale cache with its date; Flux stays live (CachedProvider pass-through).
 */
class CatalogStoreTest {

    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    private fun store(dao: CatalogDao) = CatalogStore(dao, clock = { now })

    @Test
    fun `fresh cache serves without touching the network`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("ch", listOf(show("s1", "Alpha", Genre.SERIES)), now - 1 * hour)
            val provider = FakeProvider(Channel("ch", "Test"), showsResult = listOf(show("s2", "Beta")))
            val result = store(dao).shows(provider, null)
            assertThat(result.map { it.id.value }).containsExactly("s1")
            assertThat(provider.showsCalls).isEqualTo(0)
        }
    }

    @Test
    fun `stale cache refreshes from the provider and stores`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("ch", listOf(show("s1", "Alpha")), now - CatalogStore.TTL_MS - 1)
            val provider = FakeProvider(Channel("ch", "Test"), showsResult = listOf(show("s2", "Beta", Genre.COMEDY)))
            val catalog = store(dao)
            val result = catalog.shows(provider, null)
            assertThat(result.map { it.id.value }).containsExactly("s2")
            assertThat(dao.shows("ch").map { it.id.value }).containsExactly("s2")
            assertThat(dao.listingAt("ch")).isEqualTo(now)
            assertThat(catalog.updates.value).isEqualTo(1)
        }
    }

    @Test
    fun `degraded mode serves stale cache when the provider is down`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("ch", listOf(show("s1", "Alpha")), now - CatalogStore.TTL_MS - 1)
            val provider = FakeProvider(Channel("ch", "Test"), showsError = IOException("network down"))
            val result = store(dao).shows(provider, null)
            assertThat(result.map { it.id.value }).containsExactly("s1")
        }
    }

    @Test
    fun `provider failure without cache propagates`() {
        blocking {
            val dao = FakeCatalogDao()
            val provider = FakeProvider(Channel("ch", "Test"), showsError = IOException("network down"))
            val error = runCatching { store(dao).shows(provider, null) }
            assertThat(error.exceptionOrNull()).isInstanceOf(IOException::class.java)
        }
    }

    @Test
    fun `genre filter applies to cached and fresh listings`() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing(
                "ch",
                listOf(
                    show("s1", "Alpha", Genre.SERIES),
                    show("s2", "Beta", Genre.COMEDY),
                ),
                now - 1 * hour,
            )
            val catalog = store(dao)
            val filtered = catalog.shows(FakeProvider(Channel("ch", "Test")), Genre.SERIES)
            assertThat(filtered.map { it.id.value }).containsExactly("s1")
            val fresh = listOf(
                show("s3", "Gamma", Genre.SERIES),
                show("s4", "Delta", Genre.COMEDY),
            )
            val refreshed = FakeProvider(Channel("other", "Test"), showsResult = fresh)
            val filteredFresh = catalog.shows(refreshed, Genre.COMEDY)
            assertThat(filteredFresh.map { it.id.value }).containsExactly("s4")
        }
    }

    @Test
    fun `seasons and episodes refresh per key and degrade to cache`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = store(dao)
            val provider = FakeProvider(
                Channel("ch", "Test"),
                seasonsResult = listOf(season("show1::1")),
                episodesResult = listOf(episode("ep1", "show1::1")),
            )
            val seasons = catalog.seasons(provider, ShowId("show1")).map { it.id.value }
            assertThat(seasons).containsExactly("show1::1")
            val episodes = catalog.episodes(provider, SeasonId("show1::1")).map { it.id.value }
            assertThat(episodes).containsExactly("ep1")
            assertThat(provider.seasonsCalls).isEqualTo(1)
            assertThat(provider.episodesCalls).isEqualTo(1)

            // Fresh keys: served from cache, no network.
            val cachedSeasons = catalog.seasons(provider, ShowId("show1")).map { it.id.value }
            assertThat(cachedSeasons).containsExactly("show1::1")
            assertThat(provider.seasonsCalls).isEqualTo(1)

            // Stale keys with the provider down: degraded to the cache.
            val dao2 = FakeCatalogDao()
            dao2.putSeasons("show1", listOf(season("show1::1")), now - CatalogStore.TTL_MS - 1)
            dao2.putEpisodes("show1::1", listOf(episode("ep1", "show1::1")), now - CatalogStore.TTL_MS - 1)
            val down = FakeProvider(Channel("ch", "Test"), seasonsError = IOException("down"), episodesError = IOException("down"))
            assertThat(store(dao2).seasons(down, ShowId("show1"))).isNotEmpty()
            assertThat(store(dao2).episodes(down, SeasonId("show1::1"))).isNotEmpty()
        }
    }

    @Test
    fun `episodes without season always go live`() {
        blocking {
            val dao = FakeCatalogDao()
            val provider = FakeProvider(Channel("ch", "Test"), episodesResult = listOf(episode("ep1", "s::1")))
            store(dao).episodes(provider, null)
            assertThat(provider.episodesCalls).isEqualTo(1)
        }
    }

    @Test
    fun `episodes of a season are listed by ascending number, fresh and cached`() {
        blocking {
            // TF1 lists a batch upload in no particular order (Profilage S2, 2026-11-07).
            fun numbered(id: String, number: Int?) = episode(id, "show1::2").copy(number = number)
            val shuffled = listOf(
                numbered("bonus", null),
                numbered("e3", 3),
                numbered("e1", 1),
                numbered("e2b", 2),
                numbered("e10", 10),
                numbered("e2a", 2),
                numbered("making-of", null),
            )
            val expected = listOf("e1", "e2b", "e2a", "e3", "e10", "bonus", "making-of")

            val provider = FakeProvider(Channel("ch", "Test"), episodesResult = shuffled)
            val fresh = store(FakeCatalogDao()).episodes(provider, SeasonId("show1::2"))
            assertThat(fresh.map { it.id.value }).containsExactlyElementsIn(expected).inOrder()

            // A cache filled before the fix is reordered on read.
            val dao = FakeCatalogDao()
            dao.putEpisodes("show1::2", shuffled, now - 1 * hour)
            val cached = store(dao).episodes(provider, SeasonId("show1::2"))
            assertThat(cached.map { it.id.value }).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `a season without numbers keeps the broadcaster order`() {
        blocking {
            val recentFirst = listOf("jt-07-11", "jt-06-11", "jt-05-11").map { episode(it, "show1::") }
            val provider = FakeProvider(Channel("ch", "Test"), episodesResult = recentFirst)
            val listed = store(FakeCatalogDao()).episodes(provider, SeasonId("show1::"))
            assertThat(listed.map { it.id.value }).containsExactly("jt-07-11", "jt-06-11", "jt-05-11").inOrder()
        }
    }

    private fun season(id: String) =
        Season(
            SeasonId(id),
            ShowId(id.substringBefore("::")),
            1,
            "Saison 1",
        )

    @Test
    fun failedCrawlKeepsOldListing() {
        blocking {
            val dao = FakeCatalogDao()
            val stale = now - CatalogStore.TTL_MS - 1
            dao.putListing("tf1", listOf(show("old", "Ancien", Genre.SERIES)), stale)
            val provider = FakeProvider(Channel("tf1", "TF1"), showsError = IOException("page 3 timed out"))
            val catalog = store(dao)

            assertThat(catalog.shows(provider, null).map { it.id.value }).containsExactly("old")
            assertThat(catalog.refreshListings(listOf(provider))).isEqualTo(1)
            assertThat(catalog.listingAt("tf1")).isEqualTo(stale)
            assertThat(catalog.genreRails(listOf("tf1")).single().shows.map { it.show.id.value })
                .containsExactly("old")
        }
    }

    @Test
    fun youthIsExclusiveInCachedListings() {
        blocking {
            // An Index stored before Jeunesse became exclusive: the rule applies on read,
            // without waiting for a crawl (the provider is never called).
            val dao = FakeCatalogDao()
            dao.putListing(
                "tfx",
                listOf(show("pokemon", "Pokémon", Genre.SERIES, Genre.YOUTH), show("hpi", "HPI", Genre.SERIES)),
                now - 1 * hour,
            )
            val provider = FakeProvider(Channel("tfx", "TFX"))
            val catalog = store(dao)

            val rails = catalog.genreRails(listOf("tfx")).associate { it.genre to it.shows }
            assertThat(rails.getValue(Genre.SERIES).map { it.show.id.value }).containsExactly("hpi")
            assertThat(rails.getValue(Genre.YOUTH).map { it.show.id.value }).containsExactly("pokemon")
            assertThat(catalog.shows(provider, Genre.SERIES).map { it.id.value }).containsExactly("hpi")
            assertThat(catalog.searchShows(listOf("tfx"), "poke").single().genres).containsExactly(Genre.YOUTH)
            assertThat(provider.showsCalls).isEqualTo(0)
        }
    }

    @Test
    fun youthIsExclusiveInFreshListings() {
        blocking {
            val dao = FakeCatalogDao()
            val provider = FakeProvider(
                Channel("m6", "M6"),
                showsResult = listOf(show("zig", "Zig et Sharko", Genre.SERIES, Genre.YOUTH)),
            )
            val catalog = store(dao)
            assertThat(catalog.shows(provider, Genre.SERIES)).isEmpty()
            assertThat(catalog.shows(provider, null).single().genres).containsExactly(Genre.YOUTH)
        }
    }

    @Test
    fun listingMemoInvalidatedOnWrite() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("a", "Alpha", Genre.SERIES)), now - 1 * hour)
            val catalog = store(dao)
            assertThat(catalog.genreRails(listOf("tf1")).single().shows.map { it.show.id.value })
                .containsExactly("a")

            val provider = FakeProvider(
                Channel("tf1", "TF1"),
                showsResult = listOf(show("a", "Alpha", Genre.SERIES), show("b", "Beta", Genre.SERIES)),
            )
            catalog.refreshListings(listOf(provider))

            assertThat(catalog.genreRails(listOf("tf1")).single().shows.map { it.show.id.value })
                .containsExactly("a", "b")
        }
    }
}
