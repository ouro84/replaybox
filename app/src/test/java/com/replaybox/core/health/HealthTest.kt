package com.replaybox.core.health

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.health.HealthState.KO
import com.replaybox.core.health.HealthState.OK
import com.replaybox.core.health.HealthState.UNKNOWN
import org.junit.Test

class HealthTest {

    @Test
    fun combineTable() {
        // The worse of the two sides (ROADMAP §M3, Arnaud 2026-11-07): KO > UNKNOWN > OK.
        val expected = mapOf(
            (OK to OK) to OK,
            (OK to KO) to KO,
            (OK to UNKNOWN) to UNKNOWN,
            (KO to OK) to KO,
            (KO to KO) to KO,
            (KO to UNKNOWN) to KO,
            (UNKNOWN to OK) to UNKNOWN,
            (UNKNOWN to KO) to KO,
            (UNKNOWN to UNKNOWN) to UNKNOWN,
        )
        for ((pair, result) in expected) {
            assertThat(Health.combine(ci = pair.first, local = pair.second)).isEqualTo(result)
        }
    }

    @Test
    fun localStateLatestFailureWins() {
        val ok = Outcome(ok = true, at = 10, error = null)
        val ko = Outcome(ok = false, at = 20, error = "IOException: offline")

        assertThat(Health.localState(LocalHealth(catalogue = null, stream = null))).isEqualTo(UNKNOWN)
        assertThat(Health.localState(LocalHealth(catalogue = ok, stream = null))).isEqualTo(OK)
        assertThat(Health.localState(LocalHealth(catalogue = ok, stream = ok))).isEqualTo(OK)
        assertThat(Health.localState(LocalHealth(catalogue = ok, stream = ko))).isEqualTo(KO)
        assertThat(Health.localState(LocalHealth(catalogue = ko, stream = null))).isEqualTo(KO)
    }

    @Test
    fun successReplacesFailure() {
        val store = InMemoryLocalHealthStore()
        store.record("tf1", LocalHealthStore.Kind.STREAM, ok = false, error = "boom", at = 1, source = "tf1")
        store.record("tf1", LocalHealthStore.Kind.STREAM, ok = true, error = null, at = 2, source = "tf1")

        assertThat(store.read("tf1").stream).isEqualTo(Outcome(ok = true, at = 2, error = null))
        assertThat(Health.localState(store.read("tf1"))).isEqualTo(OK)
        assertThat(store.read("francetv")).isEqualTo(LocalHealth(null, null))
    }

    @Test
    fun outcomeEncodingRoundTrips() {
        val outcome = Outcome(ok = false, at = 1_700_000_000_000, error = "Tf1AuthException: a|b")
        assertThat(LocalHealthStore.decode(LocalHealthStore.encode(outcome))).isEqualTo(outcome)
        val ok = Outcome(ok = true, at = 5, error = null)
        assertThat(LocalHealthStore.decode(LocalHealthStore.encode(ok))).isEqualTo(ok)
        assertThat(LocalHealthStore.decode("garbage")).isNull()
    }
}
