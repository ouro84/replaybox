package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * programBySlug.videos (a6f9cf0e) reads `offset` as a PAGE INDEX, not an item index
 * (measured 2026-11-06 on "profilage", total=102: offset=1&limit=20 returns items 20..39,
 * offset=20&limit=20 returns nothing). Paging by item count stopped after the first page,
 * so only the most recent Saisons showed up.
 */
class Tf1ReplayPaginationTest {

    @Test
    fun `last page of profilage is a partial page of the same total`() {
        val page = Tf1Parsers.replayPage(TestFixtures.load("tf1_program_by_slug_profilage_last_page.json"))
        assertThat(page.total).isEqualTo(102)
        assertThat(page.videos.map { it.season }).containsExactly(2, 2)
    }

    @Test
    fun `requests pages by index and collects every Saison`() = runBlocking {
        val http = PagedHttp(total = 45, seasonOf = { index -> index / 10 + 1 })
        val seasons = Tf1Provider(http, log = {}).seasons(ShowId("profilage"))

        assertThat(http.requestedOffsets).containsExactly(0, 1, 2).inOrder()
        assertThat(seasons.map { it.number }).containsExactly(5, 4, 3, 2, 1).inOrder()
    }

    /** Fake graphql that serves a program's videos the way TF1 does: page = offset * limit. */
    private class PagedHttp(private val total: Int, private val seasonOf: (Int) -> Int) : Tf1Http() {
        val requestedOffsets = mutableListOf<Int>()

        override suspend fun graphql(queryId: String, variables: JsonObject): JsonObject {
            val pageIndex = variables["offset"]!!.jsonPrimitive.int
            val limit = variables["limit"]!!.jsonPrimitive.int
            requestedOffsets += pageIndex
            val indices = (pageIndex * limit until minOf((pageIndex + 1) * limit, total))
            return buildJsonObject {
                put("data", buildJsonObject {
                    put("programBySlug", buildJsonObject {
                        put("videos", buildJsonObject {
                            put("total", total)
                            put("items", buildJsonArray {
                                indices.forEach { i ->
                                    add(buildJsonObject {
                                        put("slug", "video-$i")
                                        put("type", "REPLAY")
                                        put("season", seasonOf(i))
                                        put("episode", i % 10 + 1)
                                    })
                                }
                            })
                        })
                    })
                })
            }
        }
    }
}
