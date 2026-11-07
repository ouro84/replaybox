package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * « Policier & Thriller » heuristic on real TF1 series (capture 2026-11-07): a keyword in the
 * title, or two distinct keywords in the synopsis (ROADMAP M4, grilling 2026-11-07).
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
        for (slug in listOf("profilage", "hpi", "les-experts-las-vegas", "section-de-recherches", "alice-nevers", "mentalist")) {
            assertWithMessage(slug).that(matches(slug)).isTrue()
        }
    }

    @Test
    fun negatives() {
        for (slug in listOf("plus-belle-la-vie", "demain-nous-appartient", "camping-paradis", "josephine-ange-gardien", "la-desalmada")) {
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
        // Alice Nevers is police fiction whose synopsis names one keyword only: kept by slug.
        val item = items.getValue("alice-nevers")
        assertThat(Tf1CrimeKeywords.matches("x", "Alice Nevers", Tf1Json.str(item, "decoration", "description"))).isFalse()
        assertThat(matches("alice-nevers")).isTrue()
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
