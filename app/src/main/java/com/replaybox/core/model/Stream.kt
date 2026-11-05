package com.replaybox.core.model

enum class StreamType {
    DASH,
    HLS,
}

/**
 * Flux: a playable manifest with a short-lived URL. When the Chaîne gates the
 * content behind Widevine, licenseUrl + licenseHeaders are set.
 */
data class Stream(
    val manifestUrl: String,
    val type: StreamType,
    val licenseUrl: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
)
