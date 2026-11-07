package com.replaybox.core.data

import com.replaybox.core.model.Episode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The single freshness badge of an Épisode (ROADMAP §M4). Labels belong to the UI layer. */
sealed interface FreshnessBadge {
    /** Availability ends before the end of today (Paris). */
    object LastDay : FreshnessBadge

    /** Availability ends [days] Paris calendar days from today, 1..7 (tomorrow = 1). */
    data class DaysLeft(val days: Int) : FreshnessBadge

    /** Broadcast yesterday (Paris) at or after 18:00. */
    object LastNight : FreshnessBadge

    /** Put online (else broadcast) less than 48 h ago. */
    object New : FreshnessBadge
}

/**
 * Picks at most one [FreshnessBadge] for an Épisode at a given instant, in the Europe/Paris
 * zone, by priority: expiry within 7 days, then last night, then new. Each rule uses only the
 * dates a Provider was measured to expose (SPEC.md); a null date skips its rule.
 */
object FreshnessPolicy {
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    /** Expiry badges cover today and the next [EXPIRY_WINDOW_DAYS] calendar days. */
    const val EXPIRY_WINDOW_DAYS = 7
    private val LAST_NIGHT_FROM: LocalTime = LocalTime.of(18, 0)
    private val NEW_FOR: Duration = Duration.ofHours(48)

    /** Availability ended: [Episode.availableUntil] is before [now]. Unknown end → not expired. */
    fun isExpired(episode: Episode, now: Instant): Boolean =
        episode.availableUntil?.isBefore(now) == true

    fun isExpired(episode: Episode, clock: Clock): Boolean = isExpired(episode, clock.instant())

    fun badge(episode: Episode, clock: Clock): FreshnessBadge? = badge(episode, clock.instant())

    fun badge(episode: Episode, now: Instant): FreshnessBadge? {
        if (isExpired(episode, now)) return null
        return expiry(episode.availableUntil, now)
            ?: lastNight(episode.broadcastAt, now)
            ?: new(episode.publishedAt ?: episode.broadcastAt, now)
    }

    /**
     * Days are Paris calendar days between today and the day of the last available moment
     * (an end at 00:00 belongs to the day before): 0 → [FreshnessBadge.LastDay]. Calendar days
     * keep « Dernier jour » and « Plus que N jours » on one scale and ignore 23 h / 25 h DST days.
     */
    private fun expiry(availableUntil: Instant?, now: Instant): FreshnessBadge? {
        availableUntil ?: return null
        val today = now.atZone(ZONE).toLocalDate()
        val lastDay = availableUntil.minusNanos(1).atZone(ZONE).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, lastDay)
        return when {
            days <= 0 -> FreshnessBadge.LastDay
            days <= EXPIRY_WINDOW_DAYS -> FreshnessBadge.DaysLeft(days.toInt())
            else -> null
        }
    }

    private fun lastNight(broadcastAt: Instant?, now: Instant): FreshnessBadge? {
        broadcastAt ?: return null
        val broadcast = broadcastAt.atZone(ZONE)
        val yesterday = now.atZone(ZONE).toLocalDate().minusDays(1)
        val aired = broadcast.toLocalDate() == yesterday && !broadcast.toLocalTime().isBefore(LAST_NIGHT_FROM)
        return if (aired) FreshnessBadge.LastNight else null
    }

    private fun new(since: Instant?, now: Instant): FreshnessBadge? {
        since ?: return null
        val age = Duration.between(since, now)
        return if (!age.isNegative && age < NEW_FOR) FreshnessBadge.New else null
    }
}
