package com.replaybox.provider.francetv

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import org.junit.Test

/**
 * The meta endpoint (apps/program/meta/<path>) carries the same category /
 * sub_categories shape as the listing items; both fixtures recorded
 * 2026-11-05 document the taxonomies behind the canonical Genre mapping.
 */
class FtvProgramMetaTest {

    @Test
    fun `marleau maps to crime thriller`() {
        val meta = FtvTestFixtures.load("ftv_program_meta_capitaine-marleau.json")
        val genres = FtvGenres.map(
            FtvJson.str(meta, "category", "url_complete"),
            FtvJson.objects(meta["sub_categories"]).mapNotNull { FtvJson.str(it, "url_complete") },
        )
        assertThat(genres).containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
    }

    @Test
    fun `magazine maps to documentary`() {
        val meta = FtvTestFixtures.load("ftv_program_meta_ca-commence-aujourd-hui.json")
        val genres = FtvGenres.map(
            FtvJson.str(meta, "category", "url_complete"),
            FtvJson.objects(meta["sub_categories"]).mapNotNull { FtvJson.str(it, "url_complete") },
        )
        assertThat(genres).containsExactly(Genre.DOCUMENTARY)
    }
}
