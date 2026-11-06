package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ShowId
import org.junit.Test

/**
 * « Le 20h » (programSlug jt-20h, measured 2026-11-06): the anonymous TF1 chain
 * walked by the nightly health-check. Fixture tf1_program_by_slug_jt_20h.json.
 */
class Tf1Jt20hParserTest {

    @Test
    fun `le 20h is one numbered Saison with the latest replay first`() {
        val show = ShowId("jt-20h")
        val page = Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_jt_20h.json"))
        assertThat(page.total).isEqualTo(7)

        val seasons = Tf1Parsers.seasonsFromVideos(show, page.videos)
        assertThat(seasons.map { it.title }).containsExactly("Saison 26")

        val episodes = Tf1Parsers.episodesFromVideos(seasons.first().id, show, page.videos)
        assertThat(episodes).hasSize(7)
        assertThat(episodes.first().id.value)
            .isEqualTo("jt-20h${Tf1Parsers.SEPARATOR}le-jt-de-20-heures-de-tf1-du-jeudi-5-novembre-2026-96758227")
    }
}
