package com.replaybox.core.model

data class Show(
    val id: ShowId,
    val title: String,
    val posterUrl: String?,
    val synopsis: String?,
    val genres: Set<Genre>,
    /** Landscape 16:9 key art for the Home backdrop; null when the Chaîne ships none. */
    val backdropUrl: String? = null,
)
