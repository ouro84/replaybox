package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Channel
import com.replaybox.core.model.EpisodeId
import com.replaybox.provider.api.CachedProvider
import org.junit.Test

/** SPEC: the Flux is the only read that never goes through the cache. */
class CachedProviderTest {

    @Test
    fun `search and stream bypass the cache`() {
        blocking {
            val inner = FakeProvider(Channel("tf1", "TF1+"), showsResult = listOf(show("a", "Alpha")))
            val wrapped = CachedProvider(inner, CatalogStore(FakeCatalogDao()))
            wrapped.search("alpha")
            wrapped.stream(EpisodeId("a"))
            assertThat(inner.searchCalls).isEqualTo(1)
            assertThat(inner.streamCalls).isEqualTo(1)
            assertThat(inner.showsCalls).isEqualTo(0)
        }
    }
}
