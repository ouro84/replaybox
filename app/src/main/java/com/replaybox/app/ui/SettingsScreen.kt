package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.health.CiHealth
import com.replaybox.core.health.Health
import com.replaybox.core.health.HealthState
import com.replaybox.core.health.LocalHealth

/** Survives the trip to a Provider page: Back lands on the row that opened it. */
class SettingsUiState {
    var lastGroupId: String? = null
}

/**
 * Paramètres: one row per Provider with its Santé badge and account summary
 * (ROADMAP M3 « Paramètres par Provider »). The CI file is fetched in the
 * background; the list composes at once from what is in memory.
 */
@Composable
fun SettingsScreen(state: SettingsUiState, onOpenProvider: (String) -> Unit) {
    val groups = ProviderGroups.all
    val ci = rememberCiHealth()
    val requesters = remember { groups.associate { it.id to FocusRequester() } }

    LaunchedEffect(Unit) {
        val target = state.lastGroupId?.takeIf { it in requesters } ?: groups.first().id
        runCatching { requesters.getValue(target).requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Rb.contentStart, top = Rb.safeVertical + 8.dp, end = Rb.safeHorizontal),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = RbType.displayCompact, color = Rb.text)
        Spacer(Modifier.size(8.dp))
        for (group in groups) {
            ProviderRow(
                group = group,
                state = groupState(ci[group.id], ReplayboxApplication.localHealth.read(group.id)),
                requester = requesters.getValue(group.id),
                onClick = {
                    state.lastGroupId = group.id
                    onOpenProvider(group.id)
                },
            )
        }
    }
}

@Composable
private fun ProviderRow(group: ProviderGroup, state: HealthState, requester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .width(ROW_WIDTH)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .focusRing(focused, ROW_RADIUS)
            .background(if (focused) Rb.surfaceRaised else Rb.surface, Rb.cardShape)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.name, style = RbType.railTitle, color = Rb.text)
            Text(
                text = accountSummary(group),
                style = RbType.meta,
                color = Rb.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        HealthBadge(state)
    }
}

/** « Connecté : … », « Non connecté » or « Sans compte ». */
@Composable
internal fun accountSummary(group: ProviderGroup): String {
    val account = group.account ?: return stringResource(R.string.account_none)
    val email = account.storedEmail()
    return if (email != null) stringResource(R.string.account_signed_in, email) else stringResource(R.string.account_signed_out)
}

/** Dot plus label, never colour alone: filled white (OK), filled coral (KO), hollow Mist ring (unknown). */
@Composable
internal fun HealthBadge(state: HealthState) {
    val (label, color) = when (state) {
        HealthState.OK -> R.string.health_ok to Rb.text
        HealthState.KO -> R.string.health_ko to Rb.error
        HealthState.UNKNOWN -> R.string.health_unknown to Rb.textSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .then(
                    if (state == HealthState.UNKNOWN) {
                        Modifier.border(2.dp, color, CircleShape)
                    } else {
                        Modifier.background(color, CircleShape)
                    },
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label), style = RbType.bodyStrong, color = color, maxLines = 1)
    }
}

internal fun groupState(ci: CiHealth?, local: LocalHealth): HealthState =
    Health.combine(Health.ciState(ci), Health.localState(local))

/**
 * CI Santé for every group: what is in memory now (unknown on a cold start
 * or offline), then the fetched file. The fetch never blocks the first frame.
 */
@Composable
internal fun rememberCiHealth(): Map<String, CiHealth> {
    val source = ReplayboxApplication.ciHealth
    var health by remember { mutableStateOf(source.cached()) }
    LaunchedEffect(Unit) { health = source.health() }
    return health
}

private val ROW_WIDTH = 600.dp

/** Matches Rb.cardShape so the ring stays concentric with the row. */
private val ROW_RADIUS = 10.dp
