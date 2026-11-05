package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** SPEC §Recherche: France TV search is the cache filter wired by the app. */
class FtvSearchTest {

    @Test
    fun `search delegates to the wired local cache`() {
        runBlocking {
            val provider = FtvProvider(
                "france-2",
                Channel("france-2", "France 2"),
                localSearch = { query -> listOf(Show(ShowId("hit"), query, null, null, emptySet())) },
            )
            val results = provider.search("marleau")
            assertThat(results).hasSize(1)
            assertThat(results.first().id.value).isEqualTo("hit")
        }
    }

    @Test
    fun `search without a wired cache stays empty`() {
        runBlocking {
            val provider = FtvProvider("france-2", Channel("france-2", "France 2"))
            assertThat(provider.search("marleau")).isEmpty()
        }
    }
}
