package com.replaybox.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.health.CiHealth
import com.replaybox.core.health.HealthState
import com.replaybox.core.health.LocalHealth
import com.replaybox.core.health.Outcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A Provider's page in Paramètres: its Santé (badge, last night's CI check,
 * what this box observed), then its account form when it needs one.
 */
@Composable
fun ProviderScreen(groupId: String) {
    val group = ProviderGroups.byId(groupId) ?: return
    val ci = rememberCiHealth()[group.id]
    // Re-read after a sign-in or sign-out so the summary follows.
    var accountVersion by remember { mutableStateOf(0) }
    val local = remember(accountVersion) { ReplayboxApplication.localHealth.read(group.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = Rb.contentStart, top = Rb.safeVertical + 8.dp, end = Rb.safeHorizontal, bottom = Rb.safeVertical),
    ) {
        Text(group.name, style = RbType.displayCompact, color = Rb.text)
        Spacer(Modifier.size(12.dp))
        HealthBadge(groupState(ci, local))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.widthIn(max = TEXT_WIDTH)) {
            Text(stringResource(R.string.health_ci_line, ciText(ci)), style = RbType.body, color = Rb.textSecondary)
            Text(stringResource(R.string.health_box_line, boxText(local)), style = RbType.body, color = Rb.textSecondary)
        }
        group.account?.let { account ->
            Spacer(Modifier.size(28.dp))
            key(accountVersion) {
                AccountForm(
                    account = account,
                    providerName = group.name,
                    onDone = { accountVersion += 1 },
                )
            }
        }
    }
}

@Composable
private fun ciText(ci: CiHealth?): String = when (ci?.state) {
    null, HealthState.UNKNOWN -> stringResource(R.string.health_ci_unknown)
    HealthState.OK -> withDate(stringResource(R.string.health_ok).lowercaseFirst(), ci.checkedAt)
    HealthState.KO -> withDate(
        stringResource(R.string.health_ci_failed, ci.step ?: "?", ci.error ?: "?"),
        ci.checkedAt,
    )
}

@Composable
private fun boxText(local: LocalHealth): String {
    val parts = listOfNotNull(
        local.catalogue?.let { stringResource(R.string.health_box_catalogue, outcomeText(it)) },
        local.stream?.let { stringResource(R.string.health_box_stream, outcomeText(it)) },
    )
    return if (parts.isEmpty()) stringResource(R.string.health_box_none) else parts.joinToString(" · ")
}

/** « fonctionne le 07/11 à 21:02 » or « en panne le 07/11 à 21:05 (IOException: HTTP 503) ». */
@Composable
private fun outcomeText(outcome: Outcome): String {
    val state = stringResource(if (outcome.ok) R.string.health_ok else R.string.health_ko).lowercaseFirst()
    val dated = withDate(state, outcome.at)
    return if (!outcome.ok && outcome.error != null) "$dated (${outcome.error})" else dated
}

@Composable
private fun withDate(text: String, at: Long?): String {
    if (at == null) return text
    val date = Date(at)
    val day = SimpleDateFormat("dd/MM", Locale.FRANCE).format(date)
    val time = SimpleDateFormat("HH:mm", Locale.FRANCE).format(date)
    return "$text ${stringResource(R.string.health_checked_at, day, time)}"
}

private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercase(Locale.FRANCE) }

/** Body text column: about 75 characters at 18 sp, inside the safe frame. */
private val TEXT_WIDTH = 720.dp
