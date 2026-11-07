package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.replaybox.core.model.Genre
import org.junit.Test

/**
 * TF1 Index page (unfiltered programs 483ce0f, measured 2026-11-07: 4685 items, offset = page
 * index): only the 5 TF1 group Chaînes, only Programmes with replay Épisodes.
 */
class Tf1IndexParserTest {

    private val page = TestFixtures.load("tf1_programs_index_page.json")
    private val crime = TestFixtures.load("tf1_crime_cases.json")

    @Test
    fun keepsOnlyGroupChannelsWithReplay() {
        val entries = Tf1Parsers.indexPage(page, pageIndex = 0, pageSize = 500).entries
        assertThat(entries).hasSize(6)
        assertThat(entries.map { it.channelId })
            .containsExactly("tf1", "tf1", "tmc", "tfx", "tf1-series-films", "lci").inOrder()
        val slugs = entries.map { it.show.id.value }
        assertThat(slugs).doesNotContain("meurtres-a-sandhamn") // arte
        assertThat(slugs).doesNotContain("burger-quiz") // replay.total == 0
    }

    @Test
    fun mapsRecentEpisodeCount() {
        val entries = Tf1Parsers.indexPage(page, 0, 500).entries
        val star = entries.first { it.show.id.value == "star-academy" }.show
        assertThat(star.recentEpisodeCount).isEqualTo(5)
        assertThat(star.title).isEqualTo("Star Academy")
        assertThat(star.posterUrl).isNotNull()
        assertThat(star.backdropUrl).isNotNull()
        assertThat(entries.first { it.show.id.value == "julie-lescaut" }.show.recentEpisodeCount).isEqualTo(0)
    }

    @Test
    fun hasMoreFromTotal() {
        assertThat(Tf1Parsers.indexPage(page, 0, 500).hasMore).isTrue()
        assertThat(Tf1Parsers.indexPage(page, 8, 500).hasMore).isTrue()
        assertThat(Tf1Parsers.indexPage(page, 9, 500).hasMore).isFalse()
    }

    @Test
    fun genresIncludeCrimeForPoliceFiction() {
        val shows = Tf1Parsers.indexPage(crime, 0, 500).entries.associate { it.show.id.value to it.show }
        assertThat(shows.getValue("profilage").genres).containsAtLeast(Genre.SERIES, Genre.CRIME_THRILLER)
        assertThat(shows.getValue("hpi").genres).contains(Genre.CRIME_THRILLER)
        assertThat(shows.getValue("plus-belle-la-vie").genres).doesNotContain(Genre.CRIME_THRILLER)
        assertThat(shows.getValue("camping-paradis").genres).doesNotContain(Genre.CRIME_THRILLER)
        val group = Tf1Parsers.indexPage(page, 0, 500).entries.associate { it.show.id.value to it.show }
        assertThat(group.getValue("julie-lescaut").genres).contains(Genre.CRIME_THRILLER)
        assertThat(group.getValue("hercule-poirot").genres).contains(Genre.CRIME_THRILLER)
    }

    @Test
    fun entertainmentNeverGetsCrime() {
        // « Les dossiers Cold Case » is GT_ENTERTAINMENT and its synopsis names a criminal and
        // detectives: the keyword matcher fires, the parser still keeps it out (series only).
        val entry = Tf1Parsers.indexPage(page, 0, 500).entries.first { it.show.id.value == "les-dossiers-cold-case" }
        assertThat(Tf1CrimeKeywords.matches(entry.show.id.value, entry.show.title, entry.show.synopsis)).isTrue()
        assertThat(entry.show.genres).doesNotContain(Genre.CRIME_THRILLER)
        assertThat(entry.show.genres).contains(Genre.ENTERTAINMENT)
    }

    @Test
    fun filmsGetCrimeOnlyFromNativeSgt() {
        // Twilight (fantasy) and La Ligne verte (drama) name murders in their synopsis; films
        // take « Policier & Thriller » from SGT_THRILLER / SGT_DETECTIVE only.
        val shows = Tf1Parsers.indexPage(crime, 0, 500).entries.associate { it.show.id.value to it.show }
        for (slug in listOf("twilight-chapitre-iii-hesitation", "la-ligne-verte", "la-ligne-verte-919")) {
            assertWithMessage(slug).that(shows.getValue(slug).genres).contains(Genre.MOVIES)
            assertWithMessage(slug).that(shows.getValue(slug).genres).doesNotContain(Genre.CRIME_THRILLER)
        }
        assertThat(shows.getValue("la-ligne-verte-919").genres).containsExactly(Genre.MOVIES, Genre.DRAMA)
        assertThat(Tf1Genres.map(listOf("GT_MOVIES", "SGT_THRILLER"))).containsExactly(Genre.MOVIES, Genre.CRIME_THRILLER)
    }

    @Test
    fun telenovelaWithOneKeywordIsNotCrime() {
        val shows = Tf1Parsers.indexPage(crime, 0, 500).entries.associate { it.show.id.value to it.show }
        assertThat(shows.getValue("la-desalmada").genres).containsExactly(Genre.SERIES)
        assertThat(shows.getValue("alice-nevers").genres).containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
        assertThat(shows.getValue("leo-mattei-brigade-des-mineurs").genres).contains(Genre.CRIME_THRILLER)
    }

    @Test
    fun groupChannelsInOrder() {
        assertThat(Tf1Channels.GROUP.map { it.id })
            .containsExactly("tf1", "tmc", "tfx", "tf1-series-films", "lci").inOrder()
        assertThat(Tf1Channels.GROUP.map { it.name })
            .containsExactly("TF1", "TMC", "TFX", "TF1 Séries Films", "LCI").inOrder()
    }
}
