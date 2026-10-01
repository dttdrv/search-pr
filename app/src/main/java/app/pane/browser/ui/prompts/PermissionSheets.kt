package app.pane.browser.ui.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pane.browser.engine.prompts.ContentPermissionRequest
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.theme.entrance

/**
 * "example.com wants to use your location": the title says it all, then a Remember switch (on by
 * default; absent in private tabs, which keep nothing), a solid Allow and an outlined Don't Allow.
 * Swiping the sheet away declines for now.
 */
@Composable
internal fun ContentPermissionSheet(request: ContentPermissionRequest, visible: Boolean, onDone: () -> Unit) {
    var keep by remember { mutableStateOf(true) }

    PaneSheet(visible = visible, onDismiss = {
        request.deny(remember = false)
        onDone()
    }) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            SheetTitle(request.title, Modifier.entrance(1))
            if (request.canRemember) {
                FlatSection(modifier = Modifier.entrance(2)) {
                    row {
                        FlatRow(
                            "Remember this site",
                            onClick = { keep = !keep },
                            trailing = { PaneSwitch(checked = keep, onCheckedChange = { keep = it }) },
                        )
                    }
                }
            }
        }
        SheetButtons(
            primary = "Allow",
            onPrimary = {
                request.allow(remember = keep)
                onDone()
            },
            modifier = Modifier.entrance(3),
            secondary = "Don’t Allow",
            onSecondary = {
                request.deny(remember = keep)
                onDone()
            },
        )
    }
}
