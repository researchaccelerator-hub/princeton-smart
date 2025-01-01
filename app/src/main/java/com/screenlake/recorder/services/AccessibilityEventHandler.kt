// File: AccessibilityEventHandler.kt
package com.screenlake.recorder.services

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.screenlake.recorder.behavior.TrackingManager
import javax.inject.Inject
import javax.inject.Singleton
import com.screenlake.data.database.entity.AccessibilityEventEntity as CustomAccessibilityEvent

@Singleton
class AccessibilityEventHandler @Inject constructor(
    private val trackingManager: TrackingManager
) {

    @RequiresApi(Build.VERSION_CODES.P)
    fun handleEvent(event: AccessibilityEvent) {
        val customEvent = convertToCustomAccessibilityEvent(event)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleViewClickedEvent(customEvent)
            AccessibilityEvent.TYPE_VIEW_SELECTED -> handleViewSelectedEvent(customEvent)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleViewScrolledEvent(customEvent)
            // Add other event types as needed
        }
    }

    private fun convertToCustomAccessibilityEvent(event: AccessibilityEvent): CustomAccessibilityEvent {
        return CustomAccessibilityEvent(
            className = event.className?.toString(),
            text = event.text.joinToString(),
            eventTime = System.currentTimeMillis(),
            eventType = event.eventType.toString(),
            // add other properties needed for your custom event
        )
    }

    private fun handleViewClickedEvent(event: CustomAccessibilityEvent) {
        trackingManager.handleAccessibilityEvent(event)
    }

    private fun handleViewSelectedEvent(event: CustomAccessibilityEvent) {
        trackingManager.handleAccessibilityEvent(event)
    }

    private fun handleViewScrolledEvent(event: CustomAccessibilityEvent) {
        trackingManager.handleAccessibilityEvent(event)
    }
}
