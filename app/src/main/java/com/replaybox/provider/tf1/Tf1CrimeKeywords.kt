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
 * dramas open on a murder (« La desalmada »). Measured on the 2026-11-07 capture: 297 of the
 * 1944 group series with replay (252 by the rule, 45 by the include list), against 268 before
 * the false-negative review (« flic », « juge d'instruction », 20 more slugs) and 475 with the
 * earlier « any keyword » rule.
 */
object Tf1CrimeKeywords {

    /** One entry per distinct keyword; inflections (enquête, enquêtes, enquêter…) count once. */
    private val KEYWORDS: List<Regex> = listOf(
        "enquete\\w*", "meurtre\\w*", "meurtrier\\w*", "assassin\\w*", "policier\\w*", "commissaire",
        "inspecteur", "inspectrice", "brigade", "gendarme\\w*", "homicide\\w*", "criminel\\w*", "crime\\w*",
        "tueur\\w*", "tueuse\\w*", "serial killer", "detective\\w*", "profiler", "profileuse", "legiste",
        "thriller", // « scène de crime » is covered by « crime »
        // Added by the false-negative review (2026-11-07): each one only added police or judicial
        // fiction on the full capture (Le Rebelle, Flics, Mercato, À tort ou à raison, Le Juge…).
        "flic\\w*", "juges? d['’]instruction",
    ).map { Regex("\\b$it\\b") }

    // Known misses of the rule, by slug. Extend only with a fixture case each
    // (tf1_crime_cases.json) or a measurement noted in SPEC.md.

    /**
     * Police fiction the rule misses: its synopsis names at most one keyword and its title
     * none. « HPI » never names the police. The first block holds the series with recurring
     * police or judicial investigators that the two-keyword rule dropped on the 2026-11-07
     * capture (picked by reading their synopsis). The second block comes from the review of
     * every group series with exactly one keyword (same capture): crime, investigation, legal
     * and suspense thrillers, one comment each. Single-episode TV films stay out, as decided at
     * the M4 grilling. Alice Nevers, Boulevard du Palais, Deux flics sur les docks, La loi
     * selon Bartoli, Panda and Un flic left the list: « flic » and « juge d'instruction » now
     * catch them.
     */
    private val ALWAYS = setOf(
        "hpi",
        "a-linterieur", "ariane-ferry", "chantal", "commandant-saint-barth", "contact", "disparue",
        "elsbeth", "experts-miami", "falco", "femmes-de-loi", "greco", "groupe-flag", "julie-lescaut",
        "la-main-blanche", "le-maitre-du-zodiaque", "les-temoins", "maman-a-tort",
        "most-wanted-criminals", "peur-sur-le-lac", "poker-face", "pour-sarah", "riviere-perdue",
        "tabula-rasa", "walker-texas-ranger",
        // False-negative review (2026-11-07).
        "accuse", // anthology: an ordinary citizen accused of a crime, up to the verdict
        "au-dela-des-apparences", // a twin investigates her sister's disappearance alongside the police
        "carpe-diem", // a wrongly convicted man turned lawyer hunts his wife's real killer
        "florence-larrieu-le-juge-est-une-femme", // investigating judge (title names « juge » only)
        "indefendable", // murder trials, three versions of each case
        "intracables", // a widow hunted by a hacker while she investigates her husband's death
        "la-part-du-soupcon", // a commandante chases a man suspected of murdering his family
        "le-mystere-de-la-chambre-jaune", // locked-room attack investigation (Leroux)
        "les-liens-du-sang", // a lawyer defends a young man accused of murder
        "mammon-la-revelation", // Norwegian thriller: a journalist investigates a corporate fraud
        "menace-imminente", // intelligence officer and anti-terrorism policewoman, 72-hour hunt
        "mon-ange", // a mother searches her missing daughter in a village shaken by a murder
        "nightsleeper", // hijacked night train, ex-policeman and cyber-security chief
        "noces-rouges", // a bride found dead below a cliff, a family friend investigates
        "norskov-dans-le-secret-des-glaces", // Danish crime series: an investigator against drug trafficking
        "psycho-pass", // anime: inspectors of the criminal investigation bureau
        "ransom", // elite crisis negotiator facing dangerous criminals
        "rien-ne-tefface", // Bussi adaptation: murders tied to a vanished son
        "sauveur-giordano", // former Brigade des mineurs officer who supports victims
        "une-chance-de-trop", // Coben thriller: husband murdered, baby kidnapped
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
