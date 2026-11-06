package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Options panel choices from the tracks the player has (decisions Q5-Q8, Q10). */
class PlaybackChoicesTest {

    private fun audio(group: Int, language: String?, describesVideo: Boolean = false) =
        TrackRef(TrackKind.AUDIO, group, 0, language, label = null, describesVideo = describesVideo)

    private fun text(group: Int, language: String?) = TrackRef(TrackKind.TEXT, group, 0, language, label = null)

    private fun video(group: Int, index: Int, height: Int) = TrackRef(TrackKind.VIDEO, group, index, null, null, height = height)

    // Measured France TV set (VO + AD): fr main, qaa alternate, qad description.
    private val ftvAudio = listOf(audio(1, "fr"), audio(2, "qaa"), audio(3, "qad", describesVideo = true))

    @Test
    fun `audio choices follow Français, Version originale, Audiodescription`() {
        assertThat(PlaybackChoices.audio(ftvAudio.reversed()).map { it.kind })
            .containsExactly(AudioKind.FRENCH, AudioKind.ORIGINAL, AudioKind.DESCRIPTION).inOrder()
    }

    @Test
    fun `audio description is recognised by language or by role`() {
        assertThat(PlaybackChoices.audio(listOf(audio(1, "qad"))).single().kind).isEqualTo(AudioKind.DESCRIPTION)
        assertThat(PlaybackChoices.audio(listOf(audio(1, "fr", describesVideo = true))).single().kind).isEqualTo(AudioKind.DESCRIPTION)
    }

    @Test
    fun `one choice per audio kind, the first group wins`() {
        val choices = PlaybackChoices.audio(listOf(audio(1, "fr"), audio(4, "fra")))
        assertThat(choices).hasSize(1)
        assertThat(choices.single().track.group).isEqualTo(1)
    }

    @Test
    fun `unknown audio languages stay distinct`() {
        assertThat(PlaybackChoices.audio(listOf(audio(1, "en"), audio(2, "de"))).map { it.kind })
            .containsExactly(AudioKind.OTHER, AudioKind.OTHER)
    }

    @Test
    fun `default audio is the remembered kind when present, Français otherwise`() {
        val full = PlaybackChoices.audio(ftvAudio)
        assertThat(PlaybackChoices.defaultAudio(full, AudioKind.DESCRIPTION)?.kind).isEqualTo(AudioKind.DESCRIPTION)
        assertThat(PlaybackChoices.defaultAudio(full, null)?.kind).isEqualTo(AudioKind.FRENCH)
        val frOnly = PlaybackChoices.audio(listOf(audio(1, "fr")))
        assertThat(PlaybackChoices.defaultAudio(frOnly, AudioKind.ORIGINAL)?.kind).isEqualTo(AudioKind.FRENCH)
        assertThat(PlaybackChoices.defaultAudio(emptyList(), AudioKind.ORIGINAL)).isNull()
    }

    @Test
    fun `subtitles dedupe the side-loaded French track against the manifest one`() {
        val choices = PlaybackChoices.subtitles(listOf(text(5, "fr"), text(6, "qsm"), text(9, "fra")))
        assertThat(choices.map { it.kind }).containsExactly(SubtitleKind.FRENCH, SubtitleKind.HEARING_IMPAIRED).inOrder()
        assertThat(choices.first().track.group).isEqualTo(5)
    }

    @Test
    fun `default subtitles - French first, then hearing impaired, then the first track`() {
        assertThat(PlaybackChoices.defaultSubtitle(PlaybackChoices.subtitles(listOf(text(6, "qsm"), text(5, "fr"))))?.kind)
            .isEqualTo(SubtitleKind.FRENCH)
        assertThat(PlaybackChoices.defaultSubtitle(PlaybackChoices.subtitles(listOf(text(6, "qsm"))))?.kind)
            .isEqualTo(SubtitleKind.HEARING_IMPAIRED)
        assertThat(PlaybackChoices.defaultSubtitle(PlaybackChoices.subtitles(listOf(text(7, "de"))))?.kind)
            .isEqualTo(SubtitleKind.OTHER)
        assertThat(PlaybackChoices.defaultSubtitle(emptyList())).isNull()
    }

    @Test
    fun `qualities are the distinct heights from 576 up, highest first`() {
        val ladder = listOf(1080, 1080, 900, 720, 576, 432).mapIndexed { i, h -> video(0, i, h) }
        assertThat(PlaybackChoices.qualities(ladder)).containsExactly(1080, 900, 720, 576).inOrder()
        assertThat(PlaybackChoices.qualities(listOf(video(0, 0, 432)))).isEmpty()
    }

    @Test
    fun `the selected quality is the largest step under the cap`() {
        assertThat(PlaybackChoices.selectedQuality(listOf(1080, 720, 576), null)).isNull()
        assertThat(PlaybackChoices.selectedQuality(listOf(1080, 720, 576), 900)).isEqualTo(720)
        assertThat(PlaybackChoices.selectedQuality(listOf(1080, 720), 480)).isEqualTo(720)
        assertThat(PlaybackChoices.selectedQuality(emptyList(), 720)).isNull()
    }

    @Test
    fun `speeds are 0,75 to 1,5`() {
        assertThat(PlaybackChoices.SPEEDS).containsExactly(0.75f, 1f, 1.25f, 1.5f).inOrder()
        assertThat(PlaybackChoices.SPEEDS.map(PlaybackChoices::speedLabel))
            .containsExactly("0,75×", "1×", "1,25×", "1,5×").inOrder()
    }
}
