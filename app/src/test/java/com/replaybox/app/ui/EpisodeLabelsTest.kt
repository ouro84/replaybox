package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpisodeLabelsTest {

    @Test
    fun `numbering is prefixed when both numbers are known`() {
        assertThat(EpisodeLabels.shortNumbering(2, 4, "Le Dernier Mot")).isEqualTo(2 to 4)
    }

    @Test
    fun `no numbering without both numbers`() {
        assertThat(EpisodeLabels.shortNumbering(null, 4, "Le Dernier Mot")).isNull()
        assertThat(EpisodeLabels.shortNumbering(2, null, "Le Dernier Mot")).isNull()
    }

    @Test
    fun `a title that already carries the numbering is left alone`() {
        // TF1 « HPI - S05 E08 - Supernova », France TV « S1 E7 - À ciel ouvert ».
        assertThat(EpisodeLabels.shortNumbering(5, 8, "HPI - S05 E08 - Supernova")).isNull()
        assertThat(EpisodeLabels.shortNumbering(1, 7, "S1 E7 - À ciel ouvert")).isNull()
        assertThat(EpisodeLabels.shortNumbering(1, 17, "S1 E7 - À ciel ouvert")).isEqualTo(1 to 17)
    }
}
