package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import org.junit.Test

/** Épisode suivant: the next listed Épisode of a numbered Saison, nothing else. */
class NextEpisodePolicyTest {

    private fun ep(id: String) = Episode(EpisodeId(id), null, id, null, null, 3000, null)
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
}
