package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Playback errors: one silent retry, then the error screen (decision Round 8 Q2). */
class PlaybackRecoveryTest {

    @Test
    fun `first error retries silently, the second shows the error`() {
        val recovery = PlaybackRecovery()
        assertThat(recovery.onError(1_000L)).isEqualTo(PlaybackRecovery.Action.RETRY)
        assertThat(recovery.onError(1_000L)).isEqualTo(PlaybackRecovery.Action.SHOW_ERROR)
        assertThat(recovery.onError(1_000L)).isEqualTo(PlaybackRecovery.Action.SHOW_ERROR)
    }

    @Test
    fun `playing again earns a new silent retry`() {
        val recovery = PlaybackRecovery()
        recovery.onError(1_000L)
        recovery.onPlaying()
        assertThat(recovery.onError(1_000L)).isEqualTo(PlaybackRecovery.Action.RETRY)
    }

    @Test
    fun `Réessayer starts a fresh budget`() {
        val recovery = PlaybackRecovery()
        recovery.onError(1_000L)
        recovery.onError(1_000L)
        recovery.onUserRetry()
        assertThat(recovery.onError(1_000L)).isEqualTo(PlaybackRecovery.Action.RETRY)
    }

    @Test
    fun `the resume position follows the last error, 0 included`() {
        val recovery = PlaybackRecovery()
        assertThat(recovery.resumeAtMs).isNull()
        recovery.onError(1_800_000L)
        assertThat(recovery.resumeAtMs).isEqualTo(1_800_000L)
        recovery.onPlaying()
        recovery.onError(0L) // « Recommencer » then a failure: back to 0, not 30:00
        assertThat(recovery.resumeAtMs).isEqualTo(0L)
    }

    @Test
    fun `an error while the resume prompt is pending keeps no position`() {
        val recovery = PlaybackRecovery()
        recovery.onError(null)
        assertThat(recovery.resumeAtMs).isNull()
    }
}
