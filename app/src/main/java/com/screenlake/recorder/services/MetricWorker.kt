package com.screenlake.recorder.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MetricWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val context = this@MetricWorker.applicationContext
        var notificationID = 2

//        // Check if accessibility service is enabled
        if (!isOlderThanFiveMinutes(ScreenshotService.lastCaptureDate) && ScreenshotService.isMediaProjectionValid.value == false) {
            val notiManager = NotificationHelper(context)
            notiManager.createNotificationChannel()
            NotificationHelper(context).showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
        }

        // Indicate success
        return Result.success()
    }

    /**
     * Checks if the provided timestamp is older than 5 minutes compared to the current time.
     *
     * @param timestamp The timestamp to check, in milliseconds since epoch
     * @return true if the timestamp is more than 5 minutes old, false otherwise
     */
    fun isOlderThanFiveMinutes(timestamp: Long): Boolean {
        val fiveMinutesInMillis = 2 * 60 * 60 * 1000L // 5 minutes in milliseconds
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp

        return elapsedTime > fiveMinutesInMillis
    }
}