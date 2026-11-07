package com.replaybox.core.model

/**
 * Rules on a canonical Genre set that hold for every Provider (ROADMAP M4, grilling
 * 2026-11-07). Each Provider maps its own taxonomy (Tf1Genres, FtvGenres, M6Genres);
 * CatalogStore then applies these rules to every listing it serves, fresh or cached, so an
 * Index stored before a rule changed follows it on the next read.
 */
object Genres {

    /**
     * Jeunesse is exclusive: a Programme mapped to it gets no other Genre, so kids'
     * Programmes never land in the Séries (or any other) rail next to adult ones.
     */
    fun canonical(genres: Set<Genre>): Set<Genre> =
        if (Genre.YOUTH in genres && genres.size > 1) setOf(Genre.YOUTH) else genres

    /** [show] with [canonical] Genres; the same instance when nothing changes. */
    fun canonical(show: Show): Show {
        val genres = canonical(show.genres)
        return if (genres == show.genres) show else show.copy(genres = genres)
    }
}
