package com.replaybox.app.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.replaybox.R

/**
 * Search on mobile (grilling 2026-11-10): the system keyboard in a one-line field, in place of
 * the D-pad keyboard. An EditText rather than BasicTextField: Compose 1.4 cannot ask the IME
 * not to go fullscreen in landscape (IME_FLAG_NO_EXTRACT_UI), which would hide the results.
 */
@Composable
fun SearchField(
    query: String,
    openKeyboard: Boolean,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.search_hint)
    // The EditText is built once; its watcher calls the latest callback.
    val currentOnQuery by rememberUpdatedState(onQuery)
    val holder = remember { arrayOfNulls<EditText>(1) }
    // Leaving Search (a result, the rail) removes the focused field: close its keyboard too.
    DisposableEffect(Unit) {
        onDispose { holder[0]?.let { hideKeyboard(it.context, it) } }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                EditText(context).apply {
                    holder[0] = this
                    background = null
                    setPadding(0, 0, 0, 0)
                    setSingleLine()
                    imeOptions = EditorInfo.IME_ACTION_SEARCH or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        EditorInfo.IME_FLAG_NO_FULLSCREEN
                    // No suggestions, no autocorrect: titles are not dictionary words.
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    filters = arrayOf(InputFilter.LengthFilter(SearchKeyboard.MAX_LENGTH))
                    setTextColor(Rb.text.toArgb())
                    setHintTextColor(Rb.textSecondary.toArgb())
                    this.hint = hint
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, RbType.displayCompact.fontSize.value)
                    typeface = Typeface.DEFAULT_BOLD
                    setText(query)
                    setSelection(text.length)
                    doAfterTextChanged { currentOnQuery(it?.toString().orEmpty()) }
                    setOnEditorActionListener { view, actionId, _ ->
                        if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                        hideKeyboard(view.context, view)
                        view.clearFocus()
                        true
                    }
                    if (openKeyboard) {
                        post {
                            requestFocus()
                            context.getSystemService(InputMethodManager::class.java)
                                ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .size(48.dp)
                    // The watcher above empties the query; the keyboard stays as it is.
                    .clickable { holder[0]?.setText("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear), tint = Rb.text)
            }
        }
    }
}

private fun hideKeyboard(context: Context, view: android.view.View) {
    context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
}
