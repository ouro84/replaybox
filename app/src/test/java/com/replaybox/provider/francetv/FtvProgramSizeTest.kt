package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * yatta caps each program collection at 250 items unless `size` is given (measured
 * 2026-11-06: journal-20h00 249 → 443, ca-commence-aujourd-hui 274 → 495,
 * 13h15-le-samedi 205 → 253 integrales with size=2000). Numbered series were complete.
 */
class FtvProgramSizeTest {

    @Test
    fun `program url asks for the whole collection`() {
        assertThat(FtvHttp().programUrl("france-2_13h15-le-samedi"))
            .isEqualTo("https://api-mobile.yatta.francetv.fr/apps/program/france-2_13h15-le-samedi?platform=apps&size=2000")
    }

    @Test
    fun `keeps every integrale past the default 250 cap`() {
        // Fixture recorded with size=2000; items trimmed of images/program/channel/category/markers.
        val root = FtvTestFixtures.load("ftv_program_13h15-le-samedi_size2000.json")
        val showId = FtvParsers.showIdFromPath("france-2_13h15-le-samedi")
        val season = FtvParsers.seasonsFromProgram(root, showId).single()
        assertThat(FtvParsers.episodesFromProgram(root, season.id)).hasSize(253)
    }
}
