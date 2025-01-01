package com.screenlake.recorder.behavior

import android.view.accessibility.AccessibilityNodeInfo
import com.screenlake.recorder.behavior.behaviors.handlers.EventHandler
import com.screenlake.recorder.behavior.behaviors.handlers.AllTextHandler
import com.screenlake.recorder.behavior.behaviors.handlers.SelectedEventHandler
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingManager @Inject constructor(
    generalOperationsRepository: GeneralOperationsRepository
) {
    // List of event handlers
    private var eventHandlers: MutableList<EventHandler> = ArrayList()

    // Handle the accessibility event
    fun handleAccessibilityEvent(event: AccessibilityEventEntity) {
        // Iterate through each event handler and pass the event
        for (handler in eventHandlers) {
            if (handler.canHandleEvent(event)) {
                handler.handleEvent(event)
            }
        }
    }

    // Handle the accessibility event
    fun handleAccessibilityNode(event: AccessibilityNodeInfo) {
        // Iterate through each event handler and pass the event
        for (handler in eventHandlers) {
            if (handler.canHandleEvent(event)) {
                handler.handleEvent(event)
            }
        }
    }

    // Constructor
    init {
        // Initialize eventHandlers with appropriate event handler instances
        eventHandlers.add(SelectedEventHandler(generalOperationsRepository))
        AllTextHandler(generalOperationsRepository).let { eventHandlers.add(it) }
        // Add more event handlers as needed
    }
}



// Additional event handlers and event-specific logic can be added similarly
