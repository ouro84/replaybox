package com.replaybox.core.health

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.data.FakeCatalogDao
import com.replaybox.core.data.FakeProvider
import com.replaybox.core.data.blocking
import com.replaybox.core.data.show
import com.replaybox.core.model.Channel
import com.replaybox.provider.api.CachedProvider
import com.replaybox.provider.api.HealthTrackingProvider
import org.junit.Test

class LocalHealthTest {

    private val ftvIds = listOf("france-2", "france-3", "france-4", "france-5", "franceinfo")

    private fun group(store: LocalHealthStore, catalog: CatalogStore, failing: String?, at: () -> Long) =
        ftvIds.map { id ->
            val network = if (id == failing) {
                FakeProvider(channel = Channel(id, id), showsError = java.io.IOException("HTTP 503"))
            } else {
                FakeProvider(channel = Channel(id, id), showsResult = listOf(show("$id-1", "Programme $id")))
            }
            CachedProvider(HealthTrackingProvider(network, "francetv", store, at), catalog)
        }

    @Test
    fun anyChannelFailureMakesGroupKo() {
        blocking {
            val store = InMemoryLocalHealthStore()
            val catalog = CatalogStore(FakeCatalogDao()) { 1_000L }
            var now = 100L

            // France 3 fails while the 4 others succeed (it is not even the last one refreshed).
            catalog.refreshListings(group(store, catalog, failing = "france-3") { now })

            val afterFailure = store.read("francetv")
            assertThat(afterFailure.catalogue?.ok).isFalse()
            assertThat(afterFailure.catalogue?.error).isEqualTo("IOException: HTTP 503")
            assertThat(Health.localState(afterFailure)).isEqualTo(HealthState.KO)

            // Next pass, every Chaîne answers: the success replaces the failure.
            now = 200L
            catalog.refreshListings(group(store, catalog, failing = null) { now })

            val recovered = store.read("francetv")
            assertThat(recovered.catalogue).isEqualTo(Outcome(ok = true, at = 200L, error = null))
            assertThat(Health.localState(recovered)).isEqualTo(HealthState.OK)
        }
    }
}
