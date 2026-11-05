package com.replaybox.provider.tf1

import com.replaybox.core.model.Genre

/**
 * Mapping from TF1's GT_/SGT_ taxonomy to Replaybox canonical Genres (SPEC).
 * TF1 has no native crime/thriller category: it falls back on SERIES per SPEC.
 */
object Tf1Genres {

    private val exact = mapOf(
        "GT_SERIES_AND_FICTIONS" to Genre.SERIES,
        "GT_MOVIES" to Genre.MOVIES,
        "GT_ENTERTAINMENT" to Genre.ENTERTAINMENT,
        "GT_INFOS_MAG_AND_SPORT" to Genre.SPORT_AND_INFO,
        "GT_YOUTH" to Genre.YOUTH,
        "SGT_SERIES" to Genre.SERIES,
        "SGT_POLICIER" to Genre.CRIME_THRILLER,
        "SGT_THRILLER" to Genre.CRIME_THRILLER,
        "SGT_COMEDIE" to Genre.COMEDY,
        "SGT_DRAME" to Genre.DRAMA,
        "SGT_DOCUMENTAIRE" to Genre.DOCUMENTARY,
        "SGT_DOC" to Genre.DOCUMENTARY,
        "SGT_SPORT" to Genre.SPORT_AND_INFO,
        "SGT_INFO" to Genre.SPORT_AND_INFO,
        "SGT_ENTERTAINMENT" to Genre.ENTERTAINMENT,
        "SGT_JEUNESSE" to Genre.YOUTH,
        "SGT_FILM" to Genre.MOVIES,
    )

    private val suffixes = listOf(
        "POLICIER" to Genre.CRIME_THRILLER,
        "THRILLER" to Genre.CRIME_THRILLER,
        "COMEDIE" to Genre.COMEDY,
        "DRAME" to Genre.DRAMA,
        "DOCUMENTAIRE" to Genre.DOCUMENTARY,
        "SPORT" to Genre.SPORT_AND_INFO,
        "JEUNESSE" to Genre.YOUTH,
        "FILMS" to Genre.MOVIES,
        "SERIES" to Genre.SERIES,
    )

    fun map(categoryIds: List<String>): Set<Genre> {
        val result = linkedSetOf<Genre>()
        for (id in categoryIds) {
            exact[id]?.let { result.add(it) }
        }
        if (result.isEmpty()) {
            for (id in categoryIds) {
                val upper = id.uppercase()
                suffixes.firstOrNull { upper.contains(it.first) }?.let { result.add(it.second) }
            }
        }
        return result
    }
}
