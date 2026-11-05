package com.replaybox.core.model

data class Season(
    val id: SeasonId,
    val showId: ShowId,
    val number: Int?,
    val title: String,
)
