package app.pane.browser.downloads

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.pane.browser.MainActivity
import app.pane.browser.PaneApp
import app.pane.browser.data.DownloadRecord
import app.pane.core.library.ByteSizes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * keeps Pane in the foreground while it downloads, so the system doesn't end the process once the
 * app is in the background. it watches the download list, shows the progress (with a way to cancel)
 * and stops itself when nothing is left to fetch.
 */
class DownloadService : Service() {
    private var watch: Job? = null

    // with the screen off the CPU would otherwise sleep between packets and stall the transfer
    private val wake by lazy { getSystemService<PowerManager>()!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pane:downloads") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as PaneApp).container
        if (intent?.action == ACTION_CANCEL) container.downloads.cancelAll()
        // started with startForegroundService, so it has to be in the foreground now, with whatever is known
        show(DownloadNotifications.ongoing(this, emptyList()))
        if (!wake.isHeld) wake.acquire(WAKE_MS)
        watch?.cancel()
        watch = container.scope.launch {
            container.downloadsRepository.observeAll().collect { all ->
                val active = all.filter { it.isActive }
                if (active.isEmpty()) stopSelf(startId) else show(DownloadNotifications.ongoing(this@DownloadService, active))
            }
        }
        return START_NOT_STICKY
    }

    // Android 15 ends a data sync service after six hours; the downloads go on for as long as the process does.
    override fun onTimeout(startId: Int, fgsType: Int) = stopSelf()

    override fun onDestroy() {
        watch?.cancel()
        if (wake.isHeld) wake.release()
        super.onDestroy()
    }

    private fun show(notification: Notification) =
        ServiceCompat.startForeground(this, ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    companion object {
        const val ACTION_CANCEL = "app.pane.browser.CANCEL_DOWNLOADS"
        private const val ONGOING_ID = 1

        /** the most a data sync service may run anyway (Android 15). */
        private const val WAKE_MS = 6 * 60 * 60 * 1000L

        /** without the foreground (the system refuses when Pane is in the background) the downloads still run, just unprotected. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}

/** the notifications downloads post: progress while they run, and one per file that finishes or fails. */
internal object DownloadNotifications {
    private const val CHANNEL_ID = "downloads"
    private const val TAG = "download"
    private const val PROGRESS_MAX = 1000

    private fun channel(context: Context) = NotificationManagerCompat.from(context).createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW).setName("Downloads").build(),
    )

    /** opens Pane on its Downloads list. */
    fun list(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_DOWNLOADS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ongoing(context: Context, active: List<DownloadRecord>): Notification {
        channel(context)
        val done = active.sumOf { it.downloadedBytes }
        // one file of unknown size makes the whole bar unknown
        val total = if (active.all { it.totalBytes > 0 }) active.sumOf { it.totalBytes } else -1L
        val fraction = ByteSizes.fraction(done, total)
        val cancel = PendingIntent.getService(
            context, 0,
            Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                when (active.size) {
                    0 -> "Downloads"
                    1 -> active[0].fileName
                    else -> "${active.size} downloads"
                },
            )
            .setContentText(if (active.isEmpty()) null else ByteSizes.progress(done, total))
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .setContentIntent(list(context))
            .addAction(0, "Cancel", cancel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** [tap] is what a touch does: open the file, or the list when the file shouldn't open straight away. */
    // areNotificationsEnabled covers the Android 13 permission; lint can't follow it
    @SuppressLint("MissingPermission")
    fun done(context: Context, id: Long, title: String, text: String, tap: PendingIntent) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(TAG, id.toInt(), notification) }
    }
}
