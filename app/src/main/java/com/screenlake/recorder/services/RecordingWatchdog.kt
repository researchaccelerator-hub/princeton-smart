package com.screenlake.recorder.services

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenlake.R
import com.screenlake.data.repository.GeneralOperationsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/*
 * Recording-interruption watchdog.
 *
 * When the system kills the recorder process (low-memory killer, ANR kill, crash) no app code runs
 * and the foreground-service notification disappears with the dead service. So the participant is
 * told *afterwards*, by a fresh process:
 *  - RecordingState persists "recording should be running" to disk.
 *  - RecordingWatchdogWorker runs every 15 min (WorkManager starts a new process when needed) and
 *    once on every process start. If recording is expected but no ScreenshotService exists in this
 *    process, the old process died: mark consent pending and post a normal notification, which
 *    (unlike the FGS notification) survives later process deaths.
 *  - On Android 11+ the OS-reported reason for each dead process is saved as a PROCESS_EXIT log.
 *
 * Integration (4 lines in ScreenshotService, 1 in BaseApplication):
 *   ScreenshotService.onCreate           -> RecordingState.serviceAliveInProcess = true
 *   ScreenshotService.setupMediaProjection -> RecordingState.markStarted(this)
 *   ACTION_STOP_SERVICE                  -> RecordingState.markStoppedIntentionally(...)
 *   ScreenshotService.onDestroy          -> RecordingState.onServiceDestroyed(this)
 *   BaseApplication.onCreate             -> RecordingWatchdogWorker.onProcessStart(this)
 *
 * Limitation: Force stop cancels this app's notifications AND its scheduled work, so nothing can
 * run on the device until the participant opens the app; only a server-side check covers that.
 */
object RecordingState {
    private const val PREFS = "recording_watchdog_state"
    private const val KEY_EXPECTED = "recording_expected"
    private const val KEY_EXIT_CHECKED = "exit_info_checked_ms"
    private const val KEY_LAST_NOTIFIED = "interruption_last_notified_ms"

    /** True while a ScreenshotService instance exists in THIS process; false in any new process. */
    @Volatile
    @JvmStatic
    var serviceAliveInProcess = false

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Fresh screen-sharing consent received: recording is now expected to keep running. */
    fun markStarted(c: Context) {
        prefs(c).edit().putBoolean(KEY_EXPECTED, true).commit() // sync: survive an immediate kill
    }

    /** Participant pressed Stop (or signed out): not an interruption. */
    fun markStoppedIntentionally(c: Context) {
        prefs(c).edit().putBoolean(KEY_EXPECTED, false).commit()
    }

    /** Service destroyed while the process lives on; if it wasn't a Stop, check right away. */
    fun onServiceDestroyed(c: Context) {
        serviceAliveInProcess = false
        if (isExpected(c)) RecordingWatchdogWorker.checkNow(c)
    }

    fun isExpected(c: Context) = prefs(c).getBoolean(KEY_EXPECTED, false)

    internal fun exitInfoCheckedAt(c: Context) = prefs(c).getLong(KEY_EXIT_CHECKED, 0L)
    internal fun setExitInfoCheckedAt(c: Context, ms: Long) =
        prefs(c).edit().putLong(KEY_EXIT_CHECKED, ms).apply()

    internal fun lastNotifiedAt(c: Context) = prefs(c).getLong(KEY_LAST_NOTIFIED, 0L)
    internal fun setLastNotifiedAt(c: Context, ms: Long) =
        prefs(c).edit().putLong(KEY_LAST_NOTIFIED, ms).apply()
}

object RecordingInterruptionNotifier {
    /** Same id as ScreenshotService's "Study Recording Paused" notification, so the existing
     *  cancelProjectionStoppedNotification() clears it once capture restarts. */
    private const val NOTIFICATION_ID = ScreenshotService.RESTART_NOTIFICATION_ID
    private const val CHANNEL_ID = ScreenshotService.INTERRUPTION_CHANNEL_ID
    private val REPOST_INTERVAL_MS = TimeUnit.HOURS.toMillis(2)

