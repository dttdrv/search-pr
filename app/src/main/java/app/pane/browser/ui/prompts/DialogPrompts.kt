package app.pane.browser.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pane.browser.engine.prompts.AlertRequest
import app.pane.browser.engine.prompts.AuthRequest
import app.pane.browser.engine.prompts.BeforeUnloadRequest
import app.pane.browser.engine.prompts.ConfirmRequest
import app.pane.browser.engine.prompts.RepostRequest
import app.pane.browser.engine.prompts.ScriptDialogRequest
import app.pane.browser.engine.prompts.TextInputRequest
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.PaneAlert
import kotlinx.coroutines.delay

private const val FALLBACK_SOURCE = "This page"

/** `alert()`, `confirm()` and `prompt()`, titled with the site that is asking. */
@Composable
internal fun ScriptDialog(request: ScriptDialogRequest<*>, visible: Boolean, onDone: () -> Unit) {
    var optOut by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf((request as? TextInputRequest)?.defaultValue.orEmpty()) }
    val focus = remember { FocusRequester() }

    fun finish(answer: () -> Unit) {
        if (optOut) request.blockFurtherDialogs()
        answer()
        onDone()
    }

    val actions = when (request) {
        is AlertRequest -> listOf(AlertAction("OK", AlertStyle.Cancel) { finish { request.ok() } })
        is ConfirmRequest -> listOf(
            AlertAction("Cancel", AlertStyle.Cancel) { finish { request.answer(false) } },
            AlertAction("OK") { finish { request.answer(true) } },
        )
        is TextInputRequest -> listOf(
            AlertAction("Cancel", AlertStyle.Cancel) { finish { request.dismiss() } },
            AlertAction("OK") { finish { request.submit(text) } },
        )
        else -> listOf(AlertAction("OK", AlertStyle.Cancel) { finish { request.dismiss() } })
    }
    val input = request as? TextInputRequest
    val body: (@Composable () -> Unit)? = if (input == null && !request.offerOptOut) {
        null
    } else {
        @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (input != null) {
                    AlertTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = "",
                        requester = focus,
                        onImeAction = { finish { input.submit(text) } },
                    )
                }
                if (request.offerOptOut) OptOutRow(optOut) { optOut = it }
            }
        }
    }

    PaneAlert(
        visible = visible,
        title = request.source ?: FALLBACK_SOURCE,
        message = request.message.takeIf { it.isNotBlank() },
        actions = actions,
        onDismissRequest = { finish { request.dismiss() } },
        body = body,
    )
    if (input != null) {
        LaunchedEffect(Unit) {
            delay(FOCUS_DELAY_MS)
            runCatching { focus.requestFocus() }
        }
    }
}

/** HTTP authentication. Nothing typed here is remembered. */
@Composable
internal fun AuthDialog(request: AuthRequest, visible: Boolean, onDone: () -> Unit) {
    var username by remember { mutableStateOf(request.defaultUsername) }
    var password by remember { mutableStateOf("") }
    val userFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val host = request.host.ifEmpty { "This site" }

    val message = buildString {
        if (request.previousFailed) append("The user name or password was incorrect. ")
        append(if (request.isProxy) "The proxy $host requires a user name and password." else "$host requires a user name and password.")
        if (request.crossOrigin) append("\n\nThis request comes from a different site than the page you’re on.")
        if (request.insecure) append("\n\nYour password will be sent unencrypted.")
    }

    fun submit() {
        request.submit(username, password)
        onDone()
    }

    PaneAlert(
        visible = visible,
        title = if (request.isProxy) "Proxy Sign In" else "Sign In",
        message = message,
        actions = listOf(
            AlertAction("Cancel", AlertStyle.Cancel) {
                request.dismiss()
                onDone()
            },
            AlertAction("Sign In") { submit() },
        ),
        onDismissRequest = {
            request.dismiss()
            onDone()
        },
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!request.onlyPassword) {
                    AlertTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "User Name",
                        imeAction = ImeAction.Next,
                        autofillType = ContentType.Username,
                        onImeAction = { runCatching { passwordFocus.requestFocus() } },
                        requester = userFocus,
                    )
                }
                AlertTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    password = true,
                    imeAction = ImeAction.Go,
                    autofillType = ContentType.Password,
                    onImeAction = { submit() },
                    requester = passwordFocus,
                )
            }
        },
    )
    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        runCatching { if (request.onlyPassword || username.isNotEmpty()) passwordFocus.requestFocus() else userFocus.requestFocus() }
    }
}

@Composable
internal fun RepostDialog(request: RepostRequest, visible: Boolean, onDone: () -> Unit) {
    PaneAlert(
        visible = visible,
        title = "Resend Form?",
        message = "This page was made with information you entered. Sending it again may repeat an action, like a purchase.",
        actions = listOf(
            AlertAction("Cancel", AlertStyle.Cancel) {
                request.answer(false)
                onDone()
            },
            AlertAction("Resend") {
                request.answer(true)
                onDone()
            },
        ),
        onDismissRequest = {
            request.answer(false)
            onDone()
        },
    )
}

@Composable
internal fun BeforeUnloadDialog(request: BeforeUnloadRequest, visible: Boolean, onDone: () -> Unit) {
    PaneAlert(
        visible = visible,
        title = "Leave This Page?",
        message = "Changes you made may not be saved.",
        actions = listOf(
            AlertAction("Stay", AlertStyle.Cancel) {
                request.answer(false)
                onDone()
            },
            AlertAction("Leave", AlertStyle.Destructive) {
                request.answer(true)
                onDone()
            },
        ),
        onDismissRequest = {
            request.answer(false)
            onDone()
        },
    )
}

private const val FOCUS_DELAY_MS = 80L
