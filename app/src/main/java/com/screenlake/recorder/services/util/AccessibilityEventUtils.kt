package com.screenlake.recorder.services.util

import android.view.accessibility.AccessibilityEvent
import com.google.gson.Gson
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.enums.BehavioralEvents
import com.screenlake.recorder.utilities.TimeUtility
import timber.log.Timber

/**
 * Utility object for AccessibilityEvent-related functions.
 */
object AccessibilityEventUtils {
    fun convertEventToString(event: AccessibilityEvent): String {
        // Convert event to string and clean up formatting
        val eventString = event.toString().replace(';', ',')
        val keyValuePairs = eventString.split(",").toTypedArray()
        val jsonStringBuilder = StringBuilder("{")

        // Build a proper JSON string from key-value pairs
        for (pair in keyValuePairs) {
            val parts = pair.split(":").map { it.trim() }
            if (parts.size == 2) {
                val key = parts[0]
                val value = parts[1]
                jsonStringBuilder.append("\"").append(key).append("\":\"").append(value).append("\",")
            }
        }

        // Remove the trailing comma and close the JSON object
        if (jsonStringBuilder.isNotEmpty()) {
            jsonStringBuilder.deleteCharAt(jsonStringBuilder.length - 1)
        }
        return jsonStringBuilder.append("}").toString()
    }

    fun convertStringToAccessibilityEvent(
        eventString: String,
        eventType: BehavioralEvents,
        user: UserEntity?,
        sessionId: String,
        intervalId: String
    ): AccessibilityEventEntity? {
        val jsonString = eventString.replace(';', ',')
        val epoch = TimeUtility.getCurrentTimestampEpochMillis()
        return try {
            Gson().fromJson(jsonString, AccessibilityEventEntity::class.java)?.apply {
                this.behavorType = eventType
                this.eventTime = epoch
                this.user = user?.emailHash
                this.accessibilitySessionId = sessionId
                this.appIntervalId = intervalId
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert string to AccessibilityEvent")
            null
        }
    }
}