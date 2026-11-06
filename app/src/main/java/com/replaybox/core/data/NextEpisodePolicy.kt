package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId

/**
 * Épisode suivant (M4 Lecteur complet). The queue is the displayed list of a
 * numbered Saison, ascending (S4 E1, E2…); callers pass an empty queue for an
 * unnumbered magazine Saison, listed newest first, where "next" would be an
 * older emission rather than the continuation.
 */
object NextEpisodePolicy {
    fun next(queue: List<Episode>, current: EpisodeId): Episode? {
        val index = queue.indexOfFirst { it.id == current }
        return if (index < 0) null else queue.getOrNull(index + 1)
    }
}
