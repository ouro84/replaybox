package com.replaybox.core.model

data class Progress(
    val episodeId: EpisodeId,
    val positionSeconds: Long,
    val updatedAtEpochMs: Long,
)
