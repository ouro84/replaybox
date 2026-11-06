package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.SeasonId
import org.junit.Test

class FtvProgramParserTest {

    private val marleau = FtvTestFixtures.load("ftv_program_capitaine-marleau.json")
    private val magazine = FtvTestFixtures.load("ftv_program_ca-commence-aujourd-hui.json")

    @Test
    fun `parses numbered seasons most recent first`() {
        val showId = FtvParsers.showIdFromPath("france-2_capitaine-marleau")
        val seasons = FtvParsers.seasonsFromProgram(marleau, showId)
        assertThat(seasons).hasSize(4)
        assertThat(seasons.map { it.number }).containsExactly(4, 3, 2, 1).inOrder()
        assertThat(seasons.first().title).isEqualTo("Saison 4")
        assertThat(seasons.first().id.value).isEqualTo("ftv::france-2_capitaine-marleau::4")
        assertThat(seasons.first().showId).isEqualTo(showId)
    }

    @Test
    fun `parses episodes of a numbered season`() {
        val showId = FtvParsers.showIdFromPath("france-2_capitaine-marleau")
        val season = FtvParsers.seasonsFromProgram(marleau, showId).last()
        val episodes = FtvParsers.episodesFromProgram(marleau, season.id)
        assertThat(episodes).hasSize(4)
        val first = episodes.first()
        assertThat(first.id.value)
            .isEqualTo("ftv::c931bb6f-3cc9-4762-bfa9-9f85f832fde1")
        assertThat(first.title).isEqualTo("S1 E7 - À ciel ouvert")
        assertThat(first.durationSeconds).isEqualTo(5563)
        assertThat(first.broadcastDate).isEqualTo("07/09/2020")
        assertThat(first.thumbnailUrl).startsWith("https://medias.france.tv/")
    }

    @Test
    fun `merges thematic collections into one unnumbered season for magazines`() {
        val showId = FtvParsers.showIdFromPath("france-2_ca-commence-aujourd-hui")
        val seasons = FtvParsers.seasonsFromProgram(magazine, showId)
        assertThat(seasons).hasSize(1)
        assertThat(seasons.first().number).isNull()
        assertThat(seasons.first().id.value).isEqualTo("ftv::france-2_ca-commence-aujourd-hui::")
    }

    @Test
    fun `deduplicates integrales across overlapping collections`() {
        val showId = FtvParsers.showIdFromPath("france-2_ca-commence-aujourd-hui")
        val season = FtvParsers.seasonsFromProgram(magazine, showId).first()
        val episodes = FtvParsers.episodesFromProgram(magazine, season.id)
        // 35 integrales recorded across playlist_video collections, 2 si_ids
        // appear twice → 33 episodes (extrait/article/audio playlists excluded).
        assertThat(episodes).hasSize(33)
        assertThat(episodes.map { it.id.value }).containsNoDuplicates()
        val first = episodes.first()
        assertThat(first.id.value).isEqualTo("ftv::af285055-6156-4921-8e51-f62c57df2b7d")
        assertThat(first.seasonId).isEqualTo(season.id)
        assertThat(first.title)
            .isEqualTo("Déni de grossesse : elles ne se sont rendu compte de rien !")
        assertThat(first.broadcastDate).isEqualTo("04/11/2026")
    }

    @Test
    fun `season carrying no program path is rejected`() {
        val rejected = runCatching { FtvParsers.programPathOfSeason(SeasonId("ftv::other::3")) }
        assertThat(rejected.isSuccess).isTrue()
        assertThat(rejected.getOrNull()).isEqualTo("other")
    }

    @Test
    fun `unescapes quotes France TV double-escapes in descriptions`() {
        // Recorded 2026-11-06: the decoded description holds a literal backslash-quote.
        val pitch = FtvTestFixtures.load("ftv_program_le-pitch-cinema.json")
        val episodes = FtvParsers.episodesFromProgram(pitch, null)
        val synopsis = episodes.first().synopsis.orEmpty()
        assertThat(synopsis).contains("grand écran \" donne les clefs")
        assertThat(synopsis).doesNotContain("\\")
    }
}
