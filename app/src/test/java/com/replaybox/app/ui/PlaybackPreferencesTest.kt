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

    @Test
    fun `subtitles default to on, French`() {
        assertThat(PlaybackPreferences().subtitlesOn).isTrue()
        assertThat(PlaybackPreferences().subtitleKind).isEqualTo(SubtitleKind.FRENCH)
        assertThat(PlaybackPreferences.decode(null, -1)).isEqualTo(PlaybackPreferences())
    }

    @Test
    fun `the subtitle preference round-trips`() {
        val prefs = PlaybackPreferences(subtitlesOn = false, subtitleKind = SubtitleKind.HEARING_IMPAIRED)
        assertThat(PlaybackPreferences.decode(prefs.encodeAudio(), prefs.encodeHeight(), prefs.subtitlesOn, prefs.encodeSubtitleKind()))
            .isEqualTo(prefs)
        assertThat(PlaybackPreferences.decode(null, -1, true, "OTHER").subtitleKind).isEqualTo(SubtitleKind.FRENCH)
        assertThat(PlaybackPreferences.decode(null, -1, true, "GONE").subtitleKind).isEqualTo(SubtitleKind.FRENCH)
    }

    @Test
    fun `turning subtitles off keeps the kind`() {
        val prefs = PlaybackPreferences(subtitleKind = SubtitleKind.HEARING_IMPAIRED).withSubtitle(null, hasTextTracks = true)
        assertThat(prefs.subtitlesOn).isFalse()
        assertThat(prefs.subtitleKind).isEqualTo(SubtitleKind.HEARING_IMPAIRED)
    }

    @Test
    fun `choosing a kind saves it and turns them on`() {
        val prefs = PlaybackPreferences(subtitlesOn = false).withSubtitle(SubtitleKind.HEARING_IMPAIRED, hasTextTracks = true)
        assertThat(prefs.subtitlesOn).isTrue()
        assertThat(prefs.subtitleKind).isEqualTo(SubtitleKind.HEARING_IMPAIRED)
    }

    @Test
    fun `another language is a one-off`() {
        val prefs = PlaybackPreferences(subtitlesOn = false, subtitleKind = SubtitleKind.HEARING_IMPAIRED)
            .withSubtitle(SubtitleKind.OTHER, hasTextTracks = true)
        assertThat(prefs.subtitlesOn).isTrue()
        assertThat(prefs.subtitleKind).isEqualTo(SubtitleKind.HEARING_IMPAIRED)
    }

    @Test
    fun `an Épisode without subtitles saves nothing`() {
        val prefs = PlaybackPreferences(subtitlesOn = false, subtitleKind = SubtitleKind.HEARING_IMPAIRED)
        assertThat(prefs.withSubtitle(null, hasTextTracks = false)).isEqualTo(prefs)
        assertThat(prefs.withSubtitle(SubtitleKind.FRENCH, hasTextTracks = false)).isEqualTo(prefs)
    }
}
