package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.SeasonId
import org.junit.Test
import java.time.Instant

/**
 * Freshness dates of M6 Épisodes on video pages recorded 2026-11-07 (SPEC §Provider M6+). Only
 * the broadcast instant is exposed: `publication_date` is rewritten at broadcast + 7 days and no
 * end of availability exists in the anonymous listing.
 */
class M6FreshnessParserTest {

    private val videos = M6Parsers.videoPage(M6Fixtures.text("m6_videos_28540_freshness.json"), 6).episodes

    @Test
    fun `first diffusion is Paris time`() {
        // « 2026-11-05 21:10:00 » in winter (+01:00) → 20:10Z.
        val e3 = videos.first { it.clipId == "clip_13203441" }
        assertThat(e3.broadcastAt).isEqualTo(Instant.parse("2026-11-05T20:10:00Z"))
        // « 2026-10-29 21:10:00 »: still winter time (the change was 2026-10-25).
        val e2 = videos.first { it.clipId == "clip_13202596" }
        assertThat(e2.broadcastAt).isEqualTo(Instant.parse("2026-10-29T20:10:00Z"))
    }

    @Test
    fun `summer first diffusion uses the summer offset`() {
        // Existing fixture: Pékin Express « 2023-04-20 21:14:00 » (+02:00) → 19:14Z.
        val page = M6Parsers.videoPage(M6Fixtures.text("m6_videos_945_p1.json"), 155).episodes
        val finale = page.first { it.firstDiffusion == "2023-04-20 21:14:00" }
        assertThat(finale.broadcastAt).isEqualTo(Instant.parse("2023-04-20T19:14:00Z"))
    }

    @Test
    fun `episodes carry only the broadcast instant`() {
        val season = SeasonId("m6:m6replay:28540:1")
        val episodes = M6Parsers.episodes(season, "m6replay", videos)
        assertThat(episodes).isNotEmpty()
        episodes.forEach {
            assertThat(it.broadcastAt).isNotNull()
            // publication_date of E2 moved to 2026-11-05 22:05, a week after its broadcast:
            // not an online date, so never published as one.
            assertThat(it.publishedAt).isNull()
            assertThat(it.availableUntil).isNull()
        }
        assertThat(episodes.map { it.broadcastDate }).contains("05/11/2026")
    }

    @Test
    fun `no first diffusion means no broadcast instant`() {
        val video = M6Parsers.videoPage(M6Fixtures.text("m6_videos_829_no_diffusion.json"), 868).episodes.single()
        assertThat(video.broadcastAt).isNull()
    }
}
