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
import com.replaybox.provider.tf1.Tf1Channels
import com.replaybox.provider.tf1.Tf1Http
import com.replaybox.provider.tf1.Tf1Provider
import com.replaybox.provider.tf1.tf1CatalogueIndex

/**
 * One Provider per Chaîne (glossary), wrapped cache-first (ADR 0002). The
 * France TV instances share one HTTP layer; their listing differs
 * (france-2..franceinfo) while seasons, episodes and Flux are
 * Chaîne-independent (SPEC: program_path prefixes are navigation slugs).
 * The 5 TF1 group Chaînes share one HTTP layer and one TF1 Index (a single
 * crawl feeds every listing); each keeps the TF1+ account token provider.
 */
object Providers {
    private val ftvHttp = FtvHttp()

    val catalog: CatalogStore get() = ReplayboxApplication.catalog

    private val tf1Http = Tf1Http()
    private val tf1Index = tf1CatalogueIndex(tf1Http)

    private val tf1Group: List<CachedProvider> = Tf1Channels.GROUP.map { channel ->
        CachedProvider(
            Tf1Provider(
                http = tf1Http,
                tokenProvider = { forceRefresh -> ReplayboxApplication.tf1Auth.token(forceRefresh) },
                channel = channel,
                index = tf1Index,
                localSearch = { query -> tf1LocalSearch(channel.id, query) },
            ),
            ReplayboxApplication.catalog,
        )
    }

    /**
     * TF1 search is local over each Chaîne's stored Index slice (SPEC §Recherche).
     * Before the first Index is stored, Chaîne `tf1` alone asks TF1's remote search
     * (null = fall back) so the remote call happens once, not five times.
     */
    private suspend fun tf1LocalSearch(channelId: String, query: String): List<Show>? {
        val catalog = ReplayboxApplication.catalog
        if (catalog.listingAt(channelId) == null) return if (channelId == "tf1") null else emptyList()
        return catalog.searchShows(listOf(channelId), query)
    }
    private val ftvSearch: (suspend (String) -> List<Show>) = { query ->
        ReplayboxApplication.catalog.searchShows(CatalogStore.FTV_CHANNEL_IDS, query)
    }

    private val ftv: List<CachedProvider> = listOf(
        FtvProvider("france-2", Channel("france-2", "France 2"), ftvHttp, ftvSearch),
        FtvProvider("france-3", Channel("france-3", "France 3"), ftvHttp, ftvSearch),
        FtvProvider("france-4", Channel("france-4", "France 4"), ftvHttp, ftvSearch),
        FtvProvider("france-5", Channel("france-5", "France 5"), ftvHttp, ftvSearch),
        FtvProvider("franceinfo", Channel("franceinfo", "franceinfo"), ftvHttp, ftvSearch),
    ).map { CachedProvider(it, ReplayboxApplication.catalog) }

    val all: List<Provider> = tf1Group + ftv

    /**
     * Routes a France TV namespaced id back to its provider; TF1 keeps bare ids, and its
     * Seasons, Épisodes and Flux do not depend on the Chaîne: the first TF1 provider serves them.
     */
    fun resolveShow(id: String): Provider =
        if (id.startsWith(FtvParsers.ID_PREFIX)) ftv.first() else tf1Group.first()

    fun resolveEpisode(id: String): Provider = resolveShow(id)
}
