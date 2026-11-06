package com.replaybox.app.ui

/**
 * Playback errors (decision Round 8 Q2): the first error re-resolves the
 * Flux silently and resumes at the last position (a signed URL may simply
 * have expired); a second one in a row shows « Flux indisponible ». Playing
 * again, or the user's « Réessayer », restores the silent retry.
 */
class PlaybackRecovery {
    enum class Action { RETRY, SHOW_ERROR }

    private var failures = 0

    fun onError(): Action {
        failures += 1
        return if (failures == 1) Action.RETRY else Action.SHOW_ERROR
    }

    fun onPlaying() {
        failures = 0
    }

    fun onUserRetry() {
        failures = 0
    }
}
