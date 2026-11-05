package com.replaybox.core.model

data class Show(
    val id: ShowId,
    val title: String,
    val posterUrl: String?,
    val synopsis: String?,
    val genres: Set<Genre>,
)
