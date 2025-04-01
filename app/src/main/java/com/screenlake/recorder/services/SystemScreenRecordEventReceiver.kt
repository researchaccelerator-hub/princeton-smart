package com.screenlake.recorder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenlake.data.database.entity.SessionTempEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.utilities.BaseUtility
import com.screenlake.recorder.utilities.TimeUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SystemScreenRecordEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository
    private var screenOff = false
    private var notificationID = 1

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("()())()()()()()(")
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> handleScreenOff(context)
            Intent.ACTION_SCREEN_ON -> handleScreenOn()
            Intent.ACTION_USER_PRESENT -> handleUserPresent()
            Intent.ACTION_BATTERY_LOW -> ScreenshotService.isBatteryLow.postValue(true)
            Intent.ACTION_BATTERY_OKAY -> ScreenshotService.isBatteryLow.postValue(false)
            Intent.ACTION_POWER_CONNECTED -> ScreenshotService.isPowerConnected.postValue(true)
            Intent.ACTION_POWER_DISCONNECTED -> ScreenshotService.isPowerConnected.postValue(false)
        }
    }

    /**
     * Handles the screen off event, ensuring the session segments are saved only once
     * when the screen turns off.
     */
    private fun handleScreenOff(context: Context) {
        if (!screenOff) {
            ScreenshotService.isScreenOn.postValue(false)

            if (BaseUtility.isAndroidFifteen()) {
                ScreenshotService.isProjectionValid.postValue(false)
                NotificationHelper(context).showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
            }

            screenOff = true
        }
    }

    /**
     * Handles the screen on event, resetting screen off state (if necessary).
     */
    private fun handleScreenOn() {
        // No action currently defined for screen on, but method placeholder is kept for clarity.
    }

    /**
     * Handles the event when the user is present after unlocking the screen.
     * Resets the screen off flag and initializes a new session.
     */
    private fun handleUserPresent() {
        screenOff = false
        val currentTime = TimeUtility.getCurrentTimestamp()
        Timber.tag("SR_START").d("**** $currentTime ****")
        val sessionStartTime = currentTime.toInstant()
        generalOperationsRepository.currentSession = SessionTempEntity(sessionStart = sessionStartTime)
        ScreenshotService.isScreenOn.postValue(true)
    }
}
