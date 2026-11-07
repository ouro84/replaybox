package com.replaybox.provider.api

import com.replaybox.core.health.LocalHealthStore
import com.replaybox.core.model.Channel
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream
import kotlinx.coroutines.CancellationException

/**
 * Records the on-box Santé of a network Provider: the outcome of every
 * catalogue listing ([shows]) and Flux resolution ([stream]) goes to [store]
 * under [groupId], then the result or the error passes through unchanged.
 * Seasons, Épisodes and search are not tracked. A cancelled call (the user
 * left the screen) records nothing. Sits under [CachedProvider], so only real
 * network calls are counted, never cache hits.
 */
class HealthTrackingProvider(
    private val inner: Provider,
    private val groupId: String,
    private val store: LocalHealthStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : Provider {

    override val channel: Channel get() = inner.channel

    override suspend fun shows(genre: Genre?): List<Show> =
        track(LocalHealthStore.Kind.CATALOGUE) { inner.shows(genre) }

    override suspend fun seasons(show: ShowId): List<Season> = inner.seasons(show)

    override suspend fun episodes(season: SeasonId?): List<Episode> = inner.episodes(season)

    override suspend fun search(query: String): List<Show> = inner.search(query)

    override suspend fun stream(episode: EpisodeId): Stream =
        track(LocalHealthStore.Kind.STREAM) { inner.stream(episode) }

    private suspend fun <T> track(kind: LocalHealthStore.Kind, call: suspend () -> T): T {
        val result = try {
            call()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.record(groupId, kind, ok = false, error = describe(error), at = clock(), source = channel.id)
            throw error
        }
        store.record(groupId, kind, ok = true, error = null, at = clock(), source = channel.id)
        return result
    }

    companion object {
        private const val MAX_ERROR = 120

        /** « IOException: HTTP 503 », cut to 120 chars. */
        fun describe(error: Throwable): String {
            val name = error::class.java.simpleName
            val text = error.message?.let { "$name: $it" } ?: name
            return text.take(MAX_ERROR)
        }
    }
}
