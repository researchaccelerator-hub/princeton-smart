package com.screenlake.recorder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.utilities.TimeUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.*
import javax.inject.Inject

/**
 * BroadcastReceiver that handles system touch events, specifically when the screen turns off or the user
 * is present after unlocking the screen. It tracks and saves the session start and end events
 * in the local database using the accessibility event service.
 */

@AndroidEntryPoint
class SystemAccessibilityEventReceiver(private val mContext: Context? = null) : BroadcastReceiver() {

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    private var screenOff = false
    private var sessionStart = false

    /**
     * Called when the receiver receives an intent broadcast. Handles screen on/off events and user presence.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received, which contains the action indicating the event (e.g., screen off or user present).
     */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> handleScreenOff()
            Intent.ACTION_USER_PRESENT -> handleUserPresent()
        }
    }

    /**
     * Handles the screen off event. If a session has already started, it saves the session end.
     *
     * @param context The context in which the receiver is running.
     */
    private fun handleScreenOff() {
        TouchAccessibilityService.isScreenOn.postValue(false)
        if (sessionStart) {
            saveSessionEnd()
        }
    }

    /**
     * Handles the event when the user is present (i.e., after unlocking the screen).
     * Logs the session start and saves it in the local database.
     *
     * @param context The context in which the receiver is running.
     */
    private fun handleUserPresent() {
        val startTime = Instant.now().toEpochMilli()
        Timber.tag("AS_START").d("**** ${TimeUtility.getCurrentTimestamp()} ****")
        saveSessionStart(startTime)
    }

    /**
     * Saves the session start event in the local database. It initializes the session start time
     * and assigns a new accessibility session ID.
     *
     * @param context The context in which the receiver is running.
     * @param startTime The timestamp representing the session start time in milliseconds.
     */
    private fun saveSessionStart(startTime: Long) {
        TouchAccessibilityService.sessionStartTime = startTime
        TouchAccessibilityService.appAccessibilitySessionId = UUID.randomUUID().toString()

        sessionStart = true
        screenOff = false
        TouchAccessibilityService.isScreenOn.postValue(true)

        // Save the session start event in the database
        save("SESSION_START", startTime)
    }

    /**
     * Saves the session end event in the local database. Captures the current time as the session end time.
     *
     * @param context The context in which the receiver is running.
     */
    private fun saveSessionEnd() {
        val endTime = Instant.now().toEpochMilli()
        save("SESSION_END", endTime)
        sessionStart = false
    }

    /**
     * Launches a coroutine to save an accessibility event (either session start or session end)
     * in the local database. This method handles database operations in a background thread.
     *
     * @param context The context in which the receiver is running.
     * @param eventType The type of event being saved (e.g., "SESSION_START" or "SESSION_END").
     * @param eventTime The timestamp of the event in milliseconds.
     */
    private fun save(eventType: String, eventTime: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = AccessibilityEventEntity(
                    user = ScreenshotService.user.emailHash,
                    eventTime = eventTime,
                    eventType = eventType,
                    appIntervalId = TouchAccessibilityService.appIntervalId,
                    accessibilitySessionId = TouchAccessibilityService.appAccessibilitySessionId
                )
                generalOperationsRepository.save(event)
            } catch (ex: Exception) {
                Timber.w(ex.stackTraceToString())
            }
        }
    }
}
