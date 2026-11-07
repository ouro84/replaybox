package com.replaybox.provider.m6

import com.replaybox.core.model.Genre

/**
 * M6 programme taxonomy → canonical Genres. Two fields per programme (catalogue capture
 * 2026-11-07, SPEC §Provider M6+): `neustar.category` (documentaire, serie-policiere,
 * telefilm-drame…) and `program_type_wording.code` (episode, telefilm, film, emission…).
 * Genres = union of both. A category absent from the table still counts as a series when it
 * belongs to the serie/series/telefilm/telenovela family.
 */
object M6Genres {

    private val categories: Map<String, Set<Genre>> = mapOf(
        "serie-policiere" to setOf(Genre.SERIES, Genre.CRIME_THRILLER),
        "telefilm-frissons" to setOf(Genre.SERIES, Genre.CRIME_THRILLER),
        "comedie" to setOf(Genre.COMEDY),
        "serie-humour" to setOf(Genre.SERIES, Genre.COMEDY),
        "drame" to setOf(Genre.DRAMA),
        "series-dramatiques" to setOf(Genre.SERIES, Genre.DRAMA),
        "serie-dramatique" to setOf(Genre.SERIES, Genre.DRAMA),
        "telefilm-drame" to setOf(Genre.SERIES, Genre.DRAMA),
        "documentaire" to setOf(Genre.DOCUMENTARY),
        "kids" to setOf(Genre.YOUTH),
        "tous-petits" to setOf(Genre.YOUTH),
        "teens" to setOf(Genre.YOUTH),
        // Reality shows are entertainment, not fiction, despite the serie- prefix.
        "serie-realite" to setOf(Genre.ENTERTAINMENT),
        "series-realite" to setOf(Genre.ENTERTAINMENT),
        "divertissement" to setOf(Genre.ENTERTAINMENT),
        "cuisine" to setOf(Genre.ENTERTAINMENT),
        "musique" to setOf(Genre.ENTERTAINMENT),
        "spectacle" to setOf(Genre.ENTERTAINMENT),
        "competition" to setOf(Genre.ENTERTAINMENT),
        "magazine-dinformation" to setOf(Genre.SPORT_AND_INFO),
        "magazine-d-information" to setOf(Genre.SPORT_AND_INFO),
        "info" to setOf(Genre.SPORT_AND_INFO),
        "infos" to setOf(Genre.SPORT_AND_INFO),
        "info-et-societe" to setOf(Genre.SPORT_AND_INFO),
        "sport" to setOf(Genre.SPORT_AND_INFO),
        "film" to setOf(Genre.MOVIES),
        "long-metrage" to setOf(Genre.MOVIES),
    )

    private val types: Map<String, Genre> = mapOf(
        "film" to Genre.MOVIES,
        "episode" to Genre.SERIES,
        "telefilm" to Genre.SERIES,
        "documentaire" to Genre.DOCUMENTARY,
        "dessin-anime" to Genre.YOUTH,
        "emission" to Genre.ENTERTAINMENT,
    )

    private val seriesFamily = listOf("serie", "series", "telefilm", "telenovela")

    fun map(neustarCategory: String?, programType: String?): Set<Genre> {
        val result = linkedSetOf<Genre>()
        neustarCategory?.let { category ->
            val mapped = categories[category]
            when {
                mapped != null -> result += mapped
                seriesFamily.any { category == it || category.startsWith("$it-") || category.startsWith("${it}_") } ->
                    result += Genre.SERIES
            }
        }
        programType?.let { types[it] }?.let { result += it }
        return result
    }
}
