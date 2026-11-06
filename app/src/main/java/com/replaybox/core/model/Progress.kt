package com.replaybox.core.model

data class Progress(
    val episodeId: EpisodeId,
    val positionSeconds: Long,
    val updatedAtEpochMs: Long,
)

/**
 * What the Home "Reprendre" rail needs to render a Progression without a
 * network call: the Programme it belongs to and the Épisode's own art.
 * Captured by the player when the Épisode is opened from a Programme.
 */
data class ProgressContext(
    val showId: ShowId,
    val showTitle: String,
    val episodeTitle: String,
    val thumbnailUrl: String?,
)

/** One card of the "Reprendre" rail: the latest unfinished Épisode of a Programme. */
data class ResumeItem(
    val episodeId: EpisodeId,
    val showId: ShowId,
    val showTitle: String,
    val episodeTitle: String,
    val thumbnailUrl: String?,
    val backdropUrl: String?,
    val channelId: String?,
    val positionSeconds: Long,
    val durationSeconds: Int?,
    val updatedAtEpochMs: Long,
) {
    /** Seconds left to watch, or null when the duration is unknown. */
    val remainingSeconds: Long?
        get() = durationSeconds?.let { (it - positionSeconds).coerceAtLeast(0) }

    /** Watched share in 0..1, or null when the duration is unknown. */
    val watchedRatio: Float?
        get() = durationSeconds?.takeIf { it > 0 }?.let { (positionSeconds.toFloat() / it).coerceIn(0f, 1f) }
}

/** A cached Programme together with the Chaîne listing it came from. */
data class ChannelShow(val show: Show, val channelId: String)

/**
 * Progression of one Épisode on the Programme page. [watched] is decided by
 * ResumePolicy (finished share), so the model stays free of that rule.
 */
data class EpisodeProgress(
    val positionSeconds: Long,
    val durationSeconds: Int?,
    val watched: Boolean,
) {
    /** Seconds left to watch, or null when the duration is unknown. */
    val remainingSeconds: Long?
        get() = durationSeconds?.let { (it - positionSeconds).coerceAtLeast(0) }

    /** Watched share in 0..1, or null when the duration is unknown. */
    val watchedRatio: Float?
        get() = durationSeconds?.takeIf { it > 0 }?.let { (positionSeconds.toFloat() / it).coerceIn(0f, 1f) }
}
