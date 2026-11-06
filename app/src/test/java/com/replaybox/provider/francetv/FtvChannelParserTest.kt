package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

class FtvChannelParserTest {

    @Test
    fun `parses flat items into shows`() {
        val shows = FtvParsers.showsFromChannel(FtvTestFixtures.load("ftv_channel_france-2.json"))
        // 12 items carry a program_path, one is listed in two collections →
        // 11 deduplicated shows. Live items (no program_path) are excluded.
        assertThat(shows).hasSize(11)
        val first = shows.first()
        assertThat(first.id.value)
            .isEqualTo("ftv::series-et-fictions_series-drame_les-disparus-des-argonnes")
        assertThat(first.title).isEqualTo("Les disparus des Argonnes")
        assertThat(first.synopsis).isNotEmpty()
    }

    @Test
    fun `deduplicates programs listed in several collections`() {
        val shows = FtvParsers.showsFromChannel(FtvTestFixtures.load("ftv_channel_france-2.json"))
        val paths = shows.map { it.id.value }
        assertThat(paths).containsNoDuplicates()
    }

    @Test
    fun `picks the widest portrait poster`() {
        val shows = FtvParsers.showsFromChannel(FtvTestFixtures.load("ftv_channel_france-2.json"))
        val first = shows.first()
        assertThat(first.posterUrl)
            .isEqualTo("https://medias.france.tv/YcRaNc9y2TmQrisC_YTrlWvE1LY/1024x1365/filters:quality(85)/j/1/d/php1ktd1j.jpg")
    }

    @Test
    fun `picks a landscape backdrop`() {
        val shows = FtvParsers.showsFromChannel(FtvTestFixtures.load("ftv_channel_france-2.json"))
        assertThat(shows.first().backdropUrl).startsWith("https://medias.france.tv/")
        assertThat(shows.count { it.backdropUrl != null }).isEqualTo(shows.size)
    }

    @Test
    fun `maps listing genres`() {
        val shows = FtvParsers.showsFromChannel(FtvTestFixtures.load("ftv_channel_france-2.json"))
        val drama = shows.first { it.id.value.endsWith("les-disparus-des-argonnes") }
        assertThat(drama.genres).containsAtLeast(Genre.SERIES, Genre.DRAMA)
        val magazine = shows.first { it.id.value.endsWith("ca-commence-aujourd-hui") }
        assertThat(magazine.genres).containsExactly(Genre.DOCUMENTARY)
        val game = shows.first { it.id.value.endsWith("the-floor-a-la-conquete-du-sol") }
        assertThat(game.genres).containsExactly(Genre.ENTERTAINMENT)
    }
}
