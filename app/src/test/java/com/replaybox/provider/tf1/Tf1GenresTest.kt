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
    fun `falls back on suffix for unknown sgt`() {
        assertThat(Tf1Genres.map(listOf("SGT_SERIES_POLICIERES"))).containsExactly(Genre.CRIME_THRILLER)
        assertThat(Tf1Genres.map(listOf("SGT_COMEDIES"))).containsExactly(Genre.COMEDY)
    }

    @Test
    fun `unknown categories map to nothing`() {
        assertThat(Tf1Genres.map(listOf("GT_UNKNOWN_XYZ"))).isEmpty()
        assertThat(Tf1Genres.map(emptyList())).isEmpty()
    }
}
