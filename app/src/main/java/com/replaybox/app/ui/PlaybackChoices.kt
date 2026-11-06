package com.replaybox.app.ui

/** Track kinds the Options panel reads. */
enum class TrackKind { AUDIO, TEXT, VIDEO }

/**
 * One track the player really has (decision Q10: nothing invented), free of
 * Media3 types so the choice rules stay unit-testable. [group] and [index]
 * point back into `Player.currentTracks`.
 */
data class TrackRef(
    val type: TrackKind,
    val group: Int,
    val index: Int,
    val language: String?,
    val label: String?,
    val describesVideo: Boolean = false,
    val height: Int = 0,
)

/** Audio choices in display order (Q6). */
enum class AudioKind { FRENCH, ORIGINAL, DESCRIPTION, OTHER }

/** Subtitle choices in display order (Q7). */
enum class SubtitleKind { FRENCH, HEARING_IMPAIRED, OTHER }

data class AudioChoice(val kind: AudioKind, val track: TrackRef)

data class SubtitleChoice(val kind: SubtitleKind, val track: TrackRef)

/**
 * Rules of the Options panel (decisions Q3-Q8). France TV codes, measured on
 * seven titles: audio `fr` (main), `qaa` (original version), `qad`
 * (description); subtitles `fr` and `qsm` (hearing impaired).
 */
object PlaybackChoices {
    val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

    /** Lowest height offered as a manual cap (Q8). */
    const val MIN_HEIGHT = 576

    private val FRENCH = Regex("^fr(a|e)?([-_].*)?$")

    private fun isFrench(language: String?) = language != null && FRENCH.matches(language.lowercase())

    private fun audioKind(track: TrackRef): AudioKind {
        val language = track.language?.lowercase()
        return when {
            track.describesVideo || language == "qad" -> AudioKind.DESCRIPTION
            language == "qaa" -> AudioKind.ORIGINAL
            isFrench(language) -> AudioKind.FRENCH
            else -> AudioKind.OTHER
        }
    }

    private fun subtitleKind(track: TrackRef): SubtitleKind {
        val language = track.language?.lowercase()
        return when {
            language == "qsm" -> SubtitleKind.HEARING_IMPAIRED
            isFrench(language) -> SubtitleKind.FRENCH
            else -> SubtitleKind.OTHER
        }
    }

    /** One choice per kind (per language for OTHER), the first group wins. */
    fun audio(tracks: List<TrackRef>): List<AudioChoice> =
        tracks.filter { it.type == TrackKind.AUDIO }
            .map { AudioChoice(audioKind(it), it) }
            .distinctBy { if (it.kind == AudioKind.OTHER) "other:${it.track.language}" else it.kind.name }
            .sortedBy { it.kind.ordinal }

    /** The remembered kind when this Flux has it, else Français, else the first. */
    fun defaultAudio(choices: List<AudioChoice>, preferred: AudioKind?): AudioChoice? =
        choices.firstOrNull { it.kind == preferred && preferred != AudioKind.OTHER }
            ?: choices.firstOrNull { it.kind == AudioKind.FRENCH }
            ?: choices.firstOrNull()

    /** One choice per kind; the side-loaded French track dedupes against the manifest one. */
    fun subtitles(tracks: List<TrackRef>): List<SubtitleChoice> =
        tracks.filter { it.type == TrackKind.TEXT }
            .map { SubtitleChoice(subtitleKind(it), it) }
            .distinctBy { if (it.kind == SubtitleKind.OTHER) "other:${it.track.language}" else it.kind.name }
            .sortedBy { it.kind.ordinal }

    /** Q7: French whenever available, then hearing impaired, then the first track. */
    fun defaultSubtitle(choices: List<SubtitleChoice>): SubtitleChoice? = choices.minByOrNull { it.kind.ordinal }

    /** Manual caps: distinct heights from [MIN_HEIGHT] up, highest first. */
    fun qualities(tracks: List<TrackRef>): List<Int> =
        tracks.filter { it.type == TrackKind.VIDEO && it.height >= MIN_HEIGHT }
            .map { it.height }
            .distinct()
            .sortedDescending()

    /** The step shown as selected for [cap]: null is Auto; otherwise the largest step under the cap, or the lowest. */
    fun selectedQuality(qualities: List<Int>, cap: Int?): Int? {
        if (cap == null || qualities.isEmpty()) return null
        return qualities.filter { it <= cap }.maxOrNull() ?: qualities.min()
    }
}
