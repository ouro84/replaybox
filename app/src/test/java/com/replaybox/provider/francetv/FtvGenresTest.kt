package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Genres
import org.junit.Test

class FtvGenresTest {

    @Test
    fun `maps categories to canonical genres`() {
        assertThat(FtvGenres.map("documentaires", emptyList()))
            .containsExactly(Genre.DOCUMENTARY)
        assertThat(FtvGenres.map("films", emptyList())).containsExactly(Genre.MOVIES)
        assertThat(FtvGenres.map("enfants", emptyList())).containsExactly(Genre.YOUTH)
        assertThat(FtvGenres.map("info", emptyList())).containsExactly(Genre.SPORT_AND_INFO)
        assertThat(FtvGenres.map("jeux-et-divertissements", emptyList()))
            .containsExactly(Genre.ENTERTAINMENT)
        assertThat(FtvGenres.map("societe", emptyList())).containsExactly(Genre.DOCUMENTARY)
        assertThat(FtvGenres.map("spectacles-et-culture", emptyList()))
            .containsExactly(Genre.ENTERTAINMENT)
        assertThat(FtvGenres.map("sport", emptyList())).containsExactly(Genre.SPORT_AND_INFO)
        assertThat(FtvGenres.map("series-et-fictions", emptyList())).containsExactly(Genre.SERIES)
    }

    @Test
    fun `sub categories refine the series family`() {
        assertThat(FtvGenres.map("series-et-fictions", listOf("series-et-fictions/series-policieres")))
            .containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
        assertThat(FtvGenres.map("series-et-fictions", listOf("series-et-fictions/series-thriller")))
            .contains(Genre.CRIME_THRILLER)
        assertThat(FtvGenres.map("series-et-fictions", listOf("series-et-fictions/series-comedie")))
            .contains(Genre.COMEDY)
        assertThat(FtvGenres.map("series-et-fictions", listOf("series-et-fictions/series-drame")))
            .contains(Genre.DRAMA)
        assertThat(FtvGenres.map("series-et-fictions", listOf("series-et-fictions/films-tv")))
            .contains(Genre.MOVIES)
    }

    @Test
    fun `handles both slash and underscore separators`() {
        // Measured: info/jt and info_jt, societe/magazine-d-actualites and
        // societe_magazine-d-actualites both occur across channel listings.
        assertThat(FtvGenres.map("info", listOf("info_jt"))).containsExactly(Genre.SPORT_AND_INFO)
        assertThat(FtvGenres.map("societe", listOf("societe_magazine-d-actualites")))
            .containsExactly(Genre.DOCUMENTARY)
        assertThat(FtvGenres.map(null, listOf("jeux-et-divertissements/humour")))
            .containsExactly(Genre.ENTERTAINMENT)
    }

    @Test
    fun `enfants is Jeunesse only`() {
        // Jeunesse is exclusive (Genres.canonical): a kids' Programme refined by a series
        // sub-category stays out of the other rails. None did on the 2026-11-07 listings.
        assertThat(Genres.canonical(FtvGenres.map("enfants", listOf("series-et-fictions/series-comedie"))))
            .containsExactly(Genre.YOUTH)
        assertThat(Genres.canonical(FtvGenres.map("enfants", emptyList()))).containsExactly(Genre.YOUTH)
    }

    @Test
    fun `unknown slugs are ignored`() {
        assertThat(FtvGenres.map("inconnu", listOf("inconnu/inconnu"))).isEmpty()
        assertThat(FtvGenres.map(null, emptyList())).isEmpty()
    }
}
