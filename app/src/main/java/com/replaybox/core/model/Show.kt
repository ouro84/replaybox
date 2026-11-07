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
     * by the catalog cache on read; 0 from a Provider. The « Tout voir » fallback for a
     * Programme with no [recencyAt] that arrived after its Chaîne's first listing (GenreGrid).
     */
    val firstSeenAt: Long = 0,
    /**
     * Latest sign of a new Épisode the Chaîne's listing gives (epoch ms), the « Tout voir »
     * recency key (GenreGrid, SPEC per Provider, measured 2026-11-07): M6 the publication of
     * its latest full Épisode, France TV the online date of an Épisode its Chaîne page features,
     * TF1 the server instant of an Index crawl that counted recent Épisodes. Null when the
     * listing says nothing. The cache keeps the latest value seen across refreshes.
     */
    val recencyAt: Long? = null,
)
