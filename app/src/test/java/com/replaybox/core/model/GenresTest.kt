package com.replaybox.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Canonical Genre rules shared by every Provider (ROADMAP M4, grilling 2026-11-07). */
class GenresTest {

    @Test
    fun youthIsExclusive() {
        assertThat(Genres.canonical(setOf(Genre.SERIES, Genre.YOUTH, Genre.COMEDY))).containsExactly(Genre.YOUTH)
        assertThat(Genres.canonical(setOf(Genre.YOUTH))).containsExactly(Genre.YOUTH)
    }

    @Test
    fun otherGenreSetsAreUnchanged() {
        assertThat(Genres.canonical(setOf(Genre.SERIES, Genre.CRIME_THRILLER)))
            .containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
        assertThat(Genres.canonical(emptySet())).isEmpty()
    }

    @Test
    fun showKeepsEverythingButItsGenres() {
        val show = Show(ShowId("zig"), "Zig et Sharko", "p", "s", setOf(Genre.SERIES, Genre.YOUTH), "b", 3)
        assertThat(Genres.canonical(show)).isEqualTo(show.copy(genres = setOf(Genre.YOUTH)))
        val adult = show.copy(genres = setOf(Genre.SERIES))
        assertThat(Genres.canonical(adult)).isSameInstanceAs(adult)
    }
}
