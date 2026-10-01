package app.pane.browser.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.floating
import kotlinx.coroutines.delay

data class Toast(
    val message: String,
    val icon: ImageVector? = null,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val durationMs: Long = 3200,
    val id: Long = System.nanoTime(),
)

val LocalToasts = androidx.compose.runtime.staticCompositionLocalOf { ToastState() }

@Stable
class ToastState {
    var current by mutableStateOf<Toast?>(null)
        private set

    fun show(message: String, icon: ImageVector? = null, actionLabel: String? = null, action: (() -> Unit)? = null) {
        current = Toast(message, icon, actionLabel, action)
    }

    fun dismiss() {
        current = null
    }
}

/** A small solid pill that drops in from the top: a short line and, sometimes, one text action. */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val toast = state.current
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(toast.durationMs)
            if (state.current?.id == toast.id) state.dismiss()
        }
    }
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = toast,
            transitionSpec = {
                (slideInVertically(Motion.bouncy()) { -it * 2 } + scaleIn(Motion.bouncy(), initialScale = 0.85f) + fadeIn(Motion.fade(120)))
                    .togetherWith(slideOutVertically(Motion.smooth()) { -it * 2 } + scaleOut(Motion.smooth(), targetScale = 0.85f) + fadeOut(Motion.fade(150)))
            },
            contentKey = { it?.id },
            label = "toast",
        ) { t ->
            if (t != null) {
                Row(
                    Modifier
                        .widthIn(max = 420.dp)
                        .heightIn(min = 44.dp)
                        .floating(PaneShapes.pill, shadow = 8.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        t.message,
                        style = PaneTheme.type.subheadline,
                        color = colors.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (t.actionLabel != null && t.action != null) {
                        Text(
                            t.actionLabel,
                            style = PaneTheme.type.headline,
                            color = colors.label,
                            maxLines = 1,
                            modifier = Modifier
                                .pressDim {
                                    t.action.invoke()
                                    state.dismiss()
                                }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            } else {
                Box(Modifier.size(0.dp))
            }
        }
    }
}

