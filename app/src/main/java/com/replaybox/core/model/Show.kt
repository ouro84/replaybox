package com.replaybox.core.model

data class Show(
    val id: ShowId,
    val title: String,
    val posterUrl: String?,
    val synopsis: String?,
    val genres: Set<Genre>,
    /** Landscape 16:9 key art for the Home backdrop; null when the Chaîne ships none. */
    val backdropUrl: String? = null,
    /**
     * Épisodes the Chaîne published recently (TF1 `replayRecent.total`); ranks search ties.
     * 0 when the Chaîne does not say.
     */
    val recentEpisodeCount: Int = 0,
    /**
     * When the device cache first listed this Programme (epoch ms, ShowEntity.firstSeenAt), set
     * by the catalog cache on read; 0 from a Provider. The « Tout voir » recency key: no listing
     * carries the date of its latest Épisode (GenreGrid).
     */
    val firstSeenAt: Long = 0,
)
