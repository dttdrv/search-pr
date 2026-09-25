package app.pane.browser.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/**
 * Keeps Pane's own text fields (address bar, find, search boxes) out of the password manager's
 * reach. Only web pages and sign-in prompts should offer autofill, as in other browsers.
 */
fun Modifier.excludeFromAutofill(): Modifier = semantics { contentDataType = ContentDataType.None }

/** Tells the password manager what a native field holds, e.g. the HTTP sign-in prompt. */
fun Modifier.autofill(type: ContentType): Modifier = semantics { contentType = type }
