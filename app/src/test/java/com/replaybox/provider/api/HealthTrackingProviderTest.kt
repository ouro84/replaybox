package com.replaybox.provider.api

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.data.FakeProvider
import com.replaybox.core.data.blocking
import com.replaybox.core.data.show
import com.replaybox.core.health.Health
import com.replaybox.core.health.HealthState
import com.replaybox.core.health.InMemoryLocalHealthStore
import com.replaybox.core.health.LocalHealth
import com.replaybox.core.health.Outcome
import com.replaybox.core.model.Channel
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthTrackingProviderTest {

    private val tf1 = Channel("tf1", "TF1")
    private val store = InMemoryLocalHealthStore()

    @Test
    fun showsSuccessRecorded() {
        blocking {
            val tracked = HealthTrackingProvider(
                FakeProvider(channel = tf1, showsResult = listOf(show("a", "A"))),
                "tf1",
                store,
            ) { 42L }

            assertThat(tracked.shows(null).map { it.id.value }).containsExactly("a")
            assertThat(store.read("tf1")).isEqualTo(LocalHealth(catalogue = Outcome(true, 42L, null), stream = null))
        }
    }

    @Test
    fun streamFailureRecorded() {
        val tracked = HealthTrackingProvider(
            FakeProvider(channel = tf1, streamError = IllegalStateException("TF1 sign-in rejected: invalid credentials")),
            "tf1",
            store,
        ) { 7L }

        val thrown = assertThrows(IllegalStateException::class.java) {
            blocking { tracked.stream(EpisodeId("e")) }
        }

        assertThat(thrown.message).isEqualTo("TF1 sign-in rejected: invalid credentials")
        val local = store.read("tf1")
        assertThat(local.stream).isEqualTo(
            Outcome(ok = false, at = 7L, error = "IllegalStateException: TF1 sign-in rejected: invalid credentials"),
        )
        assertThat(Health.combine(HealthState.OK, Health.localState(local))).isEqualTo(HealthState.KO)
    }

    @Test
    fun errorTextIsCut() {
        val long = "x".repeat(300)
        assertThat(HealthTrackingProvider.describe(RuntimeException(long))).hasLength(120)
        assertThat(HealthTrackingProvider.describe(java.io.IOException())).isEqualTo("IOException")
    }

    @Test
    fun cancellationNotRecorded() {
        val tracked = HealthTrackingProvider(
            FakeProvider(channel = tf1, showsError = CancellationException("left the screen")),
            "tf1",
            store,
        ) { 1L }

        assertThrows(CancellationException::class.java) { blocking { tracked.shows(null) } }

        assertThat(store.read("tf1")).isEqualTo(LocalHealth(null, null))
    }

    @Test
    fun seasonsAndSearchNotRecorded() {
        blocking {
            val tracked = HealthTrackingProvider(
                FakeProvider(channel = tf1, seasonsError = java.io.IOException("down"), searchError = java.io.IOException("down")),
                "tf1",
                store,
            ) { 1L }

            runCatching { tracked.seasons(ShowId("s")) }
            runCatching { tracked.search("abc") }
            tracked.episodes(null)

            assertThat(store.read("tf1")).isEqualTo(LocalHealth(null, null))
        }
    }
}
