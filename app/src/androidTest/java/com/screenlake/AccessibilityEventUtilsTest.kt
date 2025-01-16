package com.screenlake

import android.view.accessibility.AccessibilityEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.enums.BehavioralEvents
import com.screenlake.recorder.services.util.AccessibilityEventUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityEventUtilsTest {

    /**
     * Test to verify that the convertEventToString function correctly converts an AccessibilityEvent object
     * into a JSON string representation.
     *
     * Steps:
     * 1. Create a sample AccessibilityEvent with specific fields such as eventType, className, text, and packageName.
     * 2. Convert the AccessibilityEvent object to a JSON string using convertEventToString function.
     * 3. Verify that the returned JSON string matches the expected JSON structure.
     */
    @Test
    fun convertEventToString_shouldCorrectlyConvertAccessibilityEventToJsonString() {
        // Create a sample AccessibilityEvent
        val event = AccessibilityEvent()
        event.eventType = AccessibilityEvent.TYPE_VIEW_CLICKED
        event.className = "android.widget.Button"
        event.text.add("Click Me")
        event.packageName = "com.example.app"

        // Call the method under test
        val jsonString = AccessibilityEventUtils.convertEventToString(event)

        // Expected JSON structure (keys may vary based on your implementation)
        val expectedJson = """{"EventType":"TYPE_VIEW_CLICKED","EventTime":"0","PackageName":"com.example.app","MovementGranularity":"0","Action":"0","ContentChangeTypes":"[]","Text":"[Click Me]","ContentDescription":"null","ItemCount":"-1","CurrentItemIndex":"-1","Enabled":"false","Password":"false","Checked":"false","FullScreen":"false","Scrollable":"false","ImportantForAccessibility":"false","AccessibilityDataSensitive":"false","BeforeText":"null","FromIndex":"-1","ToIndex":"-1","ScrollX":"0","ScrollY":"0","MaxScrollX":"0","MaxScrollY":"0","ScrollDeltaX":"-1","ScrollDeltaY":"-1","AddedCount":"-1","RemovedCount":"-1","ParcelableData":"null","DisplayId":"-1 ]","recordCount":"0"}"""

        // Assert that the JSON string matches the expected value
        assertEquals(expectedJson, jsonString)
    }

    /**
     * Test to verify that the convertStringToAccessibilityEvent function correctly converts a JSON string
     * into an AccessibilityEvent object.
     *
     * Steps:
     * 1. Create a sample JSON string with fields such as eventType, className, text, and packageName.
     * 2. Call the convertStringToAccessibilityEvent function to convert the JSON string into an AccessibilityEvent object.
     * 3. Verify that the returned AccessibilityEvent object has the correct properties.
     *
     * Parameters:
     * - jsonString: The input JSON string representing the AccessibilityEvent data.
     * - user: A sample User object for testing purposes.
     * - sessionId: A sample session ID for testing purposes.
     * - intervalId: A sample interval ID for testing purposes.
     */
    @Test
    fun convertStringToAccessibilityEvent_shouldCorrectlyConvertJsonStringToAccessibilityEvent() {
        // Sample JSON string
        val jsonString = """{"EventType":"TYPE_VIEW_CLICKED","ClassName":"android.widget.Button","Text":"Click Me","PackageName":"com.example.app"}"""

        val user = UserEntity(emailHash = "userhash")
        val sessionId = "session123"
        val intervalId = "interval123"

        // Call the method under test
        val accessibilityEvent = AccessibilityEventUtils.convertStringToAccessibilityEvent(
            jsonString,
            BehavioralEvents.SESSION_OVER,
            user,
            sessionId,
            intervalId
        )

        // Verify the result
        assertEquals("android.widget.Button", accessibilityEvent?.className)
        assertEquals("Click Me", accessibilityEvent?.text)
        assertEquals("userhash", accessibilityEvent?.user)
        assertEquals(sessionId, accessibilityEvent?.accessibilitySessionId)
        assertEquals(intervalId, accessibilityEvent?.appIntervalId)
        assertEquals(BehavioralEvents.SESSION_OVER, accessibilityEvent?.behavorType)
    }
}
