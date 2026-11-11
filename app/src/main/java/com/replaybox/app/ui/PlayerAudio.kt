package com.replaybox.app.ui

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * What the player tells the system it plays (mobile milestone, approved 2026-11-10 for the box
 * too): a movie on the media stream, so it takes the audio focus. Another app playing sound
 * pauses it (PauseReveal shows the controls). A ringing call only suppresses playback (Media3
 * keeps playWhenReady and resumes after the call): [callReveals] shows the controls then, and
 * [playing] lets OK or the mobile tap pause it for good.
 */
object PlayerAudio {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    /**
     * What OK and the mobile tap treat as playing: the intent to play ([playWhenReady], so a
     * stall, the initial load or a seek while playing pauses too), or held silent by a call.
     */
    fun playing(playWhenReady: Boolean, transientFocusLoss: Boolean): Boolean = playWhenReady || transientFocusLoss

    /** A call starts (transient focus loss): the controls show, under the same rules as a pause. */
    fun callReveals(transientFocusLoss: Boolean, resumePrompt: Boolean, nextUp: Boolean, optionsOpen: Boolean): Boolean =
        transientFocusLoss &&
            PauseReveal.shows(playWhenReady = false, endOfMedia = false, resumePrompt = resumePrompt, nextUp = nextUp, optionsOpen = optionsOpen)

    /** Unplugged headphones pause on mobile; never on the box, where HDMI counts as « noisy ». */
    fun pausesWhenNoisy(mobile: Boolean): Boolean = mobile
}
