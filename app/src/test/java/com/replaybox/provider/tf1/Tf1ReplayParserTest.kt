package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
class Tf1ReplayParserTest {

    @Test
    fun `parses replay page of hpi`() {
        val page = Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_replay.json"))
        assertThat(page.total).isEqualTo(40)
        assertThat(page.videos).hasSize(20)
        val first = page.videos.first()
        assertThat(first.slug).isEqualTo("hpi-s05-e08-supernova-15207545")
        assertThat(first.title).isEqualTo("HPI - S05 E08 - Supernova")
        assertThat(first.season).isEqualTo(5)
        assertThat(first.episode).isEqualTo(8)
        assertThat(first.durationSeconds).isEqualTo(4149)
        assertThat(first.date).isEqualTo("2026-09-17T07:00:00Z")
        assertThat(first.thumbnailUrl).startsWith("https://photos.tf1.fr/")
    }

    @Test
    fun `keeps only REPLAY videos`() {
        val root = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put(
                        "programBySlug",
                        buildJsonObject {
                            put(
                                "videos",
                                buildJsonObject {
                                    put("total", 2)
                                    put(
                                        "items",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("slug", "replay-ok")
                                                    put("type", "REPLAY")
                                                    put("decoration", buildJsonObject { put("label", "Replay ok") })
                                                },
                                            )
                                            add(
                                                buildJsonObject {
                                                    put("slug", "extract-ko")
                                                    put("type", "EXTRACT")
                                                    put("decoration", buildJsonObject { put("label", "Extract ko") })
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
        val page = Tf1Parsers.replayPage(root)
        assertThat(page.videos.map { it.slug }).containsExactly("replay-ok")
    }

    @Test
    fun `derives seasons from videos sorted desc`() {
        val videos = Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_replay.json")).videos
        val seasons = Tf1Parsers.seasonsFromVideos(ShowId("hpi"), videos)
        assertThat(seasons.map { it.number }).containsExactly(5, 4, 3).inOrder()
        assertThat(seasons.first().id.value).isEqualTo("hpi::5")
        assertThat(seasons.first().title).isEqualTo("Saison 5")
    }

    @Test
    fun `builds episodes for a season`() {
        val videos = Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_replay.json")).videos
        val episodes = Tf1Parsers.episodesFromVideos(SeasonId("hpi::5"), ShowId("hpi"), videos)
        assertThat(episodes).hasSize(8)
        assertThat(episodes.first().id.value).isEqualTo("hpi::hpi-s05-e08-supernova-15207545")
        assertThat(episodes.first().title).isEqualTo("HPI - S05 E08 - Supernova")
        assertThat(episodes.first().durationSeconds).isEqualTo(4149)
    }

    @Test
    fun `broadcast date reads like France TV, in Paris time`() {
        fun video(date: String?) = Tf1Parsers.ReplayVideo("s", "t", null, date, null, 3, 8, null)
        val episodes = Tf1Parsers.episodesFromVideos(null, ShowId("tracker"), listOf(
            video("2026-11-04T20:13:45Z"),
            video("2026-11-04T23:30:00Z"), // already 5 November in Paris
            video("not a date"),
            video(null),
        ))
        assertThat(episodes.map { it.broadcastDate }).containsExactly("04/11/2026", "05/11/2026", null, null).inOrder()
    }
}
