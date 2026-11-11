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
 * aired in the last day or so. [Show.firstSeenAt] never ranks: on the Bbox (2026-11-07) a few
 * Programmes listed the day before made almost the whole TF1 Index look « new today », tied
 * with the really recent ones, so the grid read alphabetically again.
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
     * recency, or older than [RECENT_DAYS] before [now]. A date ahead of [now] counts as today.
     */
    fun recencyDay(show: Show, now: Long): Long? {
        val at = show.recencyAt ?: return null
        val today = parisDay(now)
        val day = minOf(parisDay(at), today)
        return day.takeIf { it >= today - RECENT_DAYS }
    }

    /** The Paris calendar day (epoch day) of [epochMs]: the unit recency ranks by. */
    fun parisDay(epochMs: Long): Long =
        Instant.ofEpochMilli(epochMs).atZone(PARIS).toLocalDate().toEpochDay()

    /** [items] most recent first, then by title. */
    fun sort(items: List<ChannelShow>, now: Long): List<ChannelShow> =
        items
            .map { Ranked(it, recencyDay(it.show, now), Titles.normalize(it.show.title)) }
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
        val seen = HashSet<String>()
        val kept = items.filter { entry ->
            (genre == null || genre in entry.show.genres) &&
                (channelIds == null || entry.channelId in channelIds) &&
                seen.add(Titles.normalize(entry.show.title))
        }
        return sort(kept, now)
    }
}
