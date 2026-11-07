package com.replaybox.provider.tf1

import com.replaybox.core.model.Titles

/**
 * « Policier & Thriller » heuristic for TF1 series (SPEC §Genres): TF1 has no crime category
 * for series (SGT_THRILLER / SGT_DETECTIVE only tag films), so police fiction is recognised
 * from its title and synopsis. The parser applies it to GT_SERIES_AND_FICTIONS only: films
 * take the Genre from their native SGT alone, and a magazine about a criminal case never
 * lands in the rail.
 *
 * Rule (ROADMAP M4, grilling 2026-11-07): a keyword in the title, or at least two distinct
 * keywords in the synopsis. A single keyword in a synopsis is too weak: telenovelas and
 * dramas open on a murder (« La desalmada »). Measured on the 2026-11-07 capture: 268 of the
 * 1947 group series (238 by the rule, 30 by the include list), against 475 with the earlier
 * « any keyword » rule.
 */
object Tf1CrimeKeywords {

    /** One entry per distinct keyword; inflections (enquête, enquêtes, enquêter…) count once. */
    private val KEYWORDS: List<Regex> = listOf(
        "enquete\\w*", "meurtre\\w*", "meurtrier\\w*", "assassin\\w*", "policier\\w*", "commissaire",
        "inspecteur", "inspectrice", "brigade", "gendarme\\w*", "homicide\\w*", "criminel\\w*", "crime\\w*",
        "tueur\\w*", "tueuse\\w*", "serial killer", "detective\\w*", "profiler", "profileuse", "legiste",
        "thriller", // « scène de crime » is covered by « crime »
    ).map { Regex("\\b$it\\b") }

    // Known misses of the rule, by slug. Extend only with a fixture case each
    // (tf1_crime_cases.json) or a measurement noted in SPEC.md.

    /**
     * Police fiction the rule misses: its synopsis names at most one keyword and its title
     * none. « HPI » never names the police. The others are the series with recurring police
     * or judicial investigators that the two-keyword rule dropped on the 2026-11-07 capture
     * (picked by reading their synopsis; « Alice Nevers » is a fixture case).
     */
    private val ALWAYS = setOf(
        "hpi",
        "a-linterieur", "alice-nevers", "ariane-ferry", "boulevard-du-palais", "chantal",
        "commandant-saint-barth", "contact", "deux-flics-sur-les-docks", "disparue", "elsbeth",
        "experts-miami", "falco", "femmes-de-loi", "greco", "groupe-flag", "julie-lescaut",
        "la-loi-selon-bartoli", "la-main-blanche", "le-maitre-du-zodiaque", "les-temoins",
        "maman-a-tort", "most-wanted-criminals", "panda", "peur-sur-le-lac", "poker-face",
        "pour-sarah", "riviere-perdue", "tabula-rasa", "un-flic", "walker-texas-ranger",
    )

    // « Plus belle la vie »: a soap whose synopses list « enquêtes policières » among its plots.
    private val NEVER_PREFIXES = listOf("plus-belle-la-vie")

    fun matches(slug: String, title: String, synopsis: String?): Boolean {
        if (slug in ALWAYS) return true
        if (NEVER_PREFIXES.any { slug.startsWith(it) }) return false
        val normalizedTitle = Titles.normalize(title)
        if (KEYWORDS.any { it.containsMatchIn(normalizedTitle) }) return true
        val text = Titles.normalize(synopsis.orEmpty())
        return KEYWORDS.count { it.containsMatchIn(text) } >= 2
    }
}
