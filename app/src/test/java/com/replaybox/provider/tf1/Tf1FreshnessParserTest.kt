package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ShowId
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Freshness dates of TF1 Épisodes on `a6f9cf0e` pages recorded 2026-11-07 (SPEC §Provider TF1+).
 * TF1 gives no end date, only `playingInfos.remainingDays` counted by the server at response
 * time and rounded up: the end of availability is anchored on `extensions.report.execution.start`.
 */
class Tf1FreshnessParserTest {

    /** `extensions.report.execution.start` of the jt-20h capture: 2026-11-07T13:48:22.965Z. */
    private val jtServerTime = Instant.ofEpochSecond(1794059302, 965137290)
    private val deviceClock = Instant.parse("2026-11-07T14:00:00Z")

    private fun jtPage() =
        Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_jt_20h_freshness.json"), deviceClock)

    @Test
    fun `broadcast and publication are the ISO instants of the listing`() {
        val newest = jtPage().videos.first()
        assertThat(newest.broadcastAt).isEqualTo(Instant.parse("2026-11-06T18:57:51Z")) // 19:57:51 Paris
        assertThat(newest.publishedAt).isEqualTo(Instant.parse("2026-11-06T19:39:50Z"))
    }

    @Test
    fun `end of availability is the server instant plus remaining days`() {
        val videos = jtPage().videos
        // remainingDays 7 for the 6/11 edition, 1 for the 31/10 one (which really ends the
        // evening of 7/11, a week after its publication: the bound is late by under a day).
        assertThat(videos[0].availableUntil).isEqualTo(jtServerTime.plus(Duration.ofDays(7)))
        assertThat(videos[1].availableUntil).isEqualTo(jtServerTime.plus(Duration.ofDays(1)))
    }

    @Test
    fun `without the server instant the fetch clock anchors remaining days`() {
        val root = TestFixtures.load("tf1_program_by_slug_jt_20h_freshness.json")
        val withoutExtensions = JsonObject(root.filterKeys { it != "extensions" })
        val videos = Tf1Parsers.replayPage(withoutExtensions, deviceClock).videos
        assertThat(videos[1].availableUntil).isEqualTo(deviceClock.plus(Duration.ofDays(1)))
    }

    @Test
    fun `regular soap Episode is published at its broadcast`() {
        val page = Tf1Parsers.replayPage(
            TestFixtures.load("tf1_program_by_slug_feux_de_l_amour_freshness.json"),
            deviceClock,
        )
        val serverTime = Instant.ofEpochSecond(1794059418, 211514799) // 13:50:18Z
        // The avant-premiere of the capture (paid offer) is dropped: see Tf1AvantPremiereTest.
        // Regular Épisode: broadcast 6/11 10:57 Paris, available 30 days → 29 days left.
        val regular = page.videos.single()
        assertThat(regular.broadcastAt).isEqualTo(Instant.parse("2026-11-06T09:57:36Z"))
        assertThat(regular.publishedAt).isEqualTo(regular.broadcastAt)
        assertThat(regular.availableUntil).isEqualTo(serverTime.plus(Duration.ofDays(29)))
    }

    @Test
    fun `episodes carry the instants`() {
        val showId = ShowId("jt-20h")
        val videos = jtPage().videos
        val episodes = Tf1Parsers.episodesFromVideos(null, showId, videos)
        assertThat(episodes[0].broadcastAt).isEqualTo(videos[0].broadcastAt)
        assertThat(episodes[0].publishedAt).isEqualTo(videos[0].publishedAt)
        assertThat(episodes[0].availableUntil).isEqualTo(videos[0].availableUntil)
        assertThat(episodes[0].broadcastDate).isEqualTo("06/11/2026")
    }

    @Test
    fun `missing or unparsable fields stay null`() {
        val root = Tf1Json.parse(
            """{"data":{"programBySlug":{"videos":{"total":1,"items":[
               {"slug":"a","type":"REPLAY","date":"not a date","published":null,
                "playingInfos":{"remainingDays":null}}]}}}}""",
        )
        val video = Tf1Parsers.replayPage(root, deviceClock).videos.single()
        assertThat(video.broadcastAt).isNull()
        assertThat(video.publishedAt).isNull()
        assertThat(video.availableUntil).isNull()
    }
}
