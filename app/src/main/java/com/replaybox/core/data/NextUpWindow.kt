package com.replaybox.core.data

/**
 * When the Épisode suivant panel shows (M4 « Lecteur complet »): over the last
 * [WINDOW_MS] of the video, while it keeps playing underneath. No credit markers exist in
 * any Provider listing (SPEC), so the window is measured from the duration alone.
 *
 * « Annuler » (or Back) hides the panel and lets the Épisode finish. Leaving the window
 * (seeking back) forgets that choice, so playing into the window again shows the panel
 * again; a resume or a seek landing inside the window shows it at once.
 */
object NextUpWindow {
    const val WINDOW_MS: Long = 10_000

    /** True over the last [WINDOW_MS] of a video of known duration. */
    fun inWindow(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs - WINDOW_MS

    /** The panel shows: a next Épisode exists, the playhead is in the window, not cancelled. */
    fun visible(positionMs: Long, durationMs: Long, cancelled: Boolean, hasNext: Boolean): Boolean =
        hasNext && !cancelled && inWindow(positionMs, durationMs)

    /** The cancel holds only while the playhead stays in the window. */
    fun stillCancelled(positionMs: Long, durationMs: Long, cancelled: Boolean): Boolean =
        cancelled && inWindow(positionMs, durationMs)

    /** Whole seconds left before the next Épisode starts (the countdown), 0..10. */
    fun secondsLeft(positionMs: Long, durationMs: Long): Int {
        val left = (durationMs - positionMs).coerceIn(0, WINDOW_MS)
        return ((left + 999) / 1000).toInt()
    }
}
