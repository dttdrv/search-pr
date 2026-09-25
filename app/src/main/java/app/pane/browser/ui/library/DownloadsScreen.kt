package app.pane.browser.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.data.DownloadRecord
import app.pane.browser.data.DownloadStatus
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.download.FileNames
import app.pane.core.library.ByteSizes
import app.pane.core.library.HistoryGrouping
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.launch

/**
 * Downloaded files, grouped by day. Rows open the file in another app; files that could run code
 * (APKs, scripts) are flagged and need a second confirmation before they open.
 */
@Composable
fun DownloadsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
    val backLabel = rememberBackLabel(Route.Downloads)
    val downloads = container.downloads

    val records by remember { container.downloadsRepository.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val groups = remember(records) { HistoryGrouping.group(records.orEmpty(), System.currentTimeMillis()) { it.created } }
    val hasFinished = records.orEmpty().any { it.isFinished }

    var menuTarget by remember { mutableStateOf<DownloadRecord?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var warnTarget by remember { mutableStateOf<DownloadRecord?>(null) }
    var warnVisible by remember { mutableStateOf(false) }

    fun showMenu(record: DownloadRecord) {
        menuTarget = record
        menuVisible = true
    }

    fun openFile(record: DownloadRecord, confirmed: Boolean = false) {
        if (!confirmed && FileNames.isPotentiallyDangerous(record.fileName)) {
            warnTarget = record
            warnVisible = true
            return
        }
        val intent = downloads.viewIntent(record) ?: return
        startOrToast(context, intent, toasts, "No app on this device can open ${record.fileName}")
    }

    fun retryDownload(record: DownloadRecord) {
        downloads.download(record.url, private = false)
        downloads.remove(record, deleteFile = false)
    }

    fun onTap(record: DownloadRecord) {
        when {
            record.status == DownloadStatus.Completed -> openFile(record)
            record.isFinished && record.url.isNotEmpty() -> retryDownload(record)
            else -> showMenu(record)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Downloads",
            onBack = navigator::pop,
            backLabel = backLabel,
            actions = {
                TextButton(
                    "Clear",
                    enabled = hasFinished,
                    onClick = {
                        container.scope.launch {
                            container.downloadsRepository.clearFinished()
                            toasts.show("Download list cleared", PaneIcons.Check)
                        }
                    },
                )
            },
        ) {
            val loaded = records
            when {
                loaded == null -> Unit
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = PaneIcons.Download,
                        title = "No Downloads",
                        message = "Files you download appear here, and stay on your device if you clear the list.",
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> {
                    groups.forEach { group ->
                        item(key = "section:${group.section.daysAgo}") {
                            SectionTitle(group.section.title(), Modifier.animateItem())
                        }
                        itemsIndexed(group.items, key = { _, r -> r.id }) { index, record ->
                            val first = index == 0
                            SwipeToDelete(
                                onDelete = { downloads.remove(record, deleteFile = false) },
                                label = "Remove",
                                modifier = Modifier.animateItem().groupedItem(first, index == group.items.lastIndex, colors.surface),
                            ) {
                                Column {
                                    if (!first) RowSeparator()
                                    DownloadRow(
                                        record = record,
                                        onClick = { onTap(record) },
                                        onLongClick = { showMenu(record) },
                                        onCancel = { downloads.cancel(record.id) },
                                        onRetry = { retryDownload(record) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "footer") {
                        SectionFooter(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "Files are saved to the Downloads folder on this device. Clearing the list keeps them."
                            } else {
                                "Files are saved in Pane's folder on this device and are removed if you uninstall Pane."
                            },
                            Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        val target = menuTarget
        ActionSheet(
            visible = menuVisible,
            title = target?.fileName.orEmpty(),
            subtitle = target?.let { statusText(it) },
            leading = if (target != null) {
                {
                    val kind = FileKind.of(target.fileName, target.mime)
                    IconTile(kind.icon, kind.color)
                }
            } else {
                null
            },
            onDismiss = { menuVisible = false },
            actions = if (target == null) {
                emptyList()
            } else {
                menuActions(
                    record = target,
                    onOpen = { openFile(target) },
                    onShare = {
                        downloads.shareIntent(target)?.let { startOrToast(context, it, toasts, "No app can share this file") }
                    },
                    onRetry = { retryDownload(target) },
                    onCancel = { downloads.cancel(target.id) },
                    onCopyLink = { copyToClipboard(context, target.url, toasts) },
                    onRemove = { deleteFile ->
                        downloads.remove(target, deleteFile)
                        if (deleteFile) toasts.show("Deleted ${target.fileName}", PaneIcons.Trash)
                    },
                )
            },
        )

        val warned = warnTarget
        PaneAlert(
            visible = warnVisible,
            title = "Open “${warned?.fileName.orEmpty()}”?",
            message = "This type of file can install apps or run code on your device. Only open it if you trust where it came from.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { warnVisible = false },
                AlertAction("Open", AlertStyle.Destructive) {
                    warnVisible = false
                    warned?.let { openFile(it, confirmed = true) }
                },
            ),
            onDismissRequest = { warnVisible = false },
        )
    }
}

private val DownloadRecord.isFinished: Boolean
    get() = status == DownloadStatus.Completed || status == DownloadStatus.Failed || status == DownloadStatus.Cancelled

private val DownloadRecord.isActive: Boolean
    get() = status == DownloadStatus.Running || status == DownloadStatus.Pending || status == DownloadStatus.Paused

/** The long-press menu for a download; what it offers depends on where the download stands. */
private fun menuActions(
    record: DownloadRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onCopyLink: () -> Unit,
    onRemove: (deleteFile: Boolean) -> Unit,
): List<SheetAction> {
    val actions = mutableListOf<SheetAction>()
    when {
        record.status == DownloadStatus.Completed -> {
            actions += SheetAction("Open", PaneIcons.OpenExternal, onClick = onOpen)
            actions += SheetAction("Share…", PaneIcons.Share, onClick = onShare)
        }
        record.isActive -> actions += SheetAction("Cancel Download", PaneIcons.Stop, destructive = true, onClick = onCancel)
        record.url.isNotEmpty() -> actions += SheetAction("Try Again", PaneIcons.Reload, onClick = onRetry)
    }
    if (record.url.isNotEmpty()) actions += SheetAction("Copy Download Link", PaneIcons.Link, onClick = onCopyLink)
    if (!record.isActive) actions += SheetAction("Remove from List", PaneIcons.Close) { onRemove(false) }
    if (record.status == DownloadStatus.Completed) {
        actions += SheetAction("Delete File", PaneIcons.Trash, destructive = true) { onRemove(true) }
    }
    return actions
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = PaneTheme.colors
    val kind = remember(record.fileName, record.mime) { FileKind.of(record.fileName, record.mime) }
    val dangerous = remember(record.fileName) { FileNames.isPotentiallyDangerous(record.fileName) }
    LibraryRow(
        title = record.fileName,
        subtitle = statusText(record),
        subtitleColor = when {
            record.status == DownloadStatus.Failed -> colors.destructive
            dangerous -> colors.warning
            else -> colors.secondaryLabel
        },
        leading = { IconTile(kind.icon, kind.color) },
        onClick = onClick,
        onLongClick = onLongClick,
        below = if (record.isActive) {
            { DownloadProgress(record) }
        } else {
            null
        },
        trailing = {
            when {
                record.isActive -> RowButton(PaneIcons.CloseCircle, "Cancel download", onCancel)
                record.isFinished && record.status != DownloadStatus.Completed && record.url.isNotEmpty() ->
                    RowButton(PaneIcons.Reload, "Try again", onRetry)
                dangerous -> Icon(
                    PaneIcons.Warning,
                    contentDescription = "Potentially harmful file",
                    tint = colors.warning,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp),
                )
            }
        },
    )
}

@Composable
private fun RowButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).pressDim(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = PaneTheme.colors.secondaryLabel, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DownloadProgress(record: DownloadRecord) {
    val colors = PaneTheme.colors
    val fraction = ByteSizes.fraction(record.downloadedBytes, record.totalBytes)
    val modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).height(3.dp)
    if (fraction == null || record.status == DownloadStatus.Pending) {
        LinearProgressIndicator(
            modifier = modifier,
            color = colors.accent,
            trackColor = colors.fill,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
        val animated by animateFloatAsState(fraction, Motion.smooth(), label = "downloadProgress")
        LinearProgressIndicator(
            progress = { animated },
            modifier = modifier,
            color = colors.accent,
            trackColor = colors.fill,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private fun statusText(record: DownloadRecord): String {
    val source = record.url.takeIf { it.isNotEmpty() }?.let { UrlDisplay.toolbarText(it) }?.takeIf { it.isNotEmpty() }
    return when (record.status) {
        DownloadStatus.Pending -> "Waiting…"
        DownloadStatus.Running -> ByteSizes.progress(record.downloadedBytes, record.totalBytes)
        DownloadStatus.Paused -> "Paused · " + ByteSizes.progress(record.downloadedBytes, record.totalBytes)
        DownloadStatus.Failed -> "Couldn't finish downloading"
        DownloadStatus.Cancelled -> "Cancelled"
        DownloadStatus.Completed -> {
            val size = ByteSizes.format(if (record.totalBytes >= 0) record.totalBytes else record.downloadedBytes)
            val parts = listOfNotNull(
                "Potentially harmful".takeIf { FileNames.isPotentiallyDangerous(record.fileName) },
                size.ifEmpty { null },
                source,
            )
            parts.joinToString(" · ")
        }
    }
}

private fun startOrToast(context: Context, intent: Intent, toasts: ToastState, failure: String) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toasts.show(failure, PaneIcons.Warning)
    } catch (_: SecurityException) {
        toasts.show(failure, PaneIcons.Warning)
    }
}
