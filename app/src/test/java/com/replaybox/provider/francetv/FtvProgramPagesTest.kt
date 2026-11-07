package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Programme detail read by pages (SPEC « Plafond 250 », measured 2026-11-07): size=2000 in one
 * call weighed 28.5 MB for « Ça commence aujourd'hui » (OOM on the Bbox), mostly podcasts.
 * Fixtures are real size=250 pages, trimmed of heavy fields but with every collection and item:
 * page 0 has 250 items in « Les émissions », page 1 the 248 left (498 items, 494 si_id), and the
 * thematic « Histoires de… » collections repeat on both pages.
 */
class FtvProgramPagesTest {

    private val path = "france-2_ca-commence-aujourd-hui"

    /** Serves fixtures by `page` without any network; records the requested urls. */
    private class FixtureServer(private val pages: Map<Int, String>) {
        val requested = mutableListOf<String>()

        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url
                requested += url.toString()
                val name = pages[url.queryParameter("page")?.toInt() ?: 0]
                val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                if (name == null) {
                    builder.code(404).message("Not Found").body(ByteArray(0).toResponseBody()).build()
                } else {
                    val bytes = FtvProgramPagesTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes()
                    builder.code(200).message("OK").body(bytes.toResponseBody("application/json".toMediaType())).build()
                }
            })
            .build()
    }

    @Test
    fun `program url asks for one page of the capped size`() {
        assertThat(FtvHttp().programUrl(path, 1))
            .isEqualTo("https://api-mobile.yatta.francetv.fr/apps/program/$path?platform=apps&size=250&page=1")
    }

    @Test
    fun `pages the main collection until a short page and keeps every integrale`() = runTest {
        val server = FixtureServer(
            mapOf(
                0 to "ftv_program_ca-commence-aujourd-hui_page0.json",
                1 to "ftv_program_ca-commence-aujourd-hui_page1.json",
            ),
        )
        val detail = FtvHttp(server.client).program(path)

        // Page 1 holds 248 < 250 items: no page 2 is asked for.
        assertThat(server.requested).containsExactly(
            FtvHttp().programUrl(path, 0),
            FtvHttp().programUrl(path, 1),
        ).inOrder()
        val showId = FtvParsers.showIdFromPath(path)
        val season = FtvParsers.seasonsFromProgram(detail, showId).single()
        assertThat(FtvParsers.episodesFromProgram(detail, season.id)).hasSize(494)
    }

    @Test
    fun `keeps only video collections, their integrales, and no thematic repeat`() = runTest {
        val server = FixtureServer(
            mapOf(
                0 to "ftv_program_ca-commence-aujourd-hui_page0.json",
                1 to "ftv_program_ca-commence-aujourd-hui_page1.json",
            ),
        )
        val collections = FtvHttp(server.client).program(path)["collections"]!!.jsonArray
            .map { it.jsonObject }

        assertThat(collections.map { it["type"]!!.jsonPrimitive.content }.toSet())
            .containsExactly("playlist_video")
        val byLabel = collections.associateBy { it["label"]!!.jsonPrimitive.content }
        assertThat((byLabel.getValue("Les émissions")["items"] as JsonArray)).hasSize(498)
        // Thematic collections come from page 0 only; « Les moments forts » has no integrale.
        assertThat((byLabel.getValue("Histoires de santé")["items"] as JsonArray)).hasSize(20)
        assertThat((byLabel.getValue("Les moments forts")["items"] as JsonArray)).isEmpty()
    }

    @Test
    fun `a Programme under the cap takes one call and parses as the raw detail`() = runTest {
        val name = "ftv_program_capitaine-marleau.json"
        val server = FixtureServer(mapOf(0 to name))
        val showId = FtvParsers.showIdFromPath("france-2_capitaine-marleau")

        val paged = FtvHttp(server.client).program("france-2_capitaine-marleau")
        val raw = FtvTestFixtures.load(name)

        assertThat(server.requested).hasSize(1)
        val seasons = FtvParsers.seasonsFromProgram(paged, showId)
        assertThat(seasons).isEqualTo(FtvParsers.seasonsFromProgram(raw, showId))
        assertThat(seasons).hasSize(4)
        for (season in seasons) {
            assertThat(FtvParsers.episodesFromProgram(paged, season.id))
                .isEqualTo(FtvParsers.episodesFromProgram(raw, season.id))
        }
    }

    @Test
    fun `stops at the page limit when every page is full`() {
        val full = FtvProgramPages.Page(
            listOf(
                FtvProgramPages.Collection(
                    type = "playlist_video",
                    label = "Les émissions",
                    items = listOf(FtvProgramPages.Item(type = "integrale", siId = "a")),
                ),
            ),
        )
        var calls = 0
        FtvProgramPages.collect(pageSize = 1, maxPages = 3) { calls++; full }
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `a body over the cap fails with an FtvHttpException`() {
        val server = FixtureServer(mapOf(0 to "ftv_program_ca-commence-aujourd-hui_page0.json"))
        val http = FtvHttp(server.client, programPageMaxBytes = 64 * 1024)

        val error = assertThrows(FtvHttpException::class.java) {
            runBlocking { http.program(path) }
        }
        assertThat(error).hasMessageThat().contains("over 65536 bytes")
    }

    @Test
    fun `a whole-tree body over the cap fails with an FtvHttpException`() {
        val server = FixtureServer(mapOf(0 to "ftv_channel_france-2.json"))
        val http = FtvHttp(server.client, jsonMaxBytes = 1024)

        val error = assertThrows(FtvHttpException::class.java) {
            runBlocking { http.channel("france-2") }
        }
        assertThat(error).hasMessageThat().contains("over 1024 bytes")
    }
}
