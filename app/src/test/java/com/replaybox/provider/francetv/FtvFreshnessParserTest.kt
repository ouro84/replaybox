package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.ShowId
import org.junit.Test
import java.time.Instant

/**
 * Freshness dates of France TV Épisodes, on items recorded 2026-11-07 (SPEC §Provider France TV).
 * Expected instants are the raw unix seconds of the fixtures; their Paris reading is in comments
 * and was checked against france.tv « Diffusé le … à … - Disponible jusqu'au … ».
 */
class FtvFreshnessParserTest {

    private fun episodes(fixture: String, path: String): Map<String, Episode> {
        val root = FtvTestFixtures.load(fixture)
        val showId = ShowId(FtvParsers.ID_PREFIX + path)
        val season = FtvParsers.seasonsFromProgram(root, showId).single()
        return FtvParsers.episodesFromProgram(root, season.id).associateBy { FtvParsers.siIdOf(it.id) }
    }

    private val journal by lazy { episodes("ftv_program_journal-20h00_freshness.json", "france-2_journal-20h00") }
    private val envoye by lazy { episodes("ftv_program_envoye-special_freshness.json", "france-2_envoye-special") }

    @Test
    fun `recent journal carries broadcast, publication and end of availability`() {
        val e = journal.getValue("e8adc1cf-026b-4dc8-a368-3fe84d21f08d")
        // Diffusé le 06/11/2026 à 19h57 - Disponible jusqu'au 06/12/2026
        assertThat(e.broadcastAt).isEqualTo(Instant.ofEpochSecond(1793991467)) // 19:57:47 Paris
        assertThat(e.publishedAt).isEqualTo(Instant.ofEpochSecond(1793993911)) // 20:38:31 Paris
        assertThat(e.availableUntil).isEqualTo(Instant.ofEpochSecond(1796585911)) // 2026-12-06 20:38:31
        assertThat(e.broadcastDate).isEqualTo("06/11/2026")
    }

    @Test
    fun `no end sentinel is null`() {
        // end_date 2147483647: the site says « Disponible jusqu'au 19/01/2038 », i.e. no end.
        val archive = journal.getValue("a34bb88f-3d7a-4d72-929f-bfce0d59e3c0")
        assertThat(archive.availableUntil).isNull()
        assertThat(archive.broadcastAt).isEqualTo(Instant.ofEpochSecond(1622397479)) // 2021-05-30 19:57:59
    }

    @Test
    fun `broadcast item of a magazine keeps its broadcast instant`() {
        val e = envoye.getValue("c50fc04d-988b-493b-bf04-a0b9fc386a56")
        assertThat(e.broadcastAt).isEqualTo(Instant.ofEpochSecond(1793304564)) // 2026-10-29 21:09:24
        assertThat(e.availableUntil).isEqualTo(Instant.ofEpochSecond(1808949795))
    }

    @Test
    fun `web only item has no broadcast instant`() {
        // broadcast_channel null: france.tv shows no « Diffusé le », the broadcast fields are a
        // placeholder (equal to begin_date here, or an unrelated time).
        val placeholder = envoye.getValue("fb7ba4ee-e1a3-4830-a65d-daa10a96a033")
        assertThat(placeholder.broadcastAt).isNull()
        assertThat(placeholder.publishedAt).isEqualTo(Instant.ofEpochSecond(1793311200))
        assertThat(placeholder.availableUntil).isEqualTo(Instant.ofEpochSecond(1816811999)) // 28/07/2027

        val unrelated = envoye.getValue("bb0802b2-d3a7-11f1-88b7-19616fa871f9")
        assertThat(unrelated.broadcastAt).isNull()
        assertThat(unrelated.publishedAt).isEqualTo(Instant.ofEpochSecond(1793306605))
        // The display date is unchanged by this PR: it still reads broadcast_begin_date.
        assertThat(unrelated.broadcastDate).isEqualTo("29/10/2026")
    }

    @Test
    fun `items without dates leave the instants null`() {
        val root = FtvJson.parse(
            """{"collections":[{"type":"playlist_video","label":"Saison 1","items":[
               {"type":"integrale","si_id":"x","season":1,"title":"T","broadcast_channel":"france-2"}]}]}""",
        )
        val showId = ShowId(FtvParsers.ID_PREFIX + "p")
        val e = FtvParsers.episodesFromProgram(root, FtvParsers.seasonsFromProgram(root, showId).single().id).single()
        assertThat(e.broadcastAt).isNull()
        assertThat(e.publishedAt).isNull()
        assertThat(e.availableUntil).isNull()
    }
}
