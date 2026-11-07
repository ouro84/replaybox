package com.replaybox.provider.tf1

import com.replaybox.core.model.Channel

/**
 * The TF1 group Chaînes served by the TF1 Index, keyed on the programs' `initialChannel.slug`
 * (SPEC §Provider TF1+, measured 2026-11-07). Every other initialChannel of the unfiltered
 * catalogue (arte, novo19, public-senat…) is not a TF1 Chaîne and is dropped.
 */
object Tf1Channels {
    val GROUP: List<Channel> = listOf(
        Channel("tf1", "TF1"),
        Channel("tmc", "TMC"),
        Channel("tfx", "TFX"),
        Channel("tf1-series-films", "TF1 Séries Films"),
        Channel("lci", "LCI"),
    )

    private val ids: Set<String> = GROUP.mapTo(HashSet()) { it.id }

    fun isGroup(channelId: String?): Boolean = channelId != null && channelId in ids
}
