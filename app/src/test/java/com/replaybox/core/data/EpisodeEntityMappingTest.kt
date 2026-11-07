package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.SeasonId
import org.junit.Test
import java.time.Instant

/** The episodes cache (schema v5) keeps the freshness instants to the millisecond. */
class EpisodeEntityMappingTest {

    private val dated = Episode(
        id = EpisodeId("ftv::abc"),
        seasonId = SeasonId("ftv::p::"),
        title = "Édition du vendredi 6 novembre 2026",
        synopsis = null,
        broadcastDate = "06/11/2026",
        durationSeconds = 2311,
        thumbnailUrl = "https://example.invalid/t.jpg",
        broadcastAt = Instant.parse("2026-11-06T18:57:47Z"),
        publishedAt = Instant.parse("2026-11-06T19:38:31Z"),
        availableUntil = Instant.parse("2026-12-06T19:38:31.250Z"),
    )

    @Test
    fun `round trip keeps the freshness instants`() {
        val entity = dated.toEntity()
        assertThat(entity.broadcastAt).isEqualTo(dated.broadcastAt!!.toEpochMilli())
        assertThat(entity.publishedAt).isEqualTo(dated.publishedAt!!.toEpochMilli())
        assertThat(entity.availableUntil).isEqualTo(dated.availableUntil!!.toEpochMilli())
        assertThat(entity.toEpisode()).isEqualTo(dated)
    }

    @Test
    fun `round trip without dates stays null`() {
        val bare = dated.copy(broadcastAt = null, publishedAt = null, availableUntil = null, seasonId = null)
        val entity = bare.toEntity()
        assertThat(entity.broadcastAt).isNull()
        assertThat(entity.publishedAt).isNull()
        assertThat(entity.availableUntil).isNull()
        assertThat(entity.toEpisode()).isEqualTo(bare)
    }
}
