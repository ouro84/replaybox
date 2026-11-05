package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

class Tf1SearchParserTest {

    @Test
    fun `parses searchPrograms for hpi`() {
        val shows = Tf1Parsers.showsFromSearch(TestFixtures.load("tf1_search_programs.json"))
        assertThat(shows).hasSize(3)
        val hpi = shows.first()
        assertThat(hpi.id.value).isEqualTo("hpi")
        assertThat(hpi.title).isEqualTo("HPI")
        assertThat(hpi.genres).contains(Genre.SERIES)
        assertThat(hpi.posterUrl).contains("photos.tf1.fr")
    }

    @Test
    fun `parses searchVideos labels`() {
        val labels = Tf1Parsers.searchVideoTitles(TestFixtures.load("tf1_search_videos.json"))
        assertThat(labels).isNotEmpty()
        assertThat(labels.first()).contains("TDAH")
    }
}
