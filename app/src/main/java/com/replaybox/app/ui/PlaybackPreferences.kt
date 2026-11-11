package com.replaybox.app.ui

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters

/**
 * What the player remembers between Épisodes: the audio kind (Q3), the quality
 * cap (Q3, Q8) and the subtitles, on/off and kind (PRD story 2, rewritten
 * 2026-11-11): on and French until the viewer chooses otherwise; another
 * language is a one-off. Speed is not remembered.
 */
data class PlaybackPreferences(
    val audio: AudioKind? = null,
    val maxHeight: Int? = null,
    val subtitlesOn: Boolean = true,
    /** Never [SubtitleKind.OTHER]: another language is a one-off. */
    val subtitleKind: SubtitleKind = SubtitleKind.FRENCH,
) {

    /** Another language is a one-off choice, not a preference. */
    fun withAudio(kind: AudioKind): PlaybackPreferences = copy(audio = kind.takeIf { it != AudioKind.OTHER })

    /**
     * The viewer chose [choice] (null is off). An Épisode without text tracks
     * saves nothing: nothing was really chosen. Off keeps the kind; another
     * language turns subtitles on and keeps the kind.
     */
    fun withSubtitle(choice: SubtitleKind?, hasTextTracks: Boolean): PlaybackPreferences = when {
        !hasTextTracks -> this
        choice == null -> copy(subtitlesOn = false)
        choice == SubtitleKind.OTHER -> copy(subtitlesOn = true)
        else -> copy(subtitlesOn = true, subtitleKind = choice)
    }

    /**
     * The track selection a Flux starts with: the quality cap as a ceiling (Q3, Q8), and the
     * text tracks disabled when subtitles are stored off, so the Flux's default text track
     * never flashes a cue before the stored choice applies. Choosing a subtitle re-enables
     * them (the player's `select`).
     */
    fun startParameters(base: TrackSelectionParameters): TrackSelectionParameters =
        base.buildUpon()
            .apply { maxHeight?.let { setMaxVideoSize(Int.MAX_VALUE, it) } }
            .apply { if (!subtitlesOn) setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }
            .build()

    fun encodeAudio(): String? = audio?.name

    fun encodeHeight(): Int = maxHeight ?: NO_CAP

    fun encodeSubtitleKind(): String = subtitleKind.name

    companion object {
        private const val NO_CAP = -1

        fun decode(
            audio: String?,
            maxHeight: Int,
            subtitlesOn: Boolean = true,
            subtitleKind: String? = null,
        ): PlaybackPreferences = PlaybackPreferences(
            audio = AudioKind.values().firstOrNull { it.name == audio && it != AudioKind.OTHER },
            maxHeight = maxHeight.takeIf { it > 0 },
            subtitlesOn = subtitlesOn,
            subtitleKind = listOf(SubtitleKind.FRENCH, SubtitleKind.HEARING_IMPAIRED)
                .firstOrNull { it.name == subtitleKind } ?: SubtitleKind.FRENCH,
        )
    }
}

interface PlaybackPreferenceStore {
    fun load(): PlaybackPreferences
    fun save(preferences: PlaybackPreferences)
}

/** Device-local, plain SharedPreferences: nothing sensitive in here. */
class SharedPrefsPlaybackPreferenceStore(context: Context) : PlaybackPreferenceStore {
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    override fun load(): PlaybackPreferences =
        PlaybackPreferences.decode(
            prefs.getString(KEY_AUDIO, null),
            prefs.getInt(KEY_HEIGHT, -1),
            prefs.getBoolean(KEY_SUBTITLES_ON, true),
            prefs.getString(KEY_SUBTITLE_KIND, null),
        )

    override fun save(preferences: PlaybackPreferences) {
        prefs.edit()
            .putString(KEY_AUDIO, preferences.encodeAudio())
            .putInt(KEY_HEIGHT, preferences.encodeHeight())
            .putBoolean(KEY_SUBTITLES_ON, preferences.subtitlesOn)
            .putString(KEY_SUBTITLE_KIND, preferences.encodeSubtitleKind())
            .apply()
    }

    private companion object {
        const val KEY_AUDIO = "audio"
        const val KEY_HEIGHT = "max_height"
        const val KEY_SUBTITLES_ON = "subtitles_on"
        const val KEY_SUBTITLE_KIND = "subtitle_kind"
    }
}