    /** @param force post even if one was shown recently (freshly detected interruption). */
    fun show(c: Context, force: Boolean): Boolean {
        val ctx = c.applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        ensureChannel(nm)

        if (nm.activeNotifications.any { it.id == NOTIFICATION_ID }) return false // already visible
        val now = System.currentTimeMillis()
        if (!force && now - RecordingState.lastNotifiedAt(ctx) < REPOST_INTERVAL_MS) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false

        val text = "Your phone closed the study app, so screen recording has stopped. " +
            "Tap to turn screen recording back on."
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_just_square_small)
            .setContentTitle("Study Recording Stopped")
            .setContentText("Tap to resume data collection")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(NotificationHelper(ctx).getMainActivityPendingIntent())
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
            RecordingState.setLastNotifiedAt(ctx, now)
            true
        } catch (se: SecurityException) { // POST_NOTIFICATIONS not granted
            Timber.w(se, "Could not post recording-interrupted notification")
            false
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // Identical to the channel ScreenshotService.onCreate() creates.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen sharing interrupted", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Alerts when screen sharing needs to be resumed"
                    enableVibration(true)
                }
        )
    }
}

@HiltWorker
class RecordingWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generalOperationsRepository: GeneralOperationsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val exits = collectNewExits(ctx)
        exits.forEach { generalOperationsRepository.saveLogForStoredUser("PROCESS_EXIT", it) }

        if (!RecordingState.isExpected(ctx) || RecordingState.serviceAliveInProcess) {
            return Result.success()
        }

        // Recording should be running but no service exists in this process -> it was killed.
        // Opening the app (or tapping the notification) now goes straight to the consent prompt.
        ScreenshotService.markMediaProjectionRequestPending(ctx)

        val shown = RecordingInterruptionNotifier.show(ctx, force = exits.isNotEmpty())
        if (shown || exits.isNotEmpty()) {
            generalOperationsRepository.saveLogForStoredUser(
                "RECORDING_INTERRUPTED",
                "notificationShown=$shown;newExits=${exits.size}"
            )
        }
        return Result.success()
    }

    /** Android 11+: OS-recorded reasons for previous process deaths since the last check. */
    private fun collectNewExits(ctx: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val since = RecordingState.exitInfoCheckedAt(ctx)
            val infos = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 16)
            infos.maxOfOrNull { it.timestamp }?.let { RecordingState.setExitInfoCheckedAt(ctx, maxOf(it, since)) }
            infos.filter { it.timestamp > since && it.reason != ApplicationExitInfo.REASON_EXIT_SELF }
                .sortedBy { it.timestamp }
                .map {
                    "reason=${reasonLabel(it.reason)};timestamp=${it.timestamp};importance=${it.importance};" +
                        "pssKb=${it.pss};rssKb=${it.rss};status=${it.status};description=${it.description}"
                }
        } catch (t: Throwable) {
            Timber.w(t, "Could not read process exit reasons")
            emptyList()
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($reason)"
    }

    companion object {
        private const val PERIODIC_WORK = "WORK_MANAGER_RECORDING_WATCHDOG"
        private const val IMMEDIATE_WORK = "WORK_MANAGER_RECORDING_WATCHDOG_NOW"

        /** Called from BaseApplication.onCreate: keep the 15-min watchdog alive and check now. */
        fun onProcessStart(c: Context) {
            try {
                WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                    PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<RecordingWatchdogWorker>(15, TimeUnit.MINUTES).build()
                )
                checkNow(c)
            } catch (e: Exception) {
                Timber.e(e, "Could not schedule recording watchdog")
            }
        }

        fun checkNow(c: Context) {
            WorkManager.getInstance(c).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RecordingWatchdogWorker>().build()
            )
        }
    }
}
