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
        // Ringing call: Media3 keeps playWhenReady, suppresses playback; Pause must pause.
        assertThat(PlayerAudio.playing(isPlaying = false, transientFocusLoss = true)).isTrue()
        assertThat(PlayerAudio.playing(isPlaying = true, transientFocusLoss = false)).isTrue()
        assertThat(PlayerAudio.playing(isPlaying = false, transientFocusLoss = false)).isFalse()
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
