package com.replaybox.provider.francetv

import com.replaybox.core.model.Genre

/**
 * Mapping from France TV's category/sub_categories slugs to Replaybox
 * canonical Genres. Measured across the five channel listings on
 * 2026-11-05 (SPEC §Provider France TV): slugs use "/" and "_" as
 * separators interchangeably (info/jt and info_jt both occur), so only
 * the last segment is matched.
 */
object FtvGenres {

    private val categories = mapOf(
        "documentaires" to Genre.DOCUMENTARY,
        "enfants" to Genre.YOUTH,
        "films" to Genre.MOVIES,
        "info" to Genre.SPORT_AND_INFO,
        "jeux-et-divertissements" to Genre.ENTERTAINMENT,
        "series-et-fictions" to Genre.SERIES,
        // Society magazines (Ça commence aujourd'hui, Envoyé spécial-like)
        // play as documentaries: closest canonical Genre.
        "societe" to Genre.DOCUMENTARY,
        "spectacles-et-culture" to Genre.ENTERTAINMENT,
        "sport" to Genre.SPORT_AND_INFO,
    )

    /** Sub-categories that refine the series family. */
    private val subCategories = mapOf(
        "series-policieres" to Genre.CRIME_THRILLER,
        "series-thriller" to Genre.CRIME_THRILLER,
        "series-comedie" to Genre.COMEDY,
        "series-drame" to Genre.DRAMA,
        "feuilletons" to Genre.SERIES,
        "films-tv" to Genre.MOVIES,
        "humour" to Genre.ENTERTAINMENT,
    )

    fun map(category: String?, subCategories: List<String>): Set<Genre> {
        val result = linkedSetOf<Genre>()
        category?.let { this.categories[lastSegment(it)]?.let { genre -> result.add(genre) } }
        for (sub in subCategories) {
            this.subCategories[lastSegment(sub)]?.let { result.add(it) }
        }
        return result
    }

    private fun lastSegment(slug: String): String =
        slug.substringAfterLast('/').substringAfterLast('_')
}
