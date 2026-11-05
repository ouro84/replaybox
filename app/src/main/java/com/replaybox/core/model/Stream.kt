package com.replaybox.core.model

enum class StreamType {
    DASH,
    HLS,
}

data class Stream(
    val manifestUrl: String,
    val type: StreamType,
)
