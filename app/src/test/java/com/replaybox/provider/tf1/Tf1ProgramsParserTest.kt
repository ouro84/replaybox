package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

class Tf1ProgramsParserTest {

    @Test
    fun `parses programs listing`() {
        val shows = Tf1Parsers.showsFromPrograms(TestFixtures.load("tf1_programs.json"))
        assertThat(shows).isNotEmpty()
        val first = shows.first()
        assertThat(first.id.value).isEqualTo("star-academy")
        assertThat(first.title).isEqualTo("Star Academy")
        assertThat(first.posterUrl).startsWith("https://photos.tf1.fr/")
        assertThat(first.synopsis).isNotEmpty()
        assertThat(first.genres).contains(Genre.ENTERTAINMENT)
    }

    @Test
    fun `picks the widest 16x9 background as backdrop`() {
        val first = Tf1Parsers.showsFromPrograms(TestFixtures.load("tf1_programs.json")).first()
        assertThat(first.backdropUrl)
            .isEqualTo("https://photos.tf1.fr/1920/1080/background-ott-jpg-282937-35ef0d-0@1x.jpg")
    }

    @Test
    fun `maps program genres`() {
        val shows = Tf1Parsers.showsFromPrograms(TestFixtures.load("tf1_programs.json"))
        val series = shows.filter { Genre.SERIES in it.genres }
        assertThat(series).isNotEmpty()
    }

    @Test
    fun `parses root categories`() {
        val categoryIds = Tf1Parsers.showsFromCategories(TestFixtures.load("tf1_categories.json"))
        assertThat(categoryIds).containsAtLeast(
            "GT_SERIES_AND_FICTIONS",
            "GT_MOVIES",
            "GT_ENTERTAINMENT",
            "GT_INFOS_MAG_AND_SPORT",
            "GT_YOUTH",
        )
    }
}
