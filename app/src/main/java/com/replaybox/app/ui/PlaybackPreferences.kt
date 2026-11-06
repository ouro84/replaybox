package com.replaybox.app.ui

import android.content.Context

/**
 * What the player remembers between Épisodes (decision Q3): the audio kind
 * (Français, Version originale, Audiodescription) and the quality cap.
 * Speed is not remembered; subtitles always start on French (Q7).
 */
data class PlaybackPreferences(val audio: AudioKind? = null, val maxHeight: Int? = null) {

    /** Another language is a one-off choice, not a preference. */
    fun withAudio(kind: AudioKind): PlaybackPreferences = copy(audio = kind.takeIf { it != AudioKind.OTHER })

    fun encodeAudio(): String? = audio?.name

    fun encodeHeight(): Int = maxHeight ?: NO_CAP

    companion object {
        private const val NO_CAP = -1

        fun decode(audio: String?, maxHeight: Int): PlaybackPreferences = PlaybackPreferences(
            audio = AudioKind.values().firstOrNull { it.name == audio && it != AudioKind.OTHER },
            maxHeight = maxHeight.takeIf { it > 0 },
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
        PlaybackPreferences.decode(prefs.getString(KEY_AUDIO, null), prefs.getInt(KEY_HEIGHT, -1))

    override fun save(preferences: PlaybackPreferences) {
        prefs.edit()
            .putString(KEY_AUDIO, preferences.encodeAudio())
            .putInt(KEY_HEIGHT, preferences.encodeHeight())
            .apply()
    }

    private companion object {
        const val KEY_AUDIO = "audio"
        const val KEY_HEIGHT = "max_height"
    }
}
