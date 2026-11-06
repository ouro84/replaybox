package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.ShowId
import org.junit.Test

/** Search captions: the Chaîne of each result, from the cached listings only. */
class SearchChannelsTest {

    @Test
    fun `channelsOf maps listed Programmes to their first Chaîne and skips unlisted ones`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao) { 1_000L }
            dao.putListing("tf1", listOf(show("koh", "Koh-Lanta")), 0)
            dao.putListing("france-2", listOf(show("pitch", "Le pitch cinéma"), show("koh", "Koh-Lanta")), 0)

            val channels = catalog.channelsOf(listOf(ShowId("koh"), ShowId("pitch"), ShowId("nope")), listOf("tf1", "france-2"))

            assertThat(channels).containsExactly(ShowId("koh"), "tf1", ShowId("pitch"), "france-2")
        }
    }
}
