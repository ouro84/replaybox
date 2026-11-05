package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.ShowId
import org.junit.Test

/** Progress + Favorites: device-local, never synchronized (glossary). */
class ProgressFavoritesTest {

    @Test
    fun `progress round-trips and feeds the resume policy`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            val episode = EpisodeId("tf1::show::e1")

            catalog.saveProgress(episode, 0, null)
            assertThat(catalog.progressFor(episode)).isNull()
            assertThat(catalog.resumeAtFor(episode)).isNull()

            catalog.saveProgress(episode, 600, 3600)
            val progress = catalog.progressFor(episode)!!
            assertThat(progress.positionSeconds).isEqualTo(600)
            assertThat(catalog.resumeAtFor(episode)).isEqualTo(600_000)

            // Finished Épisode: no resume proposal.
            catalog.saveProgress(episode, 3590, 3600)
            assertThat(catalog.resumeAtFor(episode)).isNull()
        }
    }

    @Test
    fun `favorite toggles and keeps recency order`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            dao.putListing("tf1", listOf(show("s1", "Alpha"), show("s2", "Beta")), 0)

            assertThat(catalog.isFavorite(ShowId("s1"))).isFalse()
            assertThat(catalog.toggleFavorite(ShowId("s2"))).isTrue()
            assertThat(catalog.toggleFavorite(ShowId("s1"))).isTrue()
            assertThat(catalog.toggleFavorite(ShowId("s2"))).isFalse()

            val favorites = catalog.favoriteShows(listOf("tf1"))
            assertThat(favorites.map { it.id.value }).containsExactly("s1")
        }
    }

    @Test
    fun `favorite shows missing from the cache are skipped`() {
        blocking {
            val dao = FakeCatalogDao()
            val catalog = CatalogStore(dao)
            dao.putListing("tf1", listOf(show("s1", "Alpha")), 0)
            catalog.toggleFavorite(ShowId("gone"))
            assertThat(catalog.favoriteShows(listOf("tf1"))).isEmpty()
        }
    }
}

class ResumePolicyTest {

    private fun progress(seconds: Long) =
        com.replaybox.core.model.Progress(EpisodeId("e"), seconds, 0)

    @Test
    fun `no progress starts from zero`() {
        assertThat(ResumePolicy.resumeAt(null, 3600)).isNull()
    }

    @Test
    fun `tiny progress starts from zero`() {
        assertThat(ResumePolicy.resumeAt(progress(10), 3600)).isNull()
    }

    @Test
    fun `mid progress resumes`() {
        assertThat(ResumePolicy.resumeAt(progress(600), 3600)).isEqualTo(600_000)
    }

    @Test
    fun `finished episode starts from zero`() {
        assertThat(ResumePolicy.resumeAt(progress(3590), 3600)).isNull()
    }

    @Test
    fun `unknown duration resumes when meaningful`() {
        assertThat(ResumePolicy.resumeAt(progress(600), null)).isEqualTo(600_000)
        assertThat(ResumePolicy.resumeAt(progress(10), null)).isNull()
    }
}
