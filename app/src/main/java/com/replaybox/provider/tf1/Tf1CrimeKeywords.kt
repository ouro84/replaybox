package com.replaybox.provider.tf1

import com.replaybox.core.model.Titles

/**
 * « Policier & Thriller » heuristic for TF1 (SPEC §Genres): TF1 has no crime category for
 * series (SGT_THRILLER / SGT_DETECTIVE only tag films), so police fiction is recognised from
 * its title and synopsis. Prototyped on the 2026-11-07 capture: 555 of the 2446 group
 * fictions/films match. The parser only applies it to GT_SERIES_AND_FICTIONS and GT_MOVIES,
 * so a magazine about a criminal case never lands in the rail.
 */
object Tf1CrimeKeywords {

    private val PATTERN = Regex(
        "\\b(enquete\\w*|meurtre\\w*|meurtrier\\w*|assassin\\w*|policier\\w*|commissaire|inspecteur|" +
            "inspectrice|brigade|gendarme\\w*|homicide\\w*|criminel\\w*|crime\\w*|tueur\\w*|tueuse\\w*|" +
            "serial killer|detective\\w*|profiler|profileuse|legiste|scene de crime|thriller)\\b",
    )

    // Known misses of the pattern, by slug. Extend only with a fixture case each
    // (tf1_crime_cases.json).

    // « HPI »: police fiction whose synopsis never names the police.
    private val ALWAYS = setOf("hpi")

    // « Plus belle la vie »: a soap whose synopses list « enquêtes policières » among its plots.
    private val NEVER_PREFIXES = listOf("plus-belle-la-vie")

    fun matches(slug: String, title: String, synopsis: String?): Boolean {
        if (slug in ALWAYS) return true
        if (NEVER_PREFIXES.any { slug.startsWith(it) }) return false
        val text = Titles.normalize(title + " " + synopsis.orEmpty())
        return PATTERN.containsMatchIn(text)
    }
}
