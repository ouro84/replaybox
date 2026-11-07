package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId

/** What the primary button of the Programme hero plays. */
sealed interface HeroTarget {
    /** The Saison the target is listed in, selected when the page opens; null when unknown. */
    val season: Season?

    /** « Reprendre »: the Épisode in progress. */
    data class Resume(val item: ResumeItem, override val season: Season?) : HeroTarget

    /** « Lecture »: an Épisode from the start. */
    data class Play(val episode: Episode, override val season: Season?) : HeroTarget
}

/**
 * The Programme hero cascade (M4 « À suivre »): « Reprendre » on the Épisode in progress;
 * else « Lecture » on the next unwatched Épisode after the last watched one, across numbered
 * Saisons; else the first Épisode of the first Saison for a series, the newest Épisode for
 * a magazine. Watched is the Programme page rule (95 % or marked watched).
 */
object ProgrammeHeroPolicy {

    fun target(
        resume: ResumeItem?,
        seasons: List<Season>,
        episodesBySeason: Map<SeasonId, List<Episode>>,
        watched: Set<EpisodeId>,
    ): HeroTarget? {
        if (resume != null) return HeroTarget.Resume(resume, seasonOf(resume.episodeId, seasons, episodesBySeason))
        val order = NextEpisodePolicy.programmeOrder(seasons, episodesBySeason)
        NextEpisodePolicy.nextUnwatched(order, watched)?.let { return HeroTarget.Play(it.episode, it.season) }
        if (NextEpisodePolicy.isSeries(seasons)) {
            order.firstOrNull()?.let { return HeroTarget.Play(it.episode, it.season) }
        }
        return newest(seasons, episodesBySeason)?.let { HeroTarget.Play(it.episode, it.season) }
    }

    /** The newest Épisode of the unnumbered Saisons: latest broadcast (else publication), else the first listed. */
    private fun newest(seasons: List<Season>, episodesBySeason: Map<SeasonId, List<Episode>>): SeasonEpisode? {
        val listed = seasons.filter { it.number == null }
            .flatMap { season -> episodesBySeason[season.id].orEmpty().map { SeasonEpisode(it, season) } }
        return listed.filter { UpNextPolicy.eventAt(it.episode) != null }.maxByOrNull { UpNextPolicy.eventAt(it.episode)!! }
            ?: listed.firstOrNull()
    }

    private fun seasonOf(id: EpisodeId, seasons: List<Season>, episodesBySeason: Map<SeasonId, List<Episode>>): Season? =
        seasons.firstOrNull { season -> episodesBySeason[season.id].orEmpty().any { it.id == id } }

    /** Saisons whose listed Épisodes are all watched (the check on their pill); empty Saisons never. */
    fun watchedSeasons(
        seasons: List<Season>,
        episodesBySeason: Map<SeasonId, List<Episode>>,
        watched: Set<EpisodeId>,
    ): Set<SeasonId> = seasons.mapNotNullTo(HashSet()) { season ->
        val episodes = episodesBySeason[season.id] ?: return@mapNotNullTo null
        season.id.takeIf { episodes.isNotEmpty() && episodes.all { it.id in watched } }
    }
}
