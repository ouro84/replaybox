package com.replaybox.core.model

data class Episode(
    val id: EpisodeId,
    val seasonId: SeasonId?,
    val title: String,
    val synopsis: String?,
    val broadcastDate: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
)
