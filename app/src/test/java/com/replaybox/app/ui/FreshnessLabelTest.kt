package com.replaybox.app.ui

import com.google.common.truth.Truth.assertThat
import com.replaybox.R
import com.replaybox.core.data.FreshnessBadge
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.EpisodeProgress
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Freshness pill: which label a badge reads, and which badge a row or a Reprendre card shows. */
class FreshnessLabelTest {

    private fun at(text: String): Instant =
        LocalDateTime.parse(text.replace(' ', 'T')).atZone(ZoneId.of("Europe/Paris")).toInstant()

    private val now = at("2026-11-07 09:00")

    private fun episode(broadcastAt: Instant? = null, availableUntil: Instant? = null) = Episode(
        id = EpisodeId("e"),
        seasonId = null,
        title = "T",
        synopsis = null,
        broadcastDate = null,
        durationSeconds = 3600,
        thumbnailUrl = null,
        broadcastAt = broadcastAt,
        availableUntil = availableUntil,
    )

    @Test
    fun `each badge has its label`() {
        assertThat(freshnessLabel(FreshnessBadge.LastDay)).isEqualTo(BadgeLabel.Text(R.string.badge_last_day))
        assertThat(freshnessLabel(FreshnessBadge.LastNight)).isEqualTo(BadgeLabel.Text(R.string.badge_last_night))
        assertThat(freshnessLabel(FreshnessBadge.New)).isEqualTo(BadgeLabel.Text(R.string.badge_new))
    }

    @Test
    fun `days left count through plurals`() {
        assertThat(freshnessLabel(FreshnessBadge.DaysLeft(1))).isEqualTo(BadgeLabel.Plural(R.plurals.badge_days_left, 1))
        assertThat(freshnessLabel(FreshnessBadge.DaysLeft(5))).isEqualTo(BadgeLabel.Plural(R.plurals.badge_days_left, 5))
    }

    @Test
    fun `a row shows the policy badge`() {
        assertThat(rowBadge(episode(broadcastAt = at("2026-11-06 21:00")), null, now)).isEqualTo(FreshnessBadge.LastNight)
        assertThat(rowBadge(episode(availableUntil = at("2026-11-07 20:00")), EpisodeProgress(600, 3600, false), now))
            .isEqualTo(FreshnessBadge.LastDay)
    }

    @Test
    fun `a watched row shows no badge`() {
        val watched = EpisodeProgress(3600, 3600, true)
        assertThat(rowBadge(episode(availableUntil = at("2026-11-07 20:00")), watched, now)).isNull()
    }

    @Test
    fun `a Reprendre card shows expiry only`() {
        assertThat(cardBadge(at("2026-11-08 20:00"), now)).isEqualTo(FreshnessBadge.DaysLeft(1))
        assertThat(cardBadge(null, now)).isNull()
    }
}
