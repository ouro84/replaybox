package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId

/** An Épisode together with the Saison it is listed in (null when not known). */
data class SeasonEpisode(val episode: Episode, val season: Season?)

/**
 * Play order of a Programme (M4 « À suivre », Épisode suivant). Only numbered Saisons have
 * one: Saisons by number, ascending, whatever order the Chaîne lists them in (TF1, France TV
 * and M6 list the newest first), and within a Saison the Épisodes by number, ascending (TF1
 * and France TV list the newest first too). An unnumbered magazine Saison has no « next »:
 * the following row would be an older emission, not the continuation.
 */
object NextEpisodePolicy {

    /** Next Épisode of an ordered [queue]; null at its end or when [current] is not in it. */
    fun next(queue: List<Episode>, current: EpisodeId): Episode? {
        val index = queue.indexOfFirst { it.id == current }
        return if (index < 0) null else queue.getOrNull(index + 1)
    }

    /**
     * Play order of one numbered Saison: by Épisode number, ascending; Épisodes without a
     * number follow, in the Chaîne's order (the sort is stable, so M6 parts stay in order).
     */
    fun playOrder(episodes: List<Episode>): List<Episode> =
        episodes.sortedWith(compareBy(nullsLast()) { it.number })

    /** The numbered Saisons, by number ascending. */
    fun numberedSeasons(seasons: List<Season>): List<Season> =
        seasons.filter { it.number != null }.sortedBy { it.number }

    /** True when the Programme numbers its Saisons: a series rather than a magazine. */
    fun isSeries(seasons: List<Season>): Boolean = seasons.any { it.number != null }

    /**
     * Every Épisode of the numbered Saisons in play order, Saison after Saison. Saisons
     * missing from [episodesBySeason] (not loaded) are skipped.
     */
    fun programmeOrder(seasons: List<Season>, episodesBySeason: Map<SeasonId, List<Episode>>): List<SeasonEpisode> =
        numberedSeasons(seasons).flatMap { season ->
            playOrder(episodesBySeason[season.id].orEmpty()).map { SeasonEpisode(it, season) }
        }

    /**
     * Épisode suivant of [current]: the next one in its numbered Saison, else the first
     * Épisode of the following numbered Saison that lists any. Null after the last Épisode of
     * the last Saison, for an Épisode of an unnumbered Saison, or when [current] is unknown.
     */
    fun next(
        seasons: List<Season>,
        episodesBySeason: Map<SeasonId, List<Episode>>,
        current: EpisodeId,
    ): SeasonEpisode? {
        val order = programmeOrder(seasons, episodesBySeason)
        val index = order.indexOfFirst { it.episode.id == current }
        return if (index < 0) null else order.getOrNull(index + 1)
    }

    /**
     * The next unwatched Épisode after the last watched one, in play order across numbered
     * Saisons. Null when nothing was watched, or when every Épisode after it is watched.
     */
    fun nextUnwatched(order: List<SeasonEpisode>, watched: Set<EpisodeId>): SeasonEpisode? {
        val last = order.indexOfLast { it.episode.id in watched }
        if (last < 0) return null
        return order.drop(last + 1).firstOrNull { it.episode.id !in watched }
    }
}
