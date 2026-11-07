package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Show.recencyAt of a France TV Chaîne page (SPEC §Provider France TV, measured 2026-11-07).
 * Fixture: the france-2 page of 2026-11-07 17:32 (Paris), every collection, items trimmed.
 */
class FtvRecencyParserTest {

    private val shows = FtvParsers.showsFromChannel(FtvTestFixtures.load("ftv_channel_france-2_recency.json"))
        .associateBy { it.id.value.removePrefix(FtvParsers.ID_PREFIX) }

    @Test
    fun `a featured Episode dates its Programme by its online date`() {
        assertThat(shows.getValue("france-2_cash-investigation").recencyAt).isEqualTo(1_793_918_023_000L)
        assertThat(shows.getValue("france-2_beau-geste").recencyAt).isEqualTo(1_793_768_400_000L)
        // Five Episodes of the Vendée Globe series are featured: the latest one counts.
        assertThat(shows.getValue("documentaires_documentaires-sport_vendee-globe-seuls-autour-du-monde").recencyAt)
            .isEqualTo(1_792_728_000_000L)
    }

    @Test
    fun `the live collection is not an available Episode`() {
        // Affaires sensibles is on air (live begin_date 1794070500, ahead): its replay feature counts.
        assertThat(shows.getValue("france-2_affaires-sensibles").recencyAt).isEqualTo(1_793_465_050_000L)
    }

    @Test
    fun `program items alone carry no date`() {
        assertThat(shows).hasSize(56)
        assertThat(shows.values.count { it.recencyAt != null }).isEqualTo(5)
        assertThat(shows.getValue("france-2_journal-20h00").recencyAt).isNull()
    }
}
