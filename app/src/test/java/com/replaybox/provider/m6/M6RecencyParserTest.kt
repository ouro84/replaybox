package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/**
 * Show.recencyAt of the M6 catalogue (SPEC §Provider M6+, measured 2026-11-07):
 * `publication_date_vi.date_start_max`, Paris time. Fixture: 5 programmes of the 2026-11-07
 * 17:33 capture (Wanted 28540, Scènes de ménages 829, Pékin Express 945, Les sisters 21200,
 * Noël sur la 5e avenue 21184), trimmed to the parsed fields.
 */
class M6RecencyParserTest {

    private val shows = M6Parsers.catalogPage(M6Fixtures.text("m6_programs_recency.json"), 1287, 0, 100)
        .entries.associate { it.show.id.value to it.show }

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun latestFullEpisodePublicationInParisTime() {
        // « 2026-11-05 23:13:51 » in Paris (UTC+1 in November): Wanted E6, aired at 22:10.
        assertThat(shows.getValue("m6:m6replay:28540").recencyAt).isEqualTo(at("2026-11-05T22:13:51Z"))
        assertThat(shows.getValue("m6:m6replay:829").recencyAt).isEqualTo(at("2026-11-06T20:02:16Z"))
        // « 2026-05-29 23:33:04 » in summer time (UTC+2): Pékin Express, finale aired on 22/05.
        assertThat(shows.getValue("m6:m6replay:945").recencyAt).isEqualTo(at("2026-05-29T21:33:04Z"))
        assertThat(shows.getValue("m6:gulli:21200").recencyAt).isEqualTo(at("2026-11-07T08:38:00Z"))
        assertThat(shows.getValue("m6:m6replay:21184").recencyAt).isEqualTo(at("2024-12-30T18:41:00Z"))
    }

    @Test
    fun noPublicationFieldGivesNoDate() {
        // The older fixture predates the field: every Programme parses, undated.
        val entries = M6Parsers.catalogPage(M6Fixtures.text("m6_programs_page.json"), 1288, 0, 100).entries
        assertThat(entries).isNotEmpty()
        assertThat(entries.map { it.show.recencyAt }.toSet()).containsExactly(null)
    }
}
