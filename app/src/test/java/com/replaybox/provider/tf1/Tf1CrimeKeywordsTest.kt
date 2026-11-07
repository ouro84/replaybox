package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** « Policier & Thriller » heuristic on real TF1 fictions (capture 2026-11-07). */
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
        for (slug in listOf("plus-belle-la-vie", "demain-nous-appartient", "camping-paradis", "josephine-ange-gardien")) {
            assertWithMessage(slug).that(matches(slug)).isFalse()
        }
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
