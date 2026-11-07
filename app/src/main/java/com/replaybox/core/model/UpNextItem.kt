package com.replaybox.core.model

/**
 * One card of the Home « À suivre » rail (ROADMAP §M4): an Épisode in progress, or the next
 * Épisode of a pinned Programme. One card per Programme.
 */
sealed interface UpNextItem {
    val showId: ShowId
    val showTitle: String
    val episodeId: EpisodeId
    val episodeTitle: String
    val thumbnailUrl: String?
    val backdropUrl: String?
    val channelId: String?

    /** The event the rail sorts on, newest first: last playback, or broadcast / publication. */
    val sortAtEpochMs: Long

    /** An Épisode in progress (« Reprendre »). */
    data class InProgress(val resume: ResumeItem) : UpNextItem {
        override val showId get() = resume.showId
        override val showTitle get() = resume.showTitle
        override val episodeId get() = resume.episodeId
        override val episodeTitle get() = resume.episodeTitle
        override val thumbnailUrl get() = resume.thumbnailUrl
        override val backdropUrl get() = resume.backdropUrl
        override val channelId get() = resume.channelId
        override val sortAtEpochMs get() = resume.updatedAtEpochMs
    }

    /** The next Épisode of a pinned Programme, played from the start (« Lecture »). */
    data class NewEpisode(
        override val showId: ShowId,
        override val showTitle: String,
        val episode: Episode,
        /** Number of the Saison listing it, for the « S2 E4 » label; null when unnumbered. */
        val seasonNumber: Int?,
        override val backdropUrl: String?,
        override val channelId: String?,
        override val sortAtEpochMs: Long,
    ) : UpNextItem {
        override val episodeId get() = episode.id
        override val episodeTitle get() = episode.title
        override val thumbnailUrl get() = episode.thumbnailUrl ?: backdropUrl
    }
}
