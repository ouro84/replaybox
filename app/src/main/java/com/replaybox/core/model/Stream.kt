package com.replaybox.core.model

enum class StreamType {
    DASH,
    HLS,
}

/**
 * Flux: a playable manifest with a short-lived URL. When the Chaîne gates the
 * content behind Widevine, licenseUrl + licenseHeaders are set.
 *
 * France TV serves captions side-loaded (`meta.subtitles`, WebVTT) and strips
 * text tracks from the manifest itself (SPEC): subtitleUrl carries that track
 * so the player can attach it as a SubtitleConfiguration.
 */
data class Stream(
    val manifestUrl: String,
    val type: StreamType,
    val licenseUrl: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null,
    val subtitleLanguage: String? = null,
)
