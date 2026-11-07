package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

/** neustar.category + program_type_wording.code → canonical Genres (catalogue capture 2026-11-07). */
class M6GenresTest {

    @Test
    fun crimeFictionIsSeriesAndCrime() {
        assertThat(M6Genres.map("serie-policiere", null)).containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
        assertThat(M6Genres.map("telefilm-frissons", null)).containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
    }

    @Test
    fun comedyAndDrama() {
        assertThat(M6Genres.map("comedie", null)).containsExactly(Genre.COMEDY)
        assertThat(M6Genres.map("serie-humour", null)).contains(Genre.COMEDY)
        assertThat(M6Genres.map("drame", null)).containsExactly(Genre.DRAMA)
        assertThat(M6Genres.map("series-dramatiques", null)).contains(Genre.DRAMA)
        assertThat(M6Genres.map("telefilm-drame", null)).contains(Genre.DRAMA)
    }

    @Test
    fun documentaryFromCategoryOrType() {
        assertThat(M6Genres.map("documentaire", null)).containsExactly(Genre.DOCUMENTARY)
        assertThat(M6Genres.map(null, "documentaire")).containsExactly(Genre.DOCUMENTARY)
    }

    @Test
    fun youthFromCategoryOrType() {
        listOf("kids", "tous-petits", "teens").forEach {
            assertThat(M6Genres.map(it, null)).containsExactly(Genre.YOUTH)
        }
        assertThat(M6Genres.map(null, "dessin-anime")).containsExactly(Genre.YOUTH)
    }

    @Test
    fun entertainment() {
        listOf("divertissement", "serie-realite", "cuisine", "musique", "spectacle", "competition").forEach {
            assertThat(M6Genres.map(it, null)).containsExactly(Genre.ENTERTAINMENT)
        }
        assertThat(M6Genres.map(null, "emission")).containsExactly(Genre.ENTERTAINMENT)
    }

    @Test
    fun sportAndInfo() {
        listOf("magazine-dinformation", "magazine-d-information", "info", "infos", "info-et-societe", "sport").forEach {
            assertThat(M6Genres.map(it, null)).containsExactly(Genre.SPORT_AND_INFO)
        }
    }

    @Test
    fun moviesAndSeriesFromType() {
        assertThat(M6Genres.map("action", "film")).containsExactly(Genre.MOVIES)
        assertThat(M6Genres.map(null, "episode")).containsExactly(Genre.SERIES)
        assertThat(M6Genres.map(null, "telefilm")).containsExactly(Genre.SERIES)
    }

    @Test
    fun unmappedSeriesFamilyFallsBackToSeries() {
        listOf("serie-fantastique", "serie-romantique", "telefilm-romance", "telenovela").forEach {
            assertThat(M6Genres.map(it, null)).contains(Genre.SERIES)
        }
    }

    @Test
    fun nothingKnownIsEmpty() {
        assertThat(M6Genres.map(null, null)).isEmpty()
        assertThat(M6Genres.map("action", null)).isEmpty()
    }
}
