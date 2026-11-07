package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.UpNextItem
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** « À suivre »: what a pinned Programme offers, one card per Programme, newest event first. */
class UpNextPolicyTest {

    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    private fun ep(id: String, number: Int? = null, broadcastAt: Instant? = null, availableUntil: Instant? = null) =
        Episode(EpisodeId(id), null, id, null, null, 3000, null, broadcastAt = broadcastAt, availableUntil = availableUntil, number = number)

    private fun season(n: Int?) = Season(SeasonId("show::${n ?: ""}"), ShowId("show"), n, n?.let { "Saison $it" } ?: "Divers")

    private val s1 = season(1)
    private val s2 = season(2)
    private val series = listOf(s2, s1)
    private val seriesEpisodes = mapOf(
        s1.id to listOf(ep("s1e2", 2), ep("s1e1", 1)),
        s2.id to listOf(ep("s2e1", 1)),
    )

    @Test
    fun `a series offers the Episode after the last watched one`() {
        val offered = UpNextPolicy.candidate(series, seriesEpisodes, setOf(EpisodeId("s1e2")), now)
        assertThat(offered?.episode?.id?.value).isEqualTo("s2e1")
        assertThat(offered?.season).isEqualTo(s2)
    }

    @Test
    fun `a series nobody watched offers nothing`() {
        assertThat(UpNextPolicy.candidate(series, seriesEpisodes, emptySet(), now)).isNull()
    }

    @Test
    fun `a series skips an expired next Episode`() {
        val episodes = mapOf(
            s1.id to listOf(ep("s1e1", 1), ep("s1e2", 2, availableUntil = daysAgo(1))),
            s2.id to listOf(ep("s2e1", 1)),
        )
        assertThat(UpNextPolicy.candidate(series, episodes, setOf(EpisodeId("s1e1")), now)?.episode?.id?.value)
            .isEqualTo("s2e1")
    }

    @Test
    fun `a magazine offers its newest unwatched Episode of the last seven days`() {
        val magazine = season(null)
        val episodes = mapOf(
            magazine.id to listOf(
                ep("yesterday", broadcastAt = daysAgo(1)),
                ep("today", broadcastAt = now.minusSeconds(3600)),
                ep("lastWeek", broadcastAt = daysAgo(6)),
            ),
        )
        assertThat(UpNextPolicy.candidate(listOf(magazine), episodes, emptySet(), now)?.episode?.id?.value).isEqualTo("today")
        assertThat(UpNextPolicy.candidate(listOf(magazine), episodes, setOf(EpisodeId("today")), now)?.episode?.id?.value)
            .isEqualTo("yesterday")
    }

    @Test
    fun `a magazine offers nothing older than seven days, undated or expired`() {
        val magazine = season(null)
        val episodes = mapOf(
            magazine.id to listOf(
                ep("old", broadcastAt = daysAgo(8)),
                ep("undated"),
                ep("gone", broadcastAt = daysAgo(1), availableUntil = now.minusSeconds(60)),
            ),
        )
        assertThat(UpNextPolicy.candidate(listOf(magazine), episodes, emptySet(), now)).isNull()
    }

    private fun inProgress(show: String, episode: String, at: Long) = UpNextItem.InProgress(
        ResumeItem(EpisodeId(episode), ShowId(show), show, episode, null, null, null, 600, 3000, at),
    )

    private fun fresh(show: String, episode: String, at: Long) = UpNextItem.NewEpisode(
        ShowId(show), show, ep(episode), null, null, null, at,
    )

    @Test
    fun `one card per Programme, in progress wins, newest event first`() {
        val rail = UpNextPolicy.merge(
            inProgress = listOf(inProgress("profilage", "p-s2e3", 300), inProgress("tracker", "t-e1", 100)),
            fresh = listOf(fresh("profilage", "p-s2e4", 900), fresh("soeurs", "s-e5", 200)),
            dismissed = emptyMap(),
            limit = 20,
        )
        assertThat(rail.map { it.episodeId.value }).containsExactly("p-s2e3", "s-e5", "t-e1").inOrder()
    }

    @Test
    fun `a dismissal hides that Episode only`() {
        val dismissed = mapOf("soeurs" to "s-e5")
        assertThat(UpNextPolicy.merge(emptyList(), listOf(fresh("soeurs", "s-e5", 200)), dismissed, 20)).isEmpty()
        assertThat(UpNextPolicy.merge(emptyList(), listOf(fresh("soeurs", "s-e6", 300)), dismissed, 20).map { it.episodeId.value })
            .containsExactly("s-e6")
    }

    @Test
    fun `the rail is capped`() {
        val many = (1..5).map { fresh("show$it", "e$it", it.toLong()) }
        assertThat(UpNextPolicy.merge(emptyList(), many, emptyMap(), 3).map { it.episodeId.value })
            .containsExactly("e5", "e4", "e3").inOrder()
    }
}
