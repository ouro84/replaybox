package com.replaybox.core.data

import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Show
import com.replaybox.core.model.Titles
import java.time.Instant
import java.time.ZoneId

/**
 * The « Tout voir » grid of a Genre (ROADMAP M4, grilling 2026-11-07): every Programme of the
 * Genre across the given Chaînes, most recent first, then by title.
 *
 * Recency (debt « Tri de Tout voir », measured 2026-11-07, SPEC per Provider) is
 * [Show.recencyAt], the latest sign of a new Épisode the Chaîne's own listing gives: M6 dates
 * every Programme, France TV only those its Chaîne page features, TF1 only those with Épisodes
 * aired in the last day or so. A Programme without one falls back to [Show.firstSeenAt] only
 * when it arrived after its Chaîne's first listing on the box (a newcomer): on the first
 * listing every Programme shares that instant, which says nothing about its Épisodes.
 *
 * Only the last [RECENT_DAYS] days rank, by Paris calendar day (one refresh writes the Chaînes
 * seconds apart, so the exact instant would only sort by refresh order); within a day, and for
 * every older or undated Programme after them, the title decides (case- and accent-insensitive).
 * Ranking old M6 dates above undated France TV and TF1 Programmes would bury the Chaînes that
 * date less, for a difference the viewer cannot see.
 */
object GenreGrid {

    /** How far back recency ranks; older or undated Programmes follow in title order. */
    const val RECENT_DAYS = 30L

    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /** The cache keeps the latest recency any refresh gave (ShowEntity.recencyAt). */
    fun mergeRecency(stored: Long?, incoming: Long?): Long? =
        if (stored == null) incoming else if (incoming == null) stored else maxOf(stored, incoming)

    /**
     * The Paris day (epoch day) that ranks [show], or null when it ranks by title only: no
     * recency and not a newcomer, or older than [RECENT_DAYS] before [now]. A date ahead of
     * [now] counts as today. [firstListedAt] is the first-seen instant of its Chaîne's first
     * listing (null: unknown, so no newcomer).
     */
    fun recencyDay(show: Show, now: Long, firstListedAt: Long?): Long? {
        val at = show.recencyAt ?: show.firstSeenAt.takeIf { firstListedAt != null && isNewcomer(it, firstListedAt) }
            ?: return null
        val today = parisDay(now)
        val day = minOf(parisDay(at), today)
        return day.takeIf { it >= today - RECENT_DAYS }
    }

    private fun isNewcomer(firstSeenAt: Long, firstListedAt: Long): Boolean =
        firstSeenAt > 0 && parisDay(firstSeenAt) > parisDay(firstListedAt)

    private fun parisDay(epochMs: Long): Long =
        Instant.ofEpochMilli(epochMs).atZone(PARIS).toLocalDate().toEpochDay()

    /**
     * The first-seen instant of each Chaîne's first listing in [items]: its oldest
     * [Show.firstSeenAt] (0, never stored, is ignored).
     */
    fun firstListings(items: List<ChannelShow>): Map<String, Long> {
        val first = HashMap<String, Long>()
        for (entry in items) {
            val at = entry.show.firstSeenAt.takeIf { it > 0 } ?: continue
            first[entry.channelId] = minOf(first[entry.channelId] ?: at, at)
        }
        return first
    }

    /** [items] most recent first, then by title; [firstListedAt] defaults to [items]' own. */
    fun sort(
        items: List<ChannelShow>,
        now: Long,
        firstListedAt: Map<String, Long> = firstListings(items),
    ): List<ChannelShow> =
        items
            .map { Ranked(it, recencyDay(it.show, now, firstListedAt[it.channelId]), Titles.normalize(it.show.title)) }
            .sortedWith(
                compareByDescending<Ranked, Long?>(nullsFirst<Long>()) { it.day }
                    .thenBy { it.title }
                    .thenBy { it.entry.show.title },
            )
            .map { it.entry }

    private class Ranked(val entry: ChannelShow, val day: Long?, val title: String)

    /**
     * The grid of [genre] (null: every Genre, Search with only a Chaîne filter): [items] of
     * that Genre on [channelIds] (null: every Chaîne), the
     * same Programme on two Chaînes kept once (normalized title, first in [items] order wins,
     * as on the Home rails), then [sort]ed at [now]. The filter comes before the merge, so a
     * Chaîne group keeps its own copy of a Programme another group also lists. [items] should
     * hold whole listings: each Chaîne's first listing is read from them before the filter.
     */
    fun arrange(items: List<ChannelShow>, genre: Genre?, channelIds: Set<String>?, now: Long): List<ChannelShow> {
        val firstListedAt = firstListings(items)
        val seen = HashSet<String>()
        val kept = items.filter { entry ->
            (genre == null || genre in entry.show.genres) &&
                (channelIds == null || entry.channelId in channelIds) &&
                seen.add(Titles.normalize(entry.show.title))
        }
        return sort(kept, now, firstListedAt)
    }
}
