package com.replaybox.core.data

import com.replaybox.core.model.Show
import com.replaybox.core.model.Titles

/**
 * Ranking of local search results (SPEC §Recherche). Matching is on the normalized title
 * (case- and accent-insensitive); titles starting with the query come before titles merely
 * containing it, ties go to the Programme with more recent Épisodes, then to input order.
 */
object SearchRanking {

    /** The matches of [query] in [shows], ranked; non-matches are dropped. */
    fun rank(query: String, shows: List<Show>): List<Show> {
        val wanted = Titles.normalize(query)
        if (wanted.isEmpty()) return emptyList()
        return shows
            .mapNotNull { show ->
                val title = Titles.normalize(show.title)
                when {
                    title.startsWith(wanted) -> 0 to show
                    title.contains(wanted) -> 1 to show
                    else -> null
                }
            }
            // sortedWith is stable: equal keys keep their input order.
            .sortedWith(compareBy<Pair<Int, Show>> { it.first }.thenByDescending { it.second.recentEpisodeCount })
            .map { it.second }
    }
}
