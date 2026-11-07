package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.replaybox.R
import com.replaybox.core.account.ProviderAccount
import kotlinx.coroutines.launch

/**
 * Sign-in form of a Provider account, inside its Paramètres page. The
 * credentials go straight to the account's encrypted store and are never
 * logged. Initial focus is « Se connecter », not a field, so opening the page
 * to read its Santé does not raise the keyboard.
 */
@Composable
fun AccountForm(account: ProviderAccount, providerName: String, onDone: () -> Unit) {
    val storedEmail = remember { account.storedEmail() }
    var email by remember { mutableStateOf(storedEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val signOutFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { submitFocus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.login_title, providerName), style = RbType.railTitle, color = Rb.text)
        Text(
            text = if (storedEmail != null) {
                stringResource(R.string.account_signed_in, storedEmail)
            } else {
                stringResource(R.string.account_signed_out)
            },
            style = RbType.bodyStrong,
            color = Rb.text,
        )
        Text(stringResource(R.string.login_hint), style = RbType.meta, color = Rb.textSecondary)
        AccountField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.login_email),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = { runCatching { passwordFocus.requestFocus() } },
            modifier = Modifier.focusRequester(emailFocus),
        )
        AccountField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.login_password),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { runCatching { submitFocus.requestFocus() } },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.focusRequester(passwordFocus),
        )
        status?.let { Text(it, style = RbType.body, color = Rb.error) }
        if (busy) Text(stringResource(R.string.loading), style = RbType.body, color = Rb.textSecondary)
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PanelButton(
                label = stringResource(R.string.login_submit),
                icon = null,
                requester = submitFocus,
                onClick = submit@{
                    if (busy || email.isBlank() || password.isBlank()) return@submit
                    busy = true
                    status = null
                    scope.launch {
                        try {
                            account.signIn(email.trim(), password)
                            onDone()
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            status = error.message ?: error::class.java.simpleName
                        }
                        busy = false
                    }
                },
            )
            if (storedEmail != null) {
                PanelButton(
                    label = stringResource(R.string.login_signout),
                    icon = null,
                    requester = signOutFocus,
                    onClick = {
                        account.signOut()
                        onDone()
                    },
                )
            }
        }
    }
}

/**
 * Search-field look (raised slate, ring 3 dp outside on focus). A single-line
 * BasicTextField eats UP/DOWN for its cursor: they are intercepted here so
 * the D-pad still walks the form.
 */
@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = RbType.body.copy(color = Rb.text),
        cursorBrush = SolidColor(Rb.text),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrect = false),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, FIELD_RADIUS)
            .background(Rb.surfaceRaised, Rb.buttonShape)
            .width(FIELD_WIDTH)
            .height(52.dp),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = RbType.body, color = Rb.textSecondary)
                    inner()
                }
            }
        },
    )
}

private val FIELD_WIDTH = 540.dp

/** Matches Rb.buttonShape so the ring stays concentric with the field. */
private val FIELD_RADIUS = 8.dp
