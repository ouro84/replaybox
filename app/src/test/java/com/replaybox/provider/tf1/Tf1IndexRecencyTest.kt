package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/**
 * Show.recencyAt of the TF1 Index (SPEC §Provider TF1+, measured 2026-11-07): no date in the
 * listing, only `replayRecent.total`; a non-zero count dates the Programme to the server
 * instant of the answer. Fixture: page 0 of the unfiltered 483ce0f crawl of 2026-11-07 17:29
 * (Paris), 6 programmes, images dropped, `extensions` kept.
 */
class Tf1IndexRecencyTest {

    private val page = TestFixtures.load("tf1_programs_index_recency.json")
    private val shows = Tf1Parsers.indexPage(page, 0, 500).entries.associate { it.show.id.value to it.show }

    /** extensions.report.execution.start = 1794068975472269311 ns. */
    private val answeredAt = Instant.parse("2026-11-07T16:29:35.472Z").toEpochMilli()

    @Test
    fun recentEpisodesDateTheProgrammeToTheAnswer() {
        assertThat(shows.getValue("star-academy").recentEpisodeCount).isEqualTo(5)
        assertThat(shows.getValue("star-academy").recencyAt).isEqualTo(answeredAt)
        assertThat(shows.getValue("jt-20h").recencyAt).isEqualTo(answeredAt)
        assertThat(shows.getValue("quotidien-avec-yann-barthes").recencyAt).isEqualTo(answeredAt)
        // Cat's Eyes counts its avant-première (aired 09/11, online since 02/11).
        assertThat(shows.getValue("cats-eyes").recencyAt).isEqualTo(answeredAt)
    }

    @Test
    fun noRecentEpisodeGivesNoDate() {
        // Koh-Lanta aired on 03/11: four days ago, outside TF1's recent window.
        assertThat(shows.getValue("koh-lanta").recentEpisodeCount).isEqualTo(0)
        assertThat(shows.getValue("koh-lanta").recencyAt).isNull()
        assertThat(shows.getValue("doc-martin").recencyAt).isNull()
    }

    @Test
    fun noServerInstantGivesNoDate() {
        // The older fixture has no `extensions`: Star Academy counts 5 recent Épisodes, undated.
        val old = Tf1Parsers.indexPage(TestFixtures.load("tf1_programs_index_page.json"), 0, 500).entries
        assertThat(old.first { it.show.id.value == "star-academy" }.show.recentEpisodeCount).isEqualTo(5)
        assertThat(old.map { it.show.recencyAt }.toSet()).containsExactly(null)
        assertThat(Tf1Parsers.recencyAt(3, null)).isNull()
        assertThat(Tf1Parsers.recencyAt(0, answeredAt)).isNull()
    }
}
