package com.replaybox.app.ui

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * What the player tells the system it plays (mobile milestone, approved 2026-11-10 for the box
 * too): a movie on the media stream, so it takes the audio focus. A call or another app playing
 * sound pauses it; the pause then shows the controls (PauseReveal).
 */
object PlayerAudio {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
}
