package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Tf1VideoBySlugParserTest {

    @Test
    fun `extracts streamId and metadata`() {
        val detail = Tf1Parsers.videoDetail(TestFixtures.load("tf1_video_by_slug.json"))
        assertThat(detail).isNotNull()
        assertThat(detail!!.slug).isEqualTo("hpi-s05-e08-supernova-15207545")
        assertThat(detail.streamId).isEqualTo("14564313")
        assertThat(detail.title).isEqualTo("HPI - Saison 5 - Episode 8 - Supernova")
        assertThat(detail.type).isEqualTo("REPLAY")
        assertThat(detail.season).isEqualTo(5)
        assertThat(detail.episode).isEqualTo(8)
    }
}
