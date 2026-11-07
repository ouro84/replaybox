package com.replaybox.core.data

import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Show
import com.replaybox.core.model.Titles

/**
 * The « Tout voir » grid of a Genre (ROADMAP M4, grilling 2026-11-07): every Programme of the
 * Genre across the given Chaînes, most recent first, then by title.
 *
 * Recency: no cached listing carries the date of a Programme's latest Épisode (TF1's
 * `replayRecent.total` is a count, TF1 only; France TV and M6 expose nothing). The best signal
 * the cache holds is when it first listed the Programme ([Show.firstSeenAt], kept across
 * refreshes), compared by day: one refresh writes the Chaînes seconds apart, so the exact
 * instant would only sort by refresh order. Same day: title, case- and accent-insensitive.
 */
object GenreGrid {

    private const val DAY_MS = 86_400_000L

    /** The UTC day the cache first listed [show]; the same bucket RoomRawDao.shows orders by. */
    fun recencyDay(show: Show): Long = show.firstSeenAt / DAY_MS

    fun sort(items: List<ChannelShow>): List<ChannelShow> =
        items
            .map { it to Titles.normalize(it.show.title) }
            .sortedWith(
                compareByDescending<Pair<ChannelShow, String>> { recencyDay(it.first.show) }
                    .thenBy { it.second }
                    .thenBy { it.first.show.title },
            )
            .map { it.first }

    /**
     * The grid of [genre]: [items] of that Genre on [channelIds] (null: every Chaîne), the
     * same Programme on two Chaînes kept once (normalized title, first in [items] order wins,
     * as on the Home rails), then [sort]ed. The filter comes before the merge, so a Chaîne
     * group keeps its own copy of a Programme another group also lists.
     */
    fun arrange(items: List<ChannelShow>, genre: Genre, channelIds: Set<String>?): List<ChannelShow> {
        val seen = HashSet<String>()
        val kept = items.filter { entry ->
            genre in entry.show.genres &&
                (channelIds == null || entry.channelId in channelIds) &&
                seen.add(Titles.normalize(entry.show.title))
        }
        return sort(kept)
    }
}
