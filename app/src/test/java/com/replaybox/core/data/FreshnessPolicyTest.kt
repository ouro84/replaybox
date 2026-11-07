package com.replaybox.core.data

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class FreshnessPolicyTest {

    private val paris = ZoneId.of("Europe/Paris")

    /** Paris wall-clock time « 2026-11-07 10:00 » to an instant (earlier offset in the overlap). */
    private fun at(text: String): Instant = LocalDateTime.parse(text.replace(' ', 'T')).atZone(paris).toInstant()

    /** Unambiguous instant for the autumn overlap hour. */
    private fun at(text: String, offsetHours: Int): Instant =
        LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.ofHours(offsetHours)).toInstant()

    private fun episode(
        broadcastAt: Instant? = null,
        publishedAt: Instant? = null,
        availableUntil: Instant? = null,
    ) = Episode(
        id = EpisodeId("e"),
        seasonId = null,
        title = "T",
        synopsis = null,
        broadcastDate = null,
        durationSeconds = null,
        thumbnailUrl = null,
        broadcastAt = broadcastAt,
        publishedAt = publishedAt,
        availableUntil = availableUntil,
    )

    private fun badge(e: Episode, now: String) = FreshnessPolicy.badge(e, at(now))

    // ---- Null fields ----

    @Test
    fun `no dates no badge`() {
        val bare = episode()
        assertThat(badge(bare, "2026-11-07 10:00")).isNull()
        assertThat(FreshnessPolicy.isExpired(bare, at("2026-11-07 10:00"))).isFalse()
    }

    // ---- Expiry ----

    @Test
    fun `expired is detected strictly after the end`() {
        val e = episode(availableUntil = at("2026-11-07 10:00"))
        assertThat(FreshnessPolicy.isExpired(e, at("2026-11-07 10:00").plusSeconds(1))).isTrue()
        assertThat(FreshnessPolicy.isExpired(e, at("2026-11-07 10:00"))).isFalse()
        assertThat(FreshnessPolicy.isExpired(e, at("2026-11-07 09:59"))).isFalse()
    }

    @Test
    fun `expired episode gets no badge even when new`() {
        val e = episode(
            broadcastAt = at("2026-11-06 21:00"),
            publishedAt = at("2026-11-06 22:00"),
            availableUntil = at("2026-11-07 08:00"),
        )
        assertThat(badge(e, "2026-11-07 09:00")).isNull()
    }

    @Test
    fun `last day when it ends before the next Paris midnight`() {
        assertThat(badge(episode(availableUntil = at("2026-11-07 23:59:59")), "2026-11-07 10:00"))
            .isEqualTo(FreshnessBadge.LastDay)
        assertThat(badge(episode(availableUntil = at("2026-11-07 10:30")), "2026-11-07 10:00"))
            .isEqualTo(FreshnessBadge.LastDay)
    }

    @Test
    fun `ending exactly at midnight is still the last day`() {
        // Available all of today, gone at 00:00: today is the last day to watch it.
        assertThat(badge(episode(availableUntil = at("2026-11-08 00:00")), "2026-11-07 10:00"))
            .isEqualTo(FreshnessBadge.LastDay)
        assertThat(badge(episode(availableUntil = at("2026-11-08 00:00:01")), "2026-11-07 10:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(1))
    }

    @Test
    fun `days left count Paris calendar days, not 24 hour blocks`() {
        // Three hours away but after midnight: tomorrow, 1 day.
        assertThat(badge(episode(availableUntil = at("2026-11-08 01:00")), "2026-11-07 22:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(1))
        // 37 hours away (two 24 h blocks begun) but ends tomorrow: 1 day.
        assertThat(badge(episode(availableUntil = at("2026-11-08 23:00")), "2026-11-07 10:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(1))
        // Ends the day after tomorrow, early: 2 days.
        assertThat(badge(episode(availableUntil = at("2026-11-09 01:00")), "2026-11-07 23:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(2))
    }

    @Test
    fun `seven days is the expiry window`() {
        assertThat(badge(episode(availableUntil = at("2026-11-14 23:00")), "2026-11-07 10:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(7))
        assertThat(badge(episode(availableUntil = at("2026-11-15 00:30")), "2026-11-07 10:00")).isNull()
    }

    @Test
    fun `autumn DST day of 25 hours is one last day`() {
        // 2026-10-25: 03:00 (+02) becomes 02:00 (+01). From 00:10 to 23:50 is 24 h 40 min.
        val now = at("2026-10-25 00:10", 2)
        val e = episode(availableUntil = at("2026-10-25 23:50", 1))
        assertThat(Duration.between(now, e.availableUntil)).isGreaterThan(Duration.ofHours(24))
        assertThat(FreshnessPolicy.badge(e, now)).isEqualTo(FreshnessBadge.LastDay)
    }

    @Test
    fun `days left across DST changes stay calendar days`() {
        // Autumn: 36 h 30 min, but tomorrow.
        assertThat(badge(episode(availableUntil = at("2026-10-25 23:30")), "2026-10-24 12:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(1))
        // Spring (2026-03-29, 23 h day): 22 h 30 min, tomorrow.
        assertThat(badge(episode(availableUntil = at("2026-03-29 23:00")), "2026-03-28 23:30"))
            .isEqualTo(FreshnessBadge.DaysLeft(1))
    }

    // ---- Last night ----

    @Test
    fun `last night is yesterday from 18h Paris`() {
        assertThat(badge(episode(broadcastAt = at("2026-11-06 18:00")), "2026-11-07 09:00"))
            .isEqualTo(FreshnessBadge.LastNight)
        assertThat(badge(episode(broadcastAt = at("2026-11-06 23:59:59")), "2026-11-07 23:59:59"))
            .isEqualTo(FreshnessBadge.LastNight)
    }

    @Test
    fun `yesterday before 18h is not last night`() {
        // 17:59:59 yesterday, 15 h ago: only « new » (broadcast fallback, no publication date).
        assertThat(badge(episode(broadcastAt = at("2026-11-06 17:59:59")), "2026-11-07 09:00"))
            .isEqualTo(FreshnessBadge.New)
    }

    @Test
    fun `after midnight is today, not last night`() {
        assertThat(badge(episode(broadcastAt = at("2026-11-07 00:30")), "2026-11-07 09:00"))
            .isEqualTo(FreshnessBadge.New)
    }

    @Test
    fun `last night at the stroke of midnight`() {
        assertThat(badge(episode(broadcastAt = at("2026-11-06 20:00")), "2026-11-07 00:00"))
            .isEqualTo(FreshnessBadge.LastNight)
        // Two nights ago is no longer last night (still new: 36 h).
        assertThat(badge(episode(broadcastAt = at("2026-11-05 21:00")), "2026-11-07 09:00"))
            .isEqualTo(FreshnessBadge.New)
    }

    @Test
    fun `last night across DST changes`() {
        assertThat(badge(episode(broadcastAt = at("2026-10-25 21:00")), "2026-10-26 08:00"))
            .isEqualTo(FreshnessBadge.LastNight)
        assertThat(badge(episode(broadcastAt = at("2026-03-29 20:00")), "2026-03-30 08:00"))
            .isEqualTo(FreshnessBadge.LastNight)
    }

    // ---- New ----

    @Test
    fun `new is published less than 48 hours ago`() {
        val now = at("2026-11-07 21:00")
        val justIn = episode(publishedAt = now.minus(Duration.ofHours(48)).plusSeconds(1))
        val tooOld = episode(publishedAt = now.minus(Duration.ofHours(48)))
        assertThat(FreshnessPolicy.badge(justIn, now)).isEqualTo(FreshnessBadge.New)
        assertThat(FreshnessPolicy.badge(tooOld, now)).isNull()
    }

    @Test
    fun `future publication is not new`() {
        assertThat(badge(episode(publishedAt = at("2026-11-08 10:00")), "2026-11-07 10:00")).isNull()
    }

    @Test
    fun `publication wins over broadcast for new`() {
        // Re-uploaded long after its broadcast: not new, though broadcast this morning.
        val e = episode(broadcastAt = at("2026-11-07 06:00"), publishedAt = at("2026-11-01 06:00"))
        assertThat(badge(e, "2026-11-07 09:00")).isNull()
        // Put online before its broadcast (avant-première): new from its publication.
        val early = episode(broadcastAt = at("2026-11-10 10:57"), publishedAt = at("2026-11-06 10:55"))
        assertThat(badge(early, "2026-11-07 09:00")).isEqualTo(FreshnessBadge.New)
    }

    @Test
    fun `broadcast instant is the fallback for new`() {
        assertThat(badge(episode(broadcastAt = at("2026-11-06 12:00")), "2026-11-07 09:00"))
            .isEqualTo(FreshnessBadge.New)
        assertThat(badge(episode(broadcastAt = at("2026-11-04 12:00")), "2026-11-07 09:00")).isNull()
    }

    // ---- Priority ----

    @Test
    fun `expiry beats last night and new`() {
        val e = episode(
            broadcastAt = at("2026-11-06 21:00"),
            publishedAt = at("2026-11-06 22:00"),
            availableUntil = at("2026-11-07 20:00"),
        )
        assertThat(badge(e, "2026-11-07 09:00")).isEqualTo(FreshnessBadge.LastDay)
        assertThat(badge(e.copy(availableUntil = at("2026-11-10 20:00")), "2026-11-07 09:00"))
            .isEqualTo(FreshnessBadge.DaysLeft(3))
    }

    @Test
    fun `last night beats new`() {
        val e = episode(broadcastAt = at("2026-11-06 21:00"), publishedAt = at("2026-11-06 22:00"))
        assertThat(badge(e, "2026-11-07 09:00")).isEqualTo(FreshnessBadge.LastNight)
    }

    @Test
    fun `far expiry falls through to the next rules`() {
        val e = episode(broadcastAt = at("2026-11-06 21:00"), availableUntil = at("2026-12-06 21:00"))
        assertThat(badge(e, "2026-11-07 09:00")).isEqualTo(FreshnessBadge.LastNight)
    }

    @Test
    fun `clock overload reads the clock instant`() {
        val now = at("2026-11-07 09:00")
        val e = episode(availableUntil = at("2026-11-07 20:00"))
        assertThat(FreshnessPolicy.badge(e, Clock.fixed(now, ZoneOffset.UTC))).isEqualTo(FreshnessBadge.LastDay)
        assertThat(FreshnessPolicy.isExpired(e, Clock.fixed(at("2026-11-07 21:00"), ZoneOffset.UTC))).isTrue()
    }

    @Test
    fun `expiry badge ignores broadcast and publication`() {
        val now = at("2026-11-07 09:00")
        assertThat(FreshnessPolicy.expiryBadge(at("2026-11-07 20:00"), now)).isEqualTo(FreshnessBadge.LastDay)
        assertThat(FreshnessPolicy.expiryBadge(at("2026-11-09 20:00"), now)).isEqualTo(FreshnessBadge.DaysLeft(2))
        assertThat(FreshnessPolicy.expiryBadge(at("2026-11-20 20:00"), now)).isNull()
        assertThat(FreshnessPolicy.expiryBadge(null, now)).isNull()
    }

    @Test
    fun `expired availability has no expiry badge`() {
        assertThat(FreshnessPolicy.expiryBadge(at("2026-11-07 08:00"), at("2026-11-07 09:00"))).isNull()
    }
}
