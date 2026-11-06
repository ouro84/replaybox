package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import com.replaybox.provider.api.CachedProvider
import org.junit.Test

/** ADR 0002 startup refresh: forced, even while the cached listing is fresh. */
class ForcedRefreshTest {

    @Test
    fun `refreshListings reaches the network behind a cache-first Provider`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao) { 1_000L }
            val network = FakeProvider(channel = Channel("tf1", "TF1+"), showsResult = listOf(show("new", "Nouveau")))
            dao.putListing("tf1", listOf(show("old", "Ancien")), 900L) // fresh: well within the TTL

            catalog.refreshListings(listOf(CachedProvider(network, catalog)))

            assertThat(network.showsCalls).isEqualTo(1)
            assertThat(dao.shows("tf1").map { it.id.value }).containsExactly("new")
        }
    }
}
