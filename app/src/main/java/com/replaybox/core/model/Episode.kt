package com.replaybox.core.model

import java.time.Instant

/**
 * An Épisode (glossary). [broadcastDate] is the display string (« 04/11/2026 »). The three
 * instants feed the freshness badges (ROADMAP §M4); each Provider fills only what it was
 * measured to expose in its listing (SPEC.md, Provider sections) and leaves the rest null.
 */
data class Episode(
    val id: EpisodeId,
    val seasonId: SeasonId?,
    val title: String,
    val synopsis: String?,
    val broadcastDate: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    /** Start of the TV broadcast, time of day included. Null when unknown or not a real airing. */
    val broadcastAt: Instant? = null,
    /** Start of online availability. */
    val publishedAt: Instant? = null,
    /** End of availability. TF1 gives it to the day only: an upper bound (SPEC §Provider TF1+). */
    val availableUntil: Instant? = null,
    /**
     * Number of the Épisode within its numbered Saison, as the Chaîne lists it (TF1, France TV
     * and M6 all carry `episode`). The play order of a numbered Saison (NextEpisodePolicy);
     * null when the Chaîne gives none.
     */
    val number: Int? = null,
)
