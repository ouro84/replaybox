package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** What the player remembers between Épisodes (decision Q3). */
class PlaybackPreferencesTest {

    @Test
    fun `French, original version and audio description are remembered`() {
        assertThat(PlaybackPreferences().withAudio(AudioKind.ORIGINAL).audio).isEqualTo(AudioKind.ORIGINAL)
        assertThat(PlaybackPreferences().withAudio(AudioKind.DESCRIPTION).audio).isEqualTo(AudioKind.DESCRIPTION)
    }

    @Test
    fun `another language is not remembered as a preference`() {
        assertThat(PlaybackPreferences(audio = AudioKind.ORIGINAL).withAudio(AudioKind.OTHER).audio).isNull()
    }

    @Test
    fun `the quality cap round-trips through storage values`() {
        val prefs = PlaybackPreferences(audio = AudioKind.DESCRIPTION, maxHeight = 720)
        assertThat(PlaybackPreferences.decode(prefs.encodeAudio(), prefs.encodeHeight())).isEqualTo(prefs)
        assertThat(PlaybackPreferences.decode(null, -1)).isEqualTo(PlaybackPreferences())
        assertThat(PlaybackPreferences.decode("GONE", -1)).isEqualTo(PlaybackPreferences())
    }
}
