package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.data.FakeCatalogDao
import com.replaybox.core.data.blocking
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Genres
import org.junit.Test

/**
 * Jeunesse on TF1 (capture 2026-11-07): GT_YOUTH Programmes, plus the kids' cartoons TF1 files
 * as plain fiction (GT_SERIES_AND_FICTIONS + SGT_SERIES) such as Pokémon. Jeunesse is exclusive.
 */
class Tf1YouthTest {

    private val page = TestFixtures.load("tf1_youth_cases.json")
    private val entries = Tf1Parsers.indexPage(page, 0, 500).entries
    private val shows = entries.associate { it.show.id.value to Genres.canonical(it.show) }

    @Test
    fun pokemonIsYouthOnly() {
        assertThat(shows.getValue("pokemon").genres).containsExactly(Genre.YOUTH)
    }

    @Test
    fun kidsCartoonsFiledAsFictionAreYouthOnly() {
        for (slug in listOf("pokemon", "yu-gi-oh", "miraculous-chibi")) {
            assertWithMessage(slug).that(shows.getValue(slug).genres).containsExactly(Genre.YOUTH)
        }
    }

    @Test
    fun nativeYouthIsYouthOnly() {
        assertThat(shows.getValue("pawpatrol").genres).containsExactly(Genre.YOUTH)
        // « Oui-Oui, enquêtes au Pays des jouets »: a crime keyword in the title, still Jeunesse only.
        assertThat(shows.getValue("oui-oui-enquetes-au-pays-des-jouets").genres).containsExactly(Genre.YOUTH)
    }

    @Test
    fun pokemonIsOnTheJeunesseRailOnly() {
        blocking {
            val dao = FakeCatalogDao()
            for ((channel, group) in entries.groupBy { it.channelId }) {
                dao.putListing(channel, group.map { it.show }, 0L)
            }
            val rails = CatalogStore(dao, clock = { 0L }).genreRails(entries.map { it.channelId }.distinct()).associate { it.genre to it.shows }
            assertThat(rails.getValue(Genre.YOUTH).map { it.show.id.value }).contains("pokemon")
            assertThat(rails[Genre.SERIES]).isNull()
        }
    }
}
