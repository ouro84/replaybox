package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.replaybox.R

/** One button of an [ActionDialog]; « Annuler » is added by the dialog itself. */
internal data class DialogAction(val label: String, val onClick: () -> Unit)

/**
 * Options dialog of an Épisode (DESIGN.md « Options dialogs »): a 440 dp Slate
 * Surface panel, the title in Rail Title, an optional Body Mist hint, then panel
 * buttons 24 dp below with « Annuler » first and focused. One action sits in a
 * row next to « Annuler »; more do not fit the 392 dp row and stack full width.
 */
@Composable
internal fun ActionDialog(
    title: String,
    hint: String?,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .textWidth(440.dp)
                .background(Rb.surface, Rb.cardShape)
                .padding(24.dp),
        ) {
            Text(title, style = RbType.railTitle, color = Rb.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hint?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = RbType.body,
                    color = Rb.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val stacked = actions.size > 1
            val buttonModifier = if (stacked) Modifier.fillMaxWidth() else Modifier
            val buttons: @Composable () -> Unit = {
                PanelButton(
                    stringResource(R.string.cancel),
                    icon = null,
                    requester = cancel,
                    modifier = buttonModifier,
                    onClick = onDismiss,
                )
                actions.forEachIndexed { index, action ->
                    key(index) {
                        PanelButton(
                            action.label,
                            icon = null,
                            requester = remember { FocusRequester() },
                            modifier = buttonModifier,
                            onClick = action.onClick,
                        )
                    }
                }
            }
            if (stacked) {
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
            } else {
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancel.requestFocus() }
    }
}
