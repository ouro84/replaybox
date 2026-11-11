package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
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
        val none = entry("none", backdrop = null, poster = null)

        val covers = GenreCovers.pick(
            listOf(Genre.SERIES, Genre.CRIME_THRILLER),
            mapOf(Genre.SERIES to listOf(none, y), Genre.CRIME_THRILLER to listOf(none)),
        )

        assertThat(covers[Genre.SERIES]).isEqualTo("y-bd")
        // none was never chosen, so it is not used: it has no image anyway, the tile stays text.
        assertThat(covers).doesNotContainKey(Genre.CRIME_THRILLER)
    }

    @Test
    fun blankUrlCountsAsNoImage() {
        val blank = entry("blank", backdrop = "", poster = null)

        val covers = GenreCovers.pick(listOf(Genre.SERIES), mapOf(Genre.SERIES to listOf(blank, y)))

        assertThat(covers[Genre.SERIES]).isEqualTo("y-bd")
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
}
