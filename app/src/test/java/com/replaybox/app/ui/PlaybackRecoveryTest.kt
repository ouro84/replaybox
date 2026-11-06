package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Playback errors: one silent retry, then the error screen (decision Round 8 Q2). */
class PlaybackRecoveryTest {

    @Test
    fun `first error retries silently, the second shows the error`() {
        val recovery = PlaybackRecovery()
        assertThat(recovery.onError()).isEqualTo(PlaybackRecovery.Action.RETRY)
        assertThat(recovery.onError()).isEqualTo(PlaybackRecovery.Action.SHOW_ERROR)
        assertThat(recovery.onError()).isEqualTo(PlaybackRecovery.Action.SHOW_ERROR)
    }

    @Test
    fun `playing again earns a new silent retry`() {
        val recovery = PlaybackRecovery()
        recovery.onError()
        recovery.onPlaying()
        assertThat(recovery.onError()).isEqualTo(PlaybackRecovery.Action.RETRY)
    }

    @Test
    fun `Réessayer starts a fresh budget`() {
        val recovery = PlaybackRecovery()
        recovery.onError()
        recovery.onError()
        recovery.onUserRetry()
        assertThat(recovery.onError()).isEqualTo(PlaybackRecovery.Action.RETRY)
    }
}
