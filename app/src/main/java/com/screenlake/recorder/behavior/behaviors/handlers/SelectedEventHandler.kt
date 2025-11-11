package com.screenlake.recorder.behavior.behaviors.handlers

import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.enums.BehavioralEvents
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.services.util.ScreenshotData
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectedEventHandler @Inject constructor(
    private val generalOperationsRepository: GeneralOperationsRepository
) : EventHandler {

    override fun canHandleEvent(event: AccessibilityEventEntity): Boolean {
        // Check if the event involves text seen on the screen
        // Return true if the event can be handled by this handler
        // Return false otherwise
        return true
    }

    override fun handleEvent(event: AccessibilityEventEntity) {
        // Handle the text seen event
        // Perform specific actions or processing for text seen on the screen
        when(event.behavorType) {
            BehavioralEvents.CLICKED_EVENT -> selectedEvent(event)
            else -> {

            }
        }
    }

    private fun selectedEvent(accessibilityEventConverted: AccessibilityEventEntity) {
        if (accessibilityEventConverted?.text?.trim()?.isNotEmpty() == true
            && accessibilityEventConverted.text != "[]"
        ) {

            val isRestrictedApp = ScreenshotService.isRestrictedApp(accessibilityEventConverted.packageName)

            if (isRestrictedApp) return

            accessibilityEventConverted.text =
                ScreenshotData.ocrCleanUp(accessibilityEventConverted.text ?: "")
            accessibilityEventConverted.eventType = accessibilityEventConverted.behavorType?.name
            Timber.tag("Check")
                .d(accessibilityEventConverted.text + " : " + accessibilityEventConverted.contentDescription)
            save(accessibilityEventConverted)
        }
    }

    fun save(accessibilityEvent: AccessibilityEventEntity) {
        generalOperationsRepository.save(accessibilityEvent)
    }
}
