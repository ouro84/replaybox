package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.replaybox.R

/** One choice in a row; [selected] is drawn as a check, never by color alone. */
internal data class OptionChip(val label: String, val selected: Boolean, val onSelect: () -> Unit)

internal data class OptionRow(val title: String, val chips: List<OptionChip>)

/**
 * Options panel (decision Q2): Audio, Sous-titres, Qualité, Vitesse, each a
 * row of chips built from the tracks the player has (Q10). It takes the
 * controls' place at the bottom, over the same scrim; Up/Down change rows,
 * Left/Right change choices, OK selects, Back or MENU close.
 */
@Composable
internal fun OptionsPanel(rows: List<OptionRow>, modifier: Modifier = Modifier) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initial.requestFocus() }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.2f to Rb.ground.copy(alpha = 0.8f), 1f to Rb.ground.copy(alpha = 0.94f)))
            .padding(start = Rb.safeHorizontal, end = Rb.safeHorizontal, top = 72.dp, bottom = Rb.safeVertical),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.options_title), style = RbType.railTitle, color = Rb.text)
        rows.forEachIndexed { rowIndex, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, style = RbType.bodyStrong, color = Rb.textSecondary, modifier = Modifier.width(180.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val focusIndex = if (rowIndex == 0) row.chips.indexOfFirst { it.selected }.coerceAtLeast(0) else -1
                    row.chips.forEachIndexed { index, chip ->
                        key(row.title, chip.label) {
                            PanelButton(
                                label = chip.label,
                                icon = if (chip.selected) Icons.Filled.Check else null,
                                requester = if (index == focusIndex) initial else remember { FocusRequester() },
                                onClick = chip.onSelect,
                            )
                        }
                    }
                }
            }
        }
    }
}
