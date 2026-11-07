package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.UpNextItem
import java.time.Duration
import java.time.Instant

/**
 * The Home « À suivre » rail (ROADMAP §M4): Épisodes in progress, plus for each pinned
 * Programme its next unwatched Épisode, one card per Programme, most recent event first.
 */
object UpNextPolicy {

    /** A magazine offers its newest unwatched Épisode only when broadcast this recently. */
    val MAGAZINE_WINDOW: Duration = Duration.ofDays(7)

    /** The date an Épisode reached the viewer: its broadcast, else its publication. */
    fun eventAt(episode: Episode): Instant? = episode.broadcastAt ?: episode.publishedAt

    /**
     * The new Épisode a pinned Programme offers, or null. A series (numbered Saisons) offers
     * the Épisode after the last watched one, only once one was watched; a magazine offers its
     * newest unwatched Épisode broadcast (else published) within [MAGAZINE_WINDOW]. An expired
     * Épisode is never offered (a watched expired Épisode still marks where the viewer is).
     */
    fun candidate(
        seasons: List<Season>,
        episodesBySeason: Map<SeasonId, List<Episode>>,
        watched: Set<EpisodeId>,
        now: Instant,
    ): SeasonEpisode? {
        if (NextEpisodePolicy.isSeries(seasons)) {
            val order = NextEpisodePolicy.programmeOrder(seasons, episodesBySeason)
            val last = order.indexOfLast { it.episode.id in watched }
            if (last < 0) return null
            return order.drop(last + 1).firstOrNull {
                it.episode.id !in watched && !FreshnessPolicy.isExpired(it.episode, now)
            }
        }
        val since = now.minus(MAGAZINE_WINDOW)
        return seasons.flatMap { season -> episodesBySeason[season.id].orEmpty().map { SeasonEpisode(it, season) } }
            .filter { (episode) ->
                val at = eventAt(episode)
                episode.id !in watched && at != null && !at.isBefore(since) && !at.isAfter(now) &&
                    !FreshnessPolicy.isExpired(episode, now)
            }
            .maxByOrNull { eventAt(it.episode)!! }
    }

    /**
     * The rail: every in-progress item, then the new Épisodes of Programmes without one,
     * minus those dismissed (Programme id → Épisode id: a dismissal holds only for that
     * Épisode), newest event first, at most [limit] cards.
     */
    fun merge(
        inProgress: List<UpNextItem.InProgress>,
        fresh: List<UpNextItem.NewEpisode>,
        dismissed: Map<String, String>,
        limit: Int,
    ): List<UpNextItem> {
        val claimed = inProgress.mapTo(HashSet()) { it.showId }
        val offered = fresh.filter { it.showId !in claimed && dismissed[it.showId.value] != it.episodeId.value }
            .distinctBy { it.showId }
        return (inProgress + offered).sortedByDescending { it.sortAtEpochMs }.take(limit)
    }
}
