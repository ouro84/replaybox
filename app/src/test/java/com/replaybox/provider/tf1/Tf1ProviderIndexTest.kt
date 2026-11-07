package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Show
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** The 5 TF1 group Chaînes are served from one shared Index (plan M3 step 2, Task 4). */
class Tf1ProviderIndexTest {

    /** Serves the Index fixture as a single page (total = its item count) and empty searches. */
    private class IndexHttp : Tf1Http() {
        val calls = mutableListOf<String>()

        override suspend fun graphql(queryId: String, variables: JsonObject): JsonObject {
            calls += queryId
            return when (queryId) {
                Tf1QueryIds.PROGRAMS -> singlePage(TestFixtures.load("tf1_programs_index_page.json"))
                Tf1QueryIds.SEARCH_PROGRAMS -> TestFixtures.load("tf1_search_programs.json")
                else -> error("unexpected query $queryId")
            }
        }

        private fun singlePage(root: JsonObject): JsonObject {
            val programs = root["data"]!!.jsonObject["programs"]!!.jsonObject
            val items = programs["items"]!!
            return buildJsonObject {
                put("data", buildJsonObject {
                    put("programs", buildJsonObject {
                        put("total", JsonPrimitive((items as kotlinx.serialization.json.JsonArray).size))
                        put("items", items)
                    })
                })
            }
        }
    }

    private fun provider(http: Tf1Http, channelId: String, index: com.replaybox.provider.api.CatalogueIndex, localSearch: (suspend (String) -> List<Show>?)? = null) =
        Tf1Provider(
            http = http,
            log = {},
            channel = Tf1Channels.GROUP.first { it.id == channelId },
            index = index,
            localSearch = localSearch,
        )

    @Test
    fun showsReturnsOnlyThisChannelSlice() = runBlocking {
        val http = IndexHttp()
        val index = tf1CatalogueIndex(http)
        val tmc = provider(http, "tmc", index)
        assertThat(tmc.shows(null).map { it.id.value }).containsExactly("hercule-poirot")
        assertThat(provider(http, "tf1", index).shows(null).map { it.id.value })
            .containsExactly("star-academy", "les-dossiers-cold-case").inOrder()
    }

    @Test
    fun fiveProvidersShareOneCrawl() = runBlocking {
        val http = IndexHttp()
        val index = tf1CatalogueIndex(http)
        val providers = Tf1Channels.GROUP.map { provider(http, it.id, index) }
        providers.forEach { it.shows(null) }
        assertThat(http.calls.count { it == Tf1QueryIds.PROGRAMS }).isEqualTo(1)
        assertThat(providers.map { it.channel.id }).containsExactly("tf1", "tmc", "tfx", "tf1-series-films", "lci").inOrder()
    }

    @Test
    fun emptyIndexFallsBackToRemoteOnlyForTf1() = runBlocking {
        val http = IndexHttp()
        val index = tf1CatalogueIndex(http)

        val tf1 = provider(http, "tf1", index, localSearch = { null })
        assertThat(tf1.search("koh")).isNotEmpty()
        assertThat(http.calls.count { it == Tf1QueryIds.SEARCH_PROGRAMS }).isEqualTo(1)

        val tmc = provider(http, "tmc", index, localSearch = { emptyList() })
        assertThat(tmc.search("koh")).isEmpty()
        assertThat(http.calls.count { it == Tf1QueryIds.SEARCH_PROGRAMS }).isEqualTo(1)
    }

    @Test
    fun localSearchIsUsedWhenTheIndexIsThere() = runBlocking {
        val http = IndexHttp()
        val hit = Show(com.replaybox.core.model.ShowId("profilage"), "Profilage", null, null, emptySet())
        val tf1 = provider(http, "tf1", tf1CatalogueIndex(http), localSearch = { listOf(hit) })
        assertThat(tf1.search("prof")).containsExactly(hit)
        assertThat(http.calls).isEmpty()
    }
}
