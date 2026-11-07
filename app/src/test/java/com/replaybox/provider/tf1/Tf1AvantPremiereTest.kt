package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import java.time.Instant

/**
 * TF1 avant-premières (paid TF1+ offer, broadcast days later) are not Épisodes the household's
 * free account can watch (SPEC §Provider TF1+, measured 2026-11-07): ordinary Épisodes carry
 * `rights: ["BASIC","MAX"]`, avant-premières only `["MAX"]` (and `freeRemainingDays` 0).
 */
class Tf1AvantPremiereTest {

    private val fetchedAt = Instant.parse("2026-11-07T16:00:00Z")

    @Test
    fun `avant-premiere is dropped and the ordinary Episode kept`() {
        val page = Tf1Parsers.replayPage(
            TestFixtures.load("tf1_program_by_slug_demain_nous_appartient_avant_premiere.json"),
            fetchedAt,
        )
        assertThat(page.videos.map { it.slug })
            .containsExactly("demain-nous-appartient-du-vendredi-6-novembre-2026-episode-2330-23299422")
        assertThat(page.videos.single().title).doesNotContain("Avant-première")
        // Pagination still follows TF1's counts, not the filtered list.
        assertThat(page.total).isEqualTo(2338)
        assertThat(page.itemCount).isEqualTo(2)
    }

    @Test
    fun `avant-premiere never becomes an Episode`() {
        val showId = ShowId("demain-nous-appartient")
        val videos = Tf1Parsers.replayPage(
            TestFixtures.load("tf1_program_by_slug_demain_nous_appartient_avant_premiere.json"),
            fetchedAt,
        ).videos
        val episodes = Tf1Parsers.episodesFromVideos(SeasonId("demain-nous-appartient::10"), showId, videos)
        assertThat(episodes.map { it.number }).containsExactly(50)
    }

    @Test
    fun `former avant-premiere already broadcast is kept`() {
        // HPI S04E04: slug avant-premiere-hpi-s04-e04-…, title without the prefix, rights ["MAX"],
        // broadcast 17/09: no longer an avant-première, kept at Arnaud's request (2026-11-07).
        val videos = Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_replay.json"), fetchedAt).videos
        val season4 = videos.filter { it.season == 4 }
        assertThat(season4.map { it.episode }).containsExactly(8, 7, 6, 5, 4, 3, 2, 1).inOrder()
    }

    @Test
    fun `video without rights is kept`() {
        val root = Tf1Json.parse(
            """{"data":{"programBySlug":{"videos":{"total":3,"items":[
               {"slug":"no-rights","type":"REPLAY"},
               {"slug":"free","type":"REPLAY","rights":["BASIC","MAX"]},
               {"slug":"paid-ahead","type":"REPLAY","rights":["MAX"],"date":"2099-01-01T00:00:00Z"},
               {"slug":"paid-aired","type":"REPLAY","rights":["MAX"],"date":"2000-01-01T00:00:00Z"}]}}}}""",
        )
        val page = Tf1Parsers.replayPage(root, fetchedAt)
        assertThat(page.videos.map { it.slug }).containsExactly("no-rights", "free", "paid-aired").inOrder()
        assertThat(page.itemCount).isEqualTo(4)
    }

    @Test
    fun `a page of avant-premieres only does not end the crawl`() = runBlocking {
        // Page 0 holds only paid videos; page 1 the ordinary Épisodes.
        val http = object : Tf1Http() {
            override suspend fun graphql(queryId: String, variables: JsonObject): JsonObject {
                val pageIndex = variables["offset"]!!.jsonPrimitive.int
                val rights = if (pageIndex == 0) listOf("MAX") else listOf("BASIC", "MAX")
                val count = if (pageIndex <= 1) Tf1Http.PAGE_LIMIT else 0
                return buildJsonObject {
                    put("data", buildJsonObject {
                        put("programBySlug", buildJsonObject {
                            put("videos", buildJsonObject {
                                put("total", 2 * Tf1Http.PAGE_LIMIT)
                                put("items", buildJsonArray {
                                    repeat(count) { i ->
                                        add(buildJsonObject {
                                            put("slug", "video-$pageIndex-$i")
                                            put("type", "REPLAY")
                                            put("season", 1)
                                            put("episode", i + 1)
                                            put("rights", buildJsonArray { rights.forEach { add(it) } })
                                            // Avant-premières are dated days ahead (measured 2026-11-07).
                                            if (pageIndex == 0) put("date", "2099-01-01T00:00:00Z")
                                        })
                                    }
                                })
                            })
                        })
                    })
                }
            }
        }
        val provider = Tf1Provider(http, log = {})
        val season = provider.seasons(ShowId("feuilleton")).single()
        val episodes = provider.episodes(season.id)
        assertThat(episodes).hasSize(Tf1Http.PAGE_LIMIT)
        assertThat(episodes.all { it.id.value.startsWith("feuilleton::video-1-") }).isTrue()
    }
}
