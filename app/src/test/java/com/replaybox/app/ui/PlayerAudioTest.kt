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
}
