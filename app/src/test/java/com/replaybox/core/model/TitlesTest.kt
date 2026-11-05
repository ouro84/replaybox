package com.replaybox.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SPEC §Recherche: fusion matches on case- and accent-insensitive titles. */
class TitlesTest {

    @Test
    fun `strips case and accents`() {
        assertThat(Titles.normalize("César Wagner")).isEqualTo(Titles.normalize("CESAR WAGNER"))
        assertThat(Titles.normalize("À l'instinct")).isEqualTo("a l'instinct")
        assertThat(Titles.normalize("Été")).isEqualTo("ete")
    }

    @Test
    fun `expands the oe ligature`() {
        assertThat(Titles.normalize("Cœur")).isEqualTo(Titles.normalize("Coeur"))
    }

    @Test
    fun `keeps plain titles unchanged`() {
        assertThat(Titles.normalize("Tracker")).isEqualTo("tracker")
    }
}
