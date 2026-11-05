package com.replaybox.core.data

import com.replaybox.core.model.Progress

/**
 * Resume decision (SPEC §Player): propose to resume only when it makes
 * sense — enough watched to matter, not already finished.
 */
object ResumePolicy {

    /** Below this, starting over is indistinguishable from resuming. */
    const val MIN_POSITION_SECONDS = 30L

    /** Past this share of the duration, the Épisode counts as finished. */
    const val FINISHED_RATIO = 0.95

    /** Playback position to resume at, or null when starting from zero. */
    fun resumeAt(progress: Progress?, durationSeconds: Int?): Long? {
        val saved = progress ?: return null
        if (saved.positionSeconds < MIN_POSITION_SECONDS) return null
        val duration = durationSeconds ?: return saved.positionSeconds * 1000
        if (duration <= 0) return null
        if (saved.positionSeconds >= duration * FINISHED_RATIO) return null
        return saved.positionSeconds * 1000
    }
}
