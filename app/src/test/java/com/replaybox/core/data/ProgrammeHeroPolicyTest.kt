package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import org.junit.Test
import java.time.Instant

/** Programme hero: Reprendre, else the next unwatched Épisode, else the first (series) or newest (magazine). */
class ProgrammeHeroPolicyTest {

    private fun ep(id: String, number: Int? = null, broadcastAt: Instant? = null) =
        Episode(EpisodeId(id), null, id, null, null, 3000, null, broadcastAt = broadcastAt, number = number)

    private fun season(n: Int?) = Season(SeasonId("show::${n ?: ""}"), ShowId("show"), n, n?.let { "Saison $it" } ?: "Divers")

    private val s1 = season(1)
    private val s2 = season(2)
    // Listed newest Saison first, newest Épisode first (TF1).
    private val seasons = listOf(s2, s1)
    private val episodes = mapOf(
        s1.id to listOf(ep("s1e2", 2), ep("s1e1", 1)),
        s2.id to listOf(ep("s2e2", 2), ep("s2e1", 1)),
    )

    private fun resume(id: String) = ResumeItem(
        EpisodeId(id), ShowId("show"), "Show", id, null, null, null, 600, 3000, 1L,
    )

    @Test
    fun `an Episode in progress wins, with its Saison`() {
        val target = ProgrammeHeroPolicy.target(resume("s2e1"), seasons, episodes, setOf(EpisodeId("s1e1")))
        assertThat(target).isInstanceOf(HeroTarget.Resume::class.java)
        assertThat(target?.season).isEqualTo(s2)
    }

    @Test
    fun `else the next unwatched Episode after the last watched, across Saisons`() {
        val target = ProgrammeHeroPolicy.target(null, seasons, episodes, setOf(EpisodeId("s1e1"), EpisodeId("s1e2")))
        assertThat((target as HeroTarget.Play).episode.id.value).isEqualTo("s2e1")
        assertThat(target.season).isEqualTo(s2)
    }

    @Test
    fun `else the first Episode of the first Saison for a series`() {
        val target = ProgrammeHeroPolicy.target(null, seasons, episodes, emptySet())
        assertThat((target as HeroTarget.Play).episode.id.value).isEqualTo("s1e1")
        assertThat(target.season).isEqualTo(s1)
    }

    @Test
    fun `everything watched falls back to the first Episode`() {
        val all = episodes.values.flatten().map { it.id }.toSet()
        val target = ProgrammeHeroPolicy.target(null, seasons, episodes, all)
        assertThat((target as HeroTarget.Play).episode.id.value).isEqualTo("s1e1")
    }

    @Test
    fun `the newest Episode for a magazine`() {
        val magazine = season(null)
        val listed = mapOf(
            magazine.id to listOf(
                ep("tuesday", broadcastAt = Instant.parse("2026-11-03T18:00:00Z")),
                ep("thursday", broadcastAt = Instant.parse("2026-11-05T18:00:00Z")),
                ep("monday", broadcastAt = Instant.parse("2026-11-02T18:00:00Z")),
            ),
        )
        val target = ProgrammeHeroPolicy.target(null, listOf(magazine), listed, setOf(EpisodeId("monday")))
        assertThat((target as HeroTarget.Play).episode.id.value).isEqualTo("thursday")
    }

    @Test
    fun `a magazine without dates plays its first listed Episode`() {
        val magazine = season(null)
        val listed = mapOf(magazine.id to listOf(ep("first"), ep("second")))
        val target = ProgrammeHeroPolicy.target(null, listOf(magazine), listed, emptySet())
        assertThat((target as HeroTarget.Play).episode.id.value).isEqualTo("first")
    }

    @Test
    fun `nothing to play without Episodes`() {
        assertThat(ProgrammeHeroPolicy.target(null, seasons, emptyMap(), emptySet())).isNull()
    }

    @Test
    fun `a Saison is checked when all its listed Episodes are watched`() {
        val watched = setOf(EpisodeId("s1e1"), EpisodeId("s1e2"), EpisodeId("s2e1"))
        val withEmpty = episodes + (season(3).id to emptyList())
        assertThat(ProgrammeHeroPolicy.watchedSeasons(seasons + season(3), withEmpty, watched)).containsExactly(s1.id)
    }
}
