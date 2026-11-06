package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ShowId
import org.junit.Test

/** Programme page lookups: listed Programme, its Reprendre target, Progression per Épisode. */
class ProgrammePageTest {

    private fun context(show: String, episode: String) =
        ProgressContext(ShowId(show), show.uppercase(), episode, "https://img/$episode.jpg")

    @Test
    fun `episodeProgress reports in progress and watched`() {
        blocking {
            val catalog = CatalogStore(FakeCatalogDao()) { 1_000L }
            catalog.saveProgress(EpisodeId("e1"), 600, 3600)
            catalog.saveProgress(EpisodeId("e2"), 3500, 3600)
            catalog.saveProgress(EpisodeId("e3"), 10, 3600)

            val progress = catalog.episodeProgress(listOf(EpisodeId("e1"), EpisodeId("e2"), EpisodeId("e3")))

            val e1 = progress.getValue(EpisodeId("e1"))
            assertThat(e1.watched).isFalse()
            assertThat(e1.watchedRatio).isEqualTo(600 / 3600f)
            assertThat(e1.remainingSeconds).isEqualTo(3000)
            assertThat(progress.getValue(EpisodeId("e2")).watched).isTrue()
            assertThat(progress).doesNotContainKey(EpisodeId("e3"))
        }
    }

    @Test
    fun `episodeProgress without duration is in progress without ratio`() {
        blocking {
            val catalog = CatalogStore(FakeCatalogDao()) { 1_000L }
            catalog.saveProgress(EpisodeId("e1"), 600, null)

            val e1 = catalog.episodeProgress(listOf(EpisodeId("e1"))).getValue(EpisodeId("e1"))

            assertThat(e1.watched).isFalse()
            assertThat(e1.watchedRatio).isNull()
            assertThat(e1.remainingSeconds).isNull()
        }
    }

    @Test
    fun `listedShow finds the Chaîne listing the Programme`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao) { 1_000L }
            dao.putListing("france-2", listOf(show("pitch", "Le pitch cinéma")), 0)

            val listed = catalog.listedShow(ShowId("pitch"), listOf("tf1", "france-2"))

            assertThat(listed?.channelId).isEqualTo("france-2")
            assertThat(listed?.show?.title).isEqualTo("Le pitch cinéma")
        }
    }

    @Test
    fun `listedShow returns null for unknown Programme`() {
        blocking {
            val catalog = CatalogStore(FakeCatalogDao()) { 1_000L }
            assertThat(catalog.listedShow(ShowId("nope"), listOf("tf1"))).isNull()
        }
    }

    @Test
    fun `resumeFor returns the newest unfinished Episode of this Programme only`() {
        blocking {
            var now = 1_000L
            val catalog = CatalogStore(FakeCatalogDao()) { now }
            catalog.saveProgress(EpisodeId("koh-e1"), 600, 3600, context("koh", "e1"))
            now += 10
            // Finished and newer: not a Reprendre target.
            catalog.saveProgress(EpisodeId("koh-e2"), 3500, 3600, context("koh", "e2"))
            now += 10
            catalog.saveProgress(EpisodeId("wag-e1"), 900, 3600, context("wagner", "w1"))

            assertThat(catalog.resumeFor(ShowId("koh"), listOf("tf1"))?.episodeId).isEqualTo(EpisodeId("koh-e1"))
            assertThat(catalog.resumeFor(ShowId("nope"), listOf("tf1"))).isNull()
        }
    }
}
