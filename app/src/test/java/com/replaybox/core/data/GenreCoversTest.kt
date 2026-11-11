package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import org.junit.Test

/** One cover per Genre on the Genres page: pure selection from the « Tout voir » order (spec § PR C). */
class GenreCoversTest {

    private fun entry(
        id: String,
        title: String = id,
        channel: String = "tf1",
        backdrop: String? = "$id-bd",
        poster: String? = "$id-po",
    ) = ChannelShow(
        show(id, title, Genre.SERIES).copy(backdropUrl = backdrop, posterUrl = poster),
        channel,
    )

    private val x = entry("x")
    private val y = entry("y")

    @Test
    fun resolvesInGlossaryOrder() {
        // Map iteration order puts Policier first; the genres list decides.
        val covers = GenreCovers.pick(
            Genre.values().toList(),
            mapOf(Genre.CRIME_THRILLER to listOf(x, y), Genre.SERIES to listOf(x)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("x-bd")
        assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("y-bd")
    }

    @Test
    fun aProgrammeCoversOneTileOnly() {
        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(x, y), Genre.CRIME_THRILLER to listOf(x, y)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("x-bd")
        assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("y-bd")
    }

    @Test
    fun sameTitleOnTwoChannelsIsOneProgramme() {
        val tf1 = entry("a", title = "Koh-Lanta", channel = "tf1")
        val m6 = entry("b", title = "Koh-Lanta", channel = "m6")
        val other = entry("c")

        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(tf1), Genre.CRIME_THRILLER to listOf(m6, other)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("a-bd")
        assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("c-bd")
    }

    @Test
    fun backdropPreferredOverPoster() {
        val covers = GenreCovers.pick(listOf(Genre.SERIES), mapOf(Genre.SERIES to listOf(x)))

        assertThat(covers[Genre.SERIES]).isEqualTo("x-bd")
    }

    @Test
    fun posterWhenNoBackdrop() {
        val covers = GenreCovers.pick(
            listOf(Genre.SERIES),
            mapOf(Genre.SERIES to listOf(entry("x", backdrop = null))),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("x-po")
    }

    @Test
    fun programmeWithoutImageIsSkipped() {
        // « Foo » has no image in Séries; another copy of « Foo » (other id) has one in Policier.
        val bare = entry("foo-bare", title = "Foo", backdrop = null, poster = null)
        val imaged = entry("foo-imaged", title = "Foo")

        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(bare, y), Genre.CRIME_THRILLER to listOf(imaged)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("y-bd")
        // The skipped « Foo » was never chosen, so it is not used: Policier can still show it.
        assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("foo-imaged-bd")
    }

    @Test
    fun blankUrlCountsAsNoImage() {
        val blank = entry("blank", backdrop = "", poster = null)

        val covers = GenreCovers.pick(listOf(Genre.SERIES), mapOf(Genre.SERIES to listOf(blank, y)))

        assertThat(covers[Genre.SERIES]).isEqualTo("y-bd")
    }

    @Test
    fun blankBackdropFallsBackToThePoster() {
        val covers = GenreCovers.pick(
            listOf(Genre.SERIES),
            mapOf(Genre.SERIES to listOf(entry("x", backdrop = "", poster = "x-po"))),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("x-po")
    }

    @Test
    fun accentAndCaseVariantsAreOneProgramme() {
        val accented = entry("a", title = "Zèbre", channel = "tf1")
        val shouted = entry("b", title = "ZEBRE", channel = "m6")

        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(accented), Genre.CRIME_THRILLER to listOf(shouted, y)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("a-bd")
        assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("y-bd")
    }

    @Test
    fun anImageAlreadyShownIsSkipped() {
        // Two different Programmes sharing one picture (a Provider placeholder, a spin-off).
        val first = entry("first", backdrop = "shared.jpg")
        val twin = entry("twin", backdrop = "shared.jpg")

        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(first), Genre.CRIME_THRILLER to listOf(twin, y)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("shared.jpg")
        assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("y-bd")
    }

    @Test
    fun emptyGridHasNoCover() {
        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.MOVIES),
            mapOf(Genre.SERIES to listOf()),
        )

        assertThat(covers).doesNotContainKey(Genre.SERIES)
        assertThat(covers).doesNotContainKey(Genre.MOVIES)
    }

    @Test
    fun onlyUsedProgrammesLeaveATextTile() {
        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(x), Genre.CRIME_THRILLER to listOf(x)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("x-bd")
        assertThat(covers).doesNotContainKey(Genre.CRIME_THRILLER)
    }

    private val now = 1_800_000_000_000L

    @Test
    fun coversFollowTheToutVoirGrid() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing(
                "tf1",
                listOf(show("abeille", "Abeille", Genre.SERIES).copy(backdropUrl = "abeille-bd", posterUrl = "abeille-po")),
                now,
            )
            dao.putListing(
                "france-2",
                listOf(show("zebre", "Zèbre", Genre.SERIES).copy(posterUrl = "zebre-po", recencyAt = now)),
                now,
            )
            dao.putListing(
                "m6",
                listOf(
                    show("alpha", "Alpha", Genre.SERIES)
                        .copy(backdropUrl = "alpha-bd", posterUrl = "alpha-po", recencyAt = now),
                ),
                now,
            )
            val catalog = CatalogStore(dao, clock = { now })

            val covers = catalog.genreCovers(listOf("tf1", "france-2"))

            // The recent Programme leads the grid even with a poster only; m6 is not asked for.
            assertThat(covers[Genre.SERIES]).isEqualTo("zebre-po")
            val first = catalog.genreGrid(Genre.SERIES, listOf("tf1", "france-2"))
                .first { GenreCovers.imageOf(it.show) != null }
            assertThat(covers[Genre.SERIES]).isEqualTo(GenreCovers.imageOf(first.show))
        }
    }

