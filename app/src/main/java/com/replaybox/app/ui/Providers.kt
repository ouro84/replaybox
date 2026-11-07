package com.replaybox.app.ui

import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.model.Channel
import com.replaybox.core.model.Show
import com.replaybox.provider.api.CachedProvider
import com.replaybox.provider.api.HealthTrackingProvider
import com.replaybox.provider.api.Provider
import com.replaybox.provider.francetv.FtvHttp
import com.replaybox.provider.francetv.FtvParsers
import com.replaybox.provider.francetv.FtvProvider
import com.replaybox.provider.m6.M6Channels
import com.replaybox.provider.m6.M6Http
import com.replaybox.provider.m6.M6Parsers
import com.replaybox.provider.m6.M6Provider
import com.replaybox.provider.m6.m6CatalogueIndex
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
 * Each network Provider reports its Santé under its ProviderGroups id.
 *
 * The 5 M6 group Chaînes share one HTTP layer and one M6 Index, and reach [all]
 * only while an M6+ account is stored (their Flux needs it). [all] is read
 * anew on each access, so signing in or out in Paramètres changes it without
 * a restart; Home refreshes the listings of Chaînes it has not refreshed yet
 * when it comes back (HomeScreen).
 */
object Providers {
    private val ftvHttp = FtvHttp()

    val catalog: CatalogStore get() = ReplayboxApplication.catalog

    private val tf1Http = Tf1Http()
    private val tf1Index = tf1CatalogueIndex(tf1Http)

    private val tf1Group: List<CachedProvider> = Tf1Channels.GROUP.map { channel ->
        cached(
            ProviderGroups.TF1,
            Tf1Provider(
                http = tf1Http,
                tokenProvider = { forceRefresh -> ReplayboxApplication.tf1Auth.token(forceRefresh) },
                channel = channel,
                index = tf1Index,
                localSearch = { query -> tf1LocalSearch(channel.id, query) },
            ),
        )
    }

    /** Cache-first over the Santé tracker, so only real network calls feed the on-box Santé. */
    private fun cached(groupId: String, network: Provider): CachedProvider =
        CachedProvider(
            HealthTrackingProvider(network, groupId, ReplayboxApplication.localHealth),
            ReplayboxApplication.catalog,
        )

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
    ).map { cached(ProviderGroups.FRANCETV, it) }

    private val m6Http = M6Http()
    private val m6Index = m6CatalogueIndex(m6Http)

    /** M6 search is local only (decision Q17): no remote fallback before the first Index. */
    private val m6Group: List<CachedProvider> = M6Channels.GROUP.map { channel ->
        cached(
            ProviderGroups.M6,
            M6Provider(
                channel = channel,
                http = m6Http,
                index = m6Index,
                drmTokenProvider = { serviceCode, clipId ->
                    ReplayboxApplication.m6Auth.upfrontToken(serviceCode, clipId)
                },
                localSearch = { query -> ReplayboxApplication.catalog.searchShows(listOf(channel.id), query) },
            ),
        )
    }

    private val alwaysOn: List<Provider> = tf1Group + ftv

    /** Every listed Chaîne; the M6 ones only while an M6+ account is stored. */
    val all: List<Provider>
        get() = if (ReplayboxApplication.m6Auth.hasCredentials()) alwaysOn + m6Group else alwaysOn

    /**
     * Routes a namespaced id back to its provider: France TV `ftv::`, M6 `m6:` (Saisons,
     * Épisodes and Flux carry the M6 service in the id, so the first M6 provider serves them);
     * TF1 keeps bare ids, and its Seasons, Épisodes and Flux do not depend on the Chaîne: the
     * first TF1 provider serves them.
     */
    fun resolveShow(id: String): Provider = when {
        id.startsWith(FtvParsers.ID_PREFIX) -> ftv.first()
        id.startsWith(M6Parsers.ID_PREFIX) -> m6Group.first()
        else -> tf1Group.first()
    }

    fun resolveEpisode(id: String): Provider = resolveShow(id)
}
