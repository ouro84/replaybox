package com.replaybox.app.ui

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerAudioTest {

    @Test
    fun mediaMovieAttributes() {
        assertThat(PlayerAudio.attributes.usage).isEqualTo(C.USAGE_MEDIA)
        assertThat(PlayerAudio.attributes.contentType).isEqualTo(C.AUDIO_CONTENT_TYPE_MOVIE)
    }

    @Test
    fun aCallCountsAsPlayingForTheToggle() {
        // Ringing call: Media3 keeps playWhenReady, suppresses playback; OK or a tap must pause.
        assertThat(PlayerAudio.playing(playWhenReady = false, transientFocusLoss = true)).isTrue()
        assertThat(PlayerAudio.playing(playWhenReady = false, transientFocusLoss = false)).isFalse()
    }

    @Test
    fun bufferingWithTheIntentToPlayCountsAsPlaying() {
        // Initial load, a stall or a seek while playing: isPlaying is false, playWhenReady true.
        // The toggle decides on the intent, so OK or a tap pauses instead of a no-op play().
        assertThat(PlayerAudio.playing(playWhenReady = true, transientFocusLoss = false)).isTrue()
    }

    @Test
    fun aCallRevealsTheControlsLikeAPause() {
        assertThat(PlayerAudio.callReveals(transientFocusLoss = true, resumePrompt = false, nextUp = false, optionsOpen = false)).isTrue()
        assertThat(PlayerAudio.callReveals(transientFocusLoss = false, resumePrompt = false, nextUp = false, optionsOpen = false)).isFalse()
        assertThat(PlayerAudio.callReveals(transientFocusLoss = true, resumePrompt = false, nextUp = false, optionsOpen = true)).isFalse()
    }

    @Test
    fun becomingNoisyOnlyOnMobile() {
        // HDMI counts as a noisy device: the box must not pause when the TV drops it.
        assertThat(PlayerAudio.pausesWhenNoisy(mobile = false)).isFalse()
        assertThat(PlayerAudio.pausesWhenNoisy(mobile = true)).isTrue()
    }
}
