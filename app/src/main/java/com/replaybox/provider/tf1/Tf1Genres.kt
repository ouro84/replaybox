package com.replaybox.provider.tf1

import com.replaybox.core.model.Genre

/**
 * Mapping from TF1's GT_/SGT_ taxonomy to Replaybox canonical Genres (SPEC).
 *
 * Every TF1 programme carries exactly one GT_ (root) and one SGT_ (sub) category, and SGT ids are
 * English (measured 2026-11-07 on the full catalogue). The resulting Genres are the union of both
 * mappings: the GT_ id gives the broad Genre, the SGT_ id refines it (e.g. a GT_MOVIES film tagged
 * SGT_COMEDY is both MOVIES and COMEDY).
 *
 * TF1 has no native crime/thriller category for series; SGT_THRILLER and SGT_DETECTIVE only occur
 * on films.
 */
object Tf1Genres {

    private val root = mapOf(
        "GT_SERIES_AND_FICTIONS" to Genre.SERIES,
        "GT_MOVIES" to Genre.MOVIES,
        "GT_ENTERTAINMENT" to Genre.ENTERTAINMENT,
        "GT_INFOS_MAG_AND_SPORT" to Genre.SPORT_AND_INFO,
        "GT_YOUTH" to Genre.YOUTH,
    )

    /**
     * SGT_ ids that add or confirm a Genre. SGT_ENTERTAINMENT and SGT_SPORT are deliberately absent:
     * besides their own GT_ (which already yields the Genre) they also tag GT_MOVIES films as a
     * catch-all (La Ligne verte, Morbius, Le Combattant…), which would leak films into
     * Divertissements / Sport & Info. Film sub-genres without a canonical Genre (SGT_ACTION,
     * SGT_ANIMATION, SGT_ROMANCE, SGT_HORROR…) add nothing beyond MOVIES.
     */
    private val sub = mapOf(
        "SGT_COMEDY" to Genre.COMEDY,
        "SGT_DRAMA" to Genre.DRAMA,
        "SGT_DOCUMENTARY" to Genre.DOCUMENTARY,
        "SGT_THRILLER" to Genre.CRIME_THRILLER,
        "SGT_DETECTIVE" to Genre.CRIME_THRILLER,
        "SGT_SERIES" to Genre.SERIES,
        "SGT_FOREIGN_FICTION" to Genre.SERIES,
        "SGT_FRENCH_FICTION" to Genre.SERIES,
        "SGT_INFOS" to Genre.SPORT_AND_INFO,
        "SGT_MAG_INFOS" to Genre.SPORT_AND_INFO,
        "SGT_MAGAZINES" to Genre.ENTERTAINMENT,
        "SGT_GAMES" to Genre.ENTERTAINMENT,
        "SGT_SIX_TO_TEN_YEARS" to Genre.YOUTH,
        "SGT_THREE_TO_SIX_YEARS" to Genre.YOUTH,
    )

    fun map(categoryIds: List<String>): Set<Genre> =
        categoryIds.mapNotNullTo(linkedSetOf()) { root[it] ?: sub[it] }
}
