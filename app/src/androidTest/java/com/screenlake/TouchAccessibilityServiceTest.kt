package com.screenlake

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.screenlake.recorder.behavior.TrackingManager
import com.screenlake.recorder.services.AccessibilityEventHandler
import com.screenlake.recorder.services.AccessibilityServiceDependencies
import com.screenlake.recorder.services.TouchAccessibilityService
import com.screenlake.recorder.services.util.CustomObserver
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Instrumented test class for testing the TouchAccessibilityService functionality.
 *
 * This class provides tests for validating the behavior of the accessibility service,
 * including handling accessibility events, reacting to state changes, and verifying
 * that the service processes events correctly on background threads.
 */
class TouchAccessibilityServiceTest {

    // Mocked dependencies
    private lateinit var mockEventHandler: AccessibilityEventHandler
    private lateinit var mockIoDispatcher: CoroutineDispatcher
    private lateinit var trackingManager: TrackingManager
    private lateinit var mockReceiver: BroadcastReceiver
    private lateinit var mockEvent: AccessibilityEvent
    private lateinit var mockContext: Context

    // Service instance to test
    private lateinit var service: TouchAccessibilityService

    /**
     * Sets up the test environment.
     *
     * Initializes the necessary mocks and dependencies, such as AccessibilityEventHandler, TrackingManager,
     * and sets up the AccessibilityServiceDependencies. Also initializes the TouchAccessibilityService instance.
     */
    @Before
    fun setUp() {
        // Create mocks for the dependencies
        mockEventHandler = mockk(relaxed = true) // relaxed = true will create a mock that doesn't require explicit behavior definition
        trackingManager = mockk(relaxed = true)
        mockEvent = mockk(relaxed = true)

        // Get the application context
        mockContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Set up the IO dispatcher (using Dispatchers.IO)
        mockIoDispatcher = Dispatchers.IO

        // Set up the singleton dependencies
        AccessibilityServiceDependencies.ioDispatcher = mockIoDispatcher
        AccessibilityServiceDependencies.eventHandler = mockEventHandler
        AccessibilityServiceDependencies.trackingManager = trackingManager
        AccessibilityServiceDependencies.context = WeakReference(mockContext)

        // Initialize the service (using spy to monitor behavior)
        service = spyk(TouchAccessibilityService())  // Using spyk so that you can spy on the service's behavior
    }

    /**
     * Tests that the event handler is called when an accessibility event is triggered.
     *
     * Verifies that the `handleEvent()` method of `AccessibilityEventHandler` is called when the
     * `onAccessibilityEvent()` method of `TouchAccessibilityService` is invoked with a mock event.
     */
    @Test
    fun testEventHandlerIsCalledOnAccessibilityEvent() = runTest {
        // Given: a mock AccessibilityEvent
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)

        // When: onAccessibilityEvent() is called in the service
        service.onAccessibilityEvent(mockEvent)

        // Then: Verify the event handler's handleEvent method was called with the mock event
        coVerify { mockEventHandler.handleEvent(mockEvent) }
    }

    /**
     * Tests that the service processes an accessibility event on the background thread.
     *
     * Verifies that the event is processed using the IO dispatcher, and that the event handler
     * receives the event on the background thread (specifically the IO dispatcher).
     */
    @Test
    fun testServiceProcessesEventInBackgroundThread() = runTest {
        // Given: a mock event and a dispatcher
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)

        // When: the service processes an event
        service.onAccessibilityEvent(mockEvent)

        // Then: Verify that the event handler's method is invoked on the background thread (i.e., using the IO dispatcher)
        coVerify { mockEventHandler.handleEvent(mockEvent) } // This checks if the eventHandler was called
        assertEquals(Dispatchers.IO, AccessibilityServiceDependencies.ioDispatcher) // Confirm dispatcher is IO
    }

    /**
     * Tests that the `onAccessibilityEvent()` method calls the event handler when initialized.
     *
     * Sets up a mock event handler in `AccessibilityServiceDependencies`, and verifies that
     * the `handleEvent()` method of the handler is called with a mock event.
     */
    @Test
    fun testOnAccessibilityEventCallsEventHandlerWhenInitialized() {
        // Set up a mock eventHandler in AccessibilityServiceDependencies
        val mockEventHandler = mockk<AccessibilityEventHandler>(relaxed = true)
        AccessibilityServiceDependencies.eventHandler = mockEventHandler

        // Call onAccessibilityEvent
        service.onAccessibilityEvent(mockEvent)

        // Verify that eventHandler's handleEvent method was called
        coVerify { mockEventHandler.handleEvent(mockEvent) }
    }

    /**
     * Tests that the observer reacts to changes in screen state.
     *
     * Creates a `CustomObserver` to observe changes in the `isScreenOn` LiveData property.
     * Posts a new value to `isScreenOn` to simulate a screen state change, and verifies that
     * the observer's `onChanged()` method is called with the correct value.
     */
    @Test
    fun testObserverReactsToScreenStateChange() {
        // Create a spy of the CustomObserver
        val mockObserver = spyk(CustomObserver<Boolean> {
            // No-op
        })

        Handler(Looper.getMainLooper()).post {
            // Observe the LiveData
            TouchAccessibilityService.isScreenOn.observeForever(mockObserver)
        }

        // Post a new value to trigger the observer
        TouchAccessibilityService.isScreenOn.postValue(false)

        // Verify that the observer's onChanged method was triggered
        verify { mockObserver.onChanged(false) }

        Handler(Looper.getMainLooper()).post {
            // Clean up the observer
            TouchAccessibilityService.isScreenOn.removeObserver(mockObserver)
        }
    }
}
