package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

class Tf1GenresTest {

    @Test
    fun `maps hpi taxonomy to series`() {
        val genres = Tf1Genres.map(listOf("GT_SERIES_AND_FICTIONS", "SGT_SERIES"))
        assertThat(genres).containsExactly(Genre.SERIES)
    }

    @Test
    fun `maps root categories`() {
        assertThat(Tf1Genres.map(listOf("GT_MOVIES"))).containsExactly(Genre.MOVIES)
        assertThat(Tf1Genres.map(listOf("GT_ENTERTAINMENT"))).containsExactly(Genre.ENTERTAINMENT)
        assertThat(Tf1Genres.map(listOf("GT_INFOS_MAG_AND_SPORT"))).containsExactly(Genre.SPORT_AND_INFO)
        assertThat(Tf1Genres.map(listOf("GT_YOUTH"))).containsExactly(Genre.YOUTH)
    }

    @Test
    fun `film sub-genres add to the root genre`() {
        assertThat(Tf1Genres.map(listOf("GT_MOVIES", "SGT_COMEDY")))
            .containsExactly(Genre.MOVIES, Genre.COMEDY)
        assertThat(Tf1Genres.map(listOf("GT_MOVIES", "SGT_DRAMA")))
            .containsExactly(Genre.MOVIES, Genre.DRAMA)
        assertThat(Tf1Genres.map(listOf("GT_MOVIES", "SGT_DOCUMENTARY")))
            .containsExactly(Genre.MOVIES, Genre.DOCUMENTARY)
        assertThat(Tf1Genres.map(listOf("GT_MOVIES", "SGT_THRILLER")))
            .containsExactly(Genre.MOVIES, Genre.CRIME_THRILLER)
        assertThat(Tf1Genres.map(listOf("GT_MOVIES", "SGT_DETECTIVE")))
            .containsExactly(Genre.MOVIES, Genre.CRIME_THRILLER)
    }

    @Test
    fun `catch-all film sub-genres add nothing`() {
        for (sgt in listOf("SGT_ENTERTAINMENT", "SGT_SPORT", "SGT_ACTION", "SGT_ROMANCE", "SGT_OTHER")) {
            assertThat(Tf1Genres.map(listOf("GT_MOVIES", sgt))).containsExactly(Genre.MOVIES)
        }
    }

    @Test
    fun `sub-categories under their own root keep a single genre`() {
        assertThat(Tf1Genres.map(listOf("GT_SERIES_AND_FICTIONS", "SGT_FOREIGN_FICTION")))
            .containsExactly(Genre.SERIES)
        assertThat(Tf1Genres.map(listOf("GT_INFOS_MAG_AND_SPORT", "SGT_SPORT")))
            .containsExactly(Genre.SPORT_AND_INFO)
        assertThat(Tf1Genres.map(listOf("GT_ENTERTAINMENT", "SGT_GAMES")))
            .containsExactly(Genre.ENTERTAINMENT)
        assertThat(Tf1Genres.map(listOf("GT_YOUTH", "SGT_THREE_TO_SIX_YEARS")))
            .containsExactly(Genre.YOUTH)
    }

    @Test
    fun `unknown categories map to nothing`() {
        assertThat(Tf1Genres.map(listOf("GT_UNKNOWN_XYZ"))).isEmpty()
        assertThat(Tf1Genres.map(listOf("SGT_COMEDIE", "SGT_POLICIER"))).isEmpty()
        assertThat(Tf1Genres.map(emptyList())).isEmpty()
    }
}
