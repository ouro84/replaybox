package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * « Policier & Thriller » heuristic on real TF1 series (capture 2026-11-07): a keyword in the
 * title, or two distinct keywords in the synopsis (ROADMAP M4, grilling 2026-11-07), plus the
 * false-negative review of the same day (« flic », « juge d'instruction », include list).
 */
class Tf1CrimeKeywordsTest {

    private val items = Tf1Json.objects(Tf1Json.array(TestFixtures.load("tf1_crime_cases.json"), "data", "programs", "items"))
        .associateBy { Tf1Json.str(it, "slug")!! }

    private fun matches(slug: String): Boolean {
        val item = items.getValue(slug)
        return Tf1CrimeKeywords.matches(slug, Tf1Json.str(item, "name")!!, Tf1Json.str(item, "decoration", "description"))
    }

    @Test
    fun positives() {
        val slugs = listOf(
            "profilage", "hpi", "les-experts-las-vegas", "section-de-recherches", "alice-nevers", "mentalist",
            "le-rebelle", "flics", "a-tort-ou-a-raison", "sauveur-giordano", "intracables",
        )
        for (slug in slugs) {
            assertWithMessage(slug).that(matches(slug)).isTrue()
        }
    }

    @Test
    fun negatives() {
        val slugs = listOf(
            "plus-belle-la-vie", "demain-nous-appartient", "camping-paradis", "josephine-ange-gardien", "la-desalmada",
            // Near misses left out by the false-negative review: a brocanteur who « mène aussi
            // l'enquête », a law firm between criminal and family cases.
            "louis-la-brocante", "avocats-et-associes",
        )
        for (slug in slugs) {
            assertWithMessage(slug).that(matches(slug)).isFalse()
        }
    }

    @Test
    fun keywordInTitleIsEnough() {
        // « Léo Matteï, Brigade des mineurs »: its synopsis names a single keyword.
        assertThat(matches("leo-mattei-brigade-des-mineurs")).isTrue()
        assertThat(Tf1CrimeKeywords.matches("x", "Meurtre à Noël", null)).isTrue()
    }

    @Test
    fun oneSynopsisKeywordIsNotEnough() {
        // « La desalmada » is a telenovela whose synopsis opens on a murder.
        assertThat(Tf1CrimeKeywords.matches("x", "La desalmada", "Trois ans après le meurtre de son mari.")).isFalse()
    }

    @Test
    fun twoDistinctSynopsisKeywordsMatch() {
        assertThat(Tf1CrimeKeywords.matches("x", "Profilage", "Une criminologue assiste un policier dans ses enquêtes.")).isTrue()
    }

    @Test
    fun sameKeywordTwiceCountsOnce() {
        // « enquête » and « enquêtes » are the same keyword (Alice Nevers' synopsis).
        assertThat(Tf1CrimeKeywords.matches("x", "Série", "Les enquêtes les plus complexes. Une enquête de plus.")).isFalse()
    }

    @Test
    fun includeListOverridesTheRule() {
        // Sauveur Giordano (former Brigade des mineurs officer) names one keyword only: kept by slug.
        val item = items.getValue("sauveur-giordano")
        assertThat(Tf1CrimeKeywords.matches("x", "Sauveur Giordano", Tf1Json.str(item, "decoration", "description"))).isFalse()
        assertThat(matches("sauveur-giordano")).isTrue()
    }

    @Test
    fun flicAndInvestigatingJudgeAreKeywords() {
        // « Le Rebelle »: « flics » and « meurtre »; « À tort ou à raison »: « policier » and
        // « juge d'instruction ». Both were false negatives of the first M4 rule.
        for (slug in listOf("le-rebelle", "a-tort-ou-a-raison")) {
            val description = Tf1Json.str(items.getValue(slug), "decoration", "description")
            assertWithMessage(slug).that(Tf1CrimeKeywords.matches("x", "Série", description)).isTrue()
        }
        assertThat(Tf1CrimeKeywords.matches("x", "Flics", null)).isTrue()
        assertThat(Tf1CrimeKeywords.matches("x", "Série", "La juge d’instruction et le policier.")).isTrue()
        // « juge » alone is not a keyword (« Clara et son juge » is a drama).
        assertThat(Tf1CrimeKeywords.matches("x", "Clara et son juge", null)).isFalse()
    }

    @Test
    fun accentsCaseAndWordBoundaries() {
        assertThat(Tf1CrimeKeywords.matches("x", "Une ENQUÊTE", null)).isTrue()
        assertThat(Tf1CrimeKeywords.matches("x", "Le Détective", null)).isTrue()
        assertThat(Tf1CrimeKeywords.matches("x", "Les gendarmeries", null)).isTrue()
        // A word merely containing a keyword is not a match.
        assertThat(Tf1CrimeKeywords.matches("x", "Superinspecteurs", null)).isFalse()
        assertThat(Tf1CrimeKeywords.matches("x", "Camping", "Vacances au soleil")).isFalse()
    }
}
