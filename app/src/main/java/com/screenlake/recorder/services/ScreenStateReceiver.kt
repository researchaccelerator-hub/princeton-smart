package com.screenlake.recorder.services

import android.R
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.NotificationCompat
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.ScreenshotService.Companion.CHANNEL_ID
import com.screenlake.recorder.services.ScreenshotService.Companion.RESTART_NOTIFICATION_ID
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ScreenStateReceiver(private val callback: ScreenStateCallback) : BroadcastReceiver() {
    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    interface ScreenStateCallback {
        fun onScreenOn()
        fun onScreenOff()
        fun onScreenUnlocked()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // showProjectionStoppedNotification(context)
                if (ScreenshotService.isActionScreenOff.value == true) {
                    Log.d(TAG, "Screen OFF")
                    callback.onScreenOff()
                    ScreenshotService.isActionScreenOff.postValue(false)
                }
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON")
                callback.onScreenOn()
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "Screen UNLOCKED")
                callback.onScreenUnlocked()
                ScreenshotService.isActionScreenOff.postValue(true)
            }
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(this, filter)
        Log.d(TAG, "Screen state receiver registered")
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
            Log.d(TAG, "Screen state receiver unregistered")
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    /**
     * Launches a coroutine to save all session segments in the background.
     */
    private fun saveSessionSegmentsInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            generalOperationsRepository.saveAllSessionSegments()
        }
    }

    private fun showProjectionStoppedNotification(context: Context) {
        try {
            // Create a notification to inform the user that projection stopped
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(context, ScreenshotService.CHANNEL_ID)
                .setContentTitle("Screenshot Service Stopped")
                .setContentText("Media projection permission revoked. Tap to restart.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_menu_camera)
                .setContentIntent(NotificationHelper(context).getMainActivityPendingIntent())
                .setAutoCancel(true)
                .build()

            notificationManager.notify(RESTART_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Error showing projection stopped notification")
        }
    }
}