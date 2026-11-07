package com.replaybox.app.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.replaybox.R
import com.replaybox.core.data.FreshnessBadge
import com.replaybox.core.data.FreshnessPolicy
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeProgress
import java.time.Instant

/** The string a freshness pill reads: a plain string, or a plural counted on days. */
internal sealed interface BadgeLabel {
    data class Text(@StringRes val id: Int) : BadgeLabel
    data class Plural(@PluralsRes val id: Int, val count: Int) : BadgeLabel
}

internal fun freshnessLabel(badge: FreshnessBadge): BadgeLabel = when (badge) {
    FreshnessBadge.LastDay -> BadgeLabel.Text(R.string.badge_last_day)
    is FreshnessBadge.DaysLeft -> BadgeLabel.Plural(R.plurals.badge_days_left, badge.days)
    FreshnessBadge.LastNight -> BadgeLabel.Text(R.string.badge_last_night)
    FreshnessBadge.New -> BadgeLabel.Text(R.string.badge_new)
}

/** Épisode row on the Programme page: the policy badge, none once watched (« ✓ Vu » says it). */
internal fun rowBadge(episode: Episode, progress: EpisodeProgress?, now: Instant): FreshnessBadge? =
    if (progress?.watched == true) null else FreshnessPolicy.badge(episode, now)

/** Reprendre card: expiry only, never « Hier soir » or « Nouveau » (ROADMAP §M4). */
internal fun cardBadge(availableUntil: Instant?, now: Instant): FreshnessBadge? =
    FreshnessPolicy.expiryBadge(availableUntil, now)

/**
 * Freshness pill (DESIGN.md): Meta in Mist on a Raised Slate pill, never amber. [onRaised]
 * switches the fill to Slate Surface when the pill sits on a Raised Slate plate (focused row).
 */
@Composable
internal fun FreshnessPill(badge: FreshnessBadge, modifier: Modifier = Modifier, onRaised: Boolean = false) {
    val text = when (val label = freshnessLabel(badge)) {
        is BadgeLabel.Text -> stringResource(label.id)
        is BadgeLabel.Plural -> pluralStringResource(label.id, label.count, label.count)
    }
    Text(
        text = text,
        style = RbType.meta,
        color = Rb.textSecondary,
        maxLines = 1,
        modifier = modifier
            .background(if (onRaised) Rb.surface else Rb.surfaceRaised, Rb.pillShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
