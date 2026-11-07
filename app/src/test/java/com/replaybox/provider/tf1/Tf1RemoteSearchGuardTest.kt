package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.blocking
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/**
 * SPEC §Recherche: search runs from 1 character, but TF1's remote `searchPrograms` (the
 * fallback while the Index is not stored) keeps a 3-character minimum. Zero network: the HTTP
 * layer is a double answering the recorded fixture.
 */
class Tf1RemoteSearchGuardTest {

    private class RecordingHttp : Tf1Http() {
        val queries = ArrayList<String>()

        override suspend fun graphql(queryId: String, variables: JsonObject): JsonObject {
            queries += queryId
            return TestFixtures.load("tf1_search_programs.json")
        }
    }

    private fun provider(http: Tf1Http, local: List<com.replaybox.core.model.Show>?) =
        Tf1Provider(http = http, log = {}, localSearch = { local })

    @Test
    fun shortQueriesNeverReachTheRemoteSearch() {
        val http = RecordingHttp()

        val shows = blocking { provider(http, local = null).search("hp") }

        assertThat(shows).isEmpty()
        assertThat(http.queries).isEmpty()
    }

    @Test
    fun threeCharactersFallBackToTheRemoteSearch() {
        val http = RecordingHttp()

        val shows = blocking { provider(http, local = null).search(" hpi ") }

        assertThat(http.queries).containsExactly(Tf1QueryIds.SEARCH_PROGRAMS)
        assertThat(shows.first().title).isEqualTo("HPI")
    }

    @Test
    fun theLocalIndexAnswersFromOneCharacter() {
        val http = RecordingHttp()
        val local = listOf(com.replaybox.core.model.Show(com.replaybox.core.model.ShowId("h"), "HPI", null, null, emptySet()))

        val shows = blocking { provider(http, local = local).search("h") }

        assertThat(shows).isEqualTo(local)
        assertThat(http.queries).isEmpty()
    }
}
