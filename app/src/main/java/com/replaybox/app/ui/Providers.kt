package com.replaybox.app.ui

import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.model.Channel
import com.replaybox.core.model.Show
import com.replaybox.provider.api.CachedProvider
import com.replaybox.provider.api.Provider
import com.replaybox.provider.francetv.FtvHttp
import com.replaybox.provider.francetv.FtvParsers
import com.replaybox.provider.francetv.FtvProvider
import com.replaybox.provider.tf1.Tf1Provider

/**
 * One Provider per Chaîne (glossary), wrapped cache-first (ADR 0002). The
 * France TV instances share one HTTP layer; their listing differs
 * (france-2..franceinfo) while seasons, episodes and Flux are
 * Chaîne-independent (SPEC: program_path prefixes are navigation slugs).
 * Tf1Provider keeps its TF1+ account token provider.
 */
object Providers {
    private val ftvHttp = FtvHttp()

    val catalog: CatalogStore get() = ReplayboxApplication.catalog

    private val tf1: CachedProvider = CachedProvider(
        Tf1Provider(tokenProvider = { forceRefresh -> ReplayboxApplication.tf1Auth.token(forceRefresh) }),
        ReplayboxApplication.catalog,
    )
    private val ftvSearch: (suspend (String) -> List<Show>) = { query ->
        ReplayboxApplication.catalog.searchShows(FTV_CHANNEL_IDS, query)
    }

    private val ftv: List<CachedProvider> = listOf(
        FtvProvider("france-2", Channel("france-2", "France 2"), ftvHttp, ftvSearch),
        FtvProvider("france-3", Channel("france-3", "France 3"), ftvHttp, ftvSearch),
        FtvProvider("france-4", Channel("france-4", "France 4"), ftvHttp, ftvSearch),
        FtvProvider("france-5", Channel("france-5", "France 5"), ftvHttp, ftvSearch),
        FtvProvider("franceinfo", Channel("franceinfo", "franceinfo"), ftvHttp, ftvSearch),
    ).map { CachedProvider(it, ReplayboxApplication.catalog) }

    val all: List<Provider> = listOf(tf1) + ftv

    private val FTV_CHANNEL_IDS = listOf("france-2", "france-3", "france-4", "france-5", "franceinfo")

    /** Routes a France TV namespaced id back to its provider; TF1 keeps bare ids. */
    fun resolveShow(id: String): Provider =
        if (id.startsWith(FtvParsers.ID_PREFIX)) ftv.first() else tf1

    fun resolveEpisode(id: String): Provider = resolveShow(id)
}
