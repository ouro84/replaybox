package com.replaybox.app.ui

import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.model.Channel
import com.replaybox.provider.api.Provider
import com.replaybox.provider.francetv.FtvHttp
import com.replaybox.provider.francetv.FtvParsers
import com.replaybox.provider.francetv.FtvProvider
import com.replaybox.provider.tf1.Tf1Provider

/**
 * One Provider per Chaîne (glossary). The France TV instances share one HTTP
 * layer; their listing differs (france-2..franceinfo) while seasons, episodes
 * and Flux are Chaîne-independent (SPEC: program_path prefixes are navigation
 * slugs). Tf1Provider keeps its TF1+ account token provider.
 */
object Providers {
    private val ftvHttp = FtvHttp()

    val all: List<Provider> = listOf(
        Tf1Provider(tokenProvider = { forceRefresh -> ReplayboxApplication.tf1Auth.token(forceRefresh) }),
        FtvProvider("france-2", Channel("france-2", "France 2"), ftvHttp),
        FtvProvider("france-3", Channel("france-3", "France 3"), ftvHttp),
        FtvProvider("france-4", Channel("france-4", "France 4"), ftvHttp),
        FtvProvider("france-5", Channel("france-5", "France 5"), ftvHttp),
        FtvProvider("franceinfo", Channel("franceinfo", "franceinfo"), ftvHttp),
    )

    /** Routes a France TV namespaced id back to its provider; TF1 keeps bare ids. */
    fun resolveShow(id: String): Provider =
        all.first { id.startsWith(FtvParsers.ID_PREFIX) == (it is FtvProvider) }

    fun resolveEpisode(id: String): Provider = resolveShow(id)
}
