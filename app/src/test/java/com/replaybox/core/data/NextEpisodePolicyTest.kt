package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import org.junit.Test

/** Épisode suivant: play order by Épisode number, then on to the following numbered Saison. */
class NextEpisodePolicyTest {

    private fun ep(id: String, number: Int? = null, season: String? = null) =
        Episode(EpisodeId(id), season?.let(::SeasonId), id, null, null, 3000, null, number = number)

    private fun season(n: Int?) = Season(SeasonId("show::${n ?: ""}"), ShowId("show"), n, n?.let { "Saison $it" } ?: "Divers")

    private val queue = listOf(ep("s4e1"), ep("s4e2"), ep("s4e3"))

    @Test
    fun `the Episode after the current one`() {
        assertThat(NextEpisodePolicy.next(queue, EpisodeId("s4e1"))?.id).isEqualTo(EpisodeId("s4e2"))
    }

    @Test
    fun `nothing after the last, an unknown or an empty queue`() {
        assertThat(NextEpisodePolicy.next(queue, EpisodeId("s4e3"))).isNull()
        assertThat(NextEpisodePolicy.next(queue, EpisodeId("nope"))).isNull()
        assertThat(NextEpisodePolicy.next(emptyList(), EpisodeId("s4e1"))).isNull()
    }

    @Test
    fun `play order follows the Episode number, not the newest first listing`() {
        // TF1 and France TV list a Saison newest first (SPEC, fixtures).
        val listed = listOf(ep("e3", 3), ep("e2", 2), ep("bonus"), ep("e1", 1))
        assertThat(NextEpisodePolicy.playOrder(listed).map { it.id.value })
            .containsExactly("e1", "e2", "e3", "bonus").inOrder()
    }

    @Test
    fun `play order keeps the Chaine order among equal numbers`() {
        // M6 lists the parts of one Épisode in order under the same number.
        val parts = listOf(ep("e2", 2), ep("e1p1", 1), ep("e1p2", 1))
        assertThat(NextEpisodePolicy.playOrder(parts).map { it.id.value })
            .containsExactly("e1p1", "e1p2", "e2").inOrder()
    }

    @Test
    fun `numbered Saisons ascending whatever the listing order`() {
        val seasons = listOf(season(3), season(1), season(null), season(2))
        assertThat(NextEpisodePolicy.numberedSeasons(seasons).map { it.number }).containsExactly(1, 2, 3).inOrder()
        assertThat(NextEpisodePolicy.isSeries(seasons)).isTrue()
        assertThat(NextEpisodePolicy.isSeries(listOf(season(null)))).isFalse()
    }

    private val s1 = season(1)
    private val s2 = season(2)
    private val s3 = season(3)
    private val seasons = listOf(s3, s2, s1)
    private val episodes = mapOf(
        s1.id to listOf(ep("s1e2", 2), ep("s1e1", 1)),
        s2.id to emptyList(),
        s3.id to listOf(ep("s3e2", 2), ep("s3e1", 1)),
    )

    @Test
    fun `next within the Saison in play order`() {
        assertThat(NextEpisodePolicy.next(seasons, episodes, EpisodeId("s1e1"))?.episode?.id?.value).isEqualTo("s1e2")
    }

    @Test
    fun `after the last Episode of a Saison, the first of the next numbered Saison that lists any`() {
        val next = NextEpisodePolicy.next(seasons, episodes, EpisodeId("s1e2"))
        assertThat(next?.episode?.id?.value).isEqualTo("s3e1")
        assertThat(next?.season).isEqualTo(s3)
    }

    @Test
    fun `nothing after the last Episode of the last Saison`() {
        assertThat(NextEpisodePolicy.next(seasons, episodes, EpisodeId("s3e2"))).isNull()
    }

    @Test
    fun `an unnumbered Saison has no next`() {
        val magazine = season(null)
        val listed = mapOf(magazine.id to listOf(ep("new"), ep("old")))
        assertThat(NextEpisodePolicy.next(listOf(magazine), listed, EpisodeId("new"))).isNull()
    }

    @Test
    fun `next unwatched follows the last watched across Saisons`() {
        val order = NextEpisodePolicy.programmeOrder(seasons, episodes)
        val watched = setOf(EpisodeId("s1e1"), EpisodeId("s1e2"))
        assertThat(NextEpisodePolicy.nextUnwatched(order, watched)?.episode?.id?.value).isEqualTo("s3e1")
    }

    @Test
    fun `next unwatched skips watched Episodes after the last watched gap`() {
        val order = NextEpisodePolicy.programmeOrder(seasons, episodes)
        // s1e2 unwatched before the last watched one does not count: the viewer moved on.
        val watched = setOf(EpisodeId("s1e1"), EpisodeId("s3e1"))
        assertThat(NextEpisodePolicy.nextUnwatched(order, watched)?.episode?.id?.value).isEqualTo("s3e2")
    }

    @Test
    fun `no next unwatched when nothing or everything was watched`() {
        val order = NextEpisodePolicy.programmeOrder(seasons, episodes)
        assertThat(NextEpisodePolicy.nextUnwatched(order, emptySet())).isNull()
        assertThat(NextEpisodePolicy.nextUnwatched(order, order.map { it.episode.id }.toSet())).isNull()
    }
}