    @Test
    fun emptyCacheGivesNoCovers() {
        blocking {
            val catalog = CatalogStore(FakeCatalogDao(), clock = { now })

            assertThat(catalog.genreCovers(listOf("tf1"))).isEmpty()
        }
    }

    @Test
    fun theGridsCopyDecidesNotAnotherChannelsImage() {
        blocking {
            val dao = FakeCatalogDao()
            // tf1 comes first, so the grid keeps its image-less « Alpha »; france-2's imaged copy is merged away.
            dao.putListing("tf1", listOf(show("alpha-tf1", "Alpha", Genre.SERIES)), now)
            dao.putListing(
                "france-2",
                listOf(
                    show("alpha-f2", "Alpha", Genre.SERIES).copy(backdropUrl = "alpha-f2-bd"),
                    show("beta", "Beta", Genre.SERIES).copy(backdropUrl = "beta-bd"),
                ),
                now,
            )
            val catalog = CatalogStore(dao, clock = { now })

            val covers = catalog.genreCovers(listOf("tf1", "france-2"))

            assertThat(covers[Genre.SERIES]).isEqualTo("beta-bd")
        }
    }

    @Test
    fun aRefreshChangesTheCover() {
        blocking {
            val dao = FakeCatalogDao()
            val tf1 = Channel("tf1", "TF1+")
            dao.putListing("tf1", listOf(show("abeille", "Abeille", Genre.SERIES).copy(backdropUrl = "old-bd")), now)
            // Same clock reading: only the store's own write can tell the covers to recompute.
            val catalog = CatalogStore(dao, clock = { now })
            assertThat(catalog.genreCovers(listOf("tf1"))[Genre.SERIES]).isEqualTo("old-bd")

            val fresh = show("abeille", "Abeille", Genre.SERIES).copy(backdropUrl = "new-bd")
            catalog.refreshListings(listOf(FakeProvider(channel = tf1, showsResult = listOf(fresh))))

            assertThat(catalog.genreCovers(listOf("tf1"))[Genre.SERIES]).isEqualTo("new-bd")
        }
    }

    @Test
    fun aRepeatVisitReusesTheCovers() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("abeille", "Abeille", Genre.SERIES).copy(backdropUrl = "abeille-bd")), now)
            var clock = now
            val catalog = CatalogStore(dao, clock = { clock })

            val first = catalog.genreCovers(listOf("tf1"))
            clock = now + 60_000L // a minute later, same Paris day

            assertThat(catalog.genreCovers(listOf("tf1"))).isSameInstanceAs(first)
        }
    }

    @Test
    fun aNewListingDateRecomputesTheCovers() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("abeille", "Abeille", Genre.SERIES).copy(backdropUrl = "abeille-bd")), now)
            val catalog = CatalogStore(dao, clock = { now })
            val first = catalog.genreCovers(listOf("tf1"))

            // Written behind the store's back (another process): only the listing date changes.
            dao.putListing("tf1", listOf(show("zebre", "Zèbre", Genre.SERIES).copy(backdropUrl = "zebre-bd")), now + 1)

            val second = catalog.genreCovers(listOf("tf1"))
            assertThat(second).isNotSameInstanceAs(first)
            assertThat(second[Genre.SERIES]).isEqualTo("zebre-bd")
        }
    }

    @Test
    fun aNewParisDayOrOtherChannelsRecomputeTheCovers() {
        blocking {
            val dao = FakeCatalogDao()
            dao.putListing("tf1", listOf(show("abeille", "Abeille", Genre.SERIES).copy(backdropUrl = "abeille-bd")), now)
            var clock = now
            val catalog = CatalogStore(dao, clock = { clock })
            val first = catalog.genreCovers(listOf("tf1"))

            clock = now + 86_400_000L // recency ranks by Paris day: a new day can reorder a grid
            val nextDay = catalog.genreCovers(listOf("tf1"))
            assertThat(nextDay).isNotSameInstanceAs(first)

            assertThat(catalog.genreCovers(listOf("tf1", "france-2"))).isNotSameInstanceAs(nextDay)
        }
    }
}
