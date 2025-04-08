package com.screenlake.recorder.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenlake.recorder.services.ScreenshotService.Companion.lastReEnableNotificationSent

class MetricWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val context = this@MetricWorker.applicationContext
        var notificationID = 2

        if (isOlderThanThirtyMinutes(ScreenshotService.lastIsAliveTime)) {
            val notiManager = NotificationHelper(context)
            notiManager.createNotificationChannel()

            lastReEnableNotificationSent = System.currentTimeMillis()

            NotificationHelper(context).showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
        }

        return Result.success()
    }

    /**
     * Checks if the provided timestamp is older than 30 minutes compared to the current time.
     *
     * @param timestamp The timestamp to check, in milliseconds since epoch
     * @return true if the timestamp is more than 30 minutes old, false otherwise
     */
    fun isOlderThanThirtyMinutes(timestamp: Long): Boolean {
        val fiveMinutesInMillis = 30 * 60 * 1000L // 5 minutes in milliseconds
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp

        return elapsedTime > fiveMinutesInMillis
    }
}