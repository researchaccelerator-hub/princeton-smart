package com.screenlake.recorder.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The watchdog only reports a stopped recording when recording was expected, so a participant
 * who pressed Stop is never told their phone closed the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RecordingStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `recording is not expected before the first consent`() {
        assertFalse(RecordingState.isExpected(context))
    }

    @Test
    fun `consent makes recording expected and stop clears it`() {
        RecordingState.markStarted(context)
        assertTrue(RecordingState.isExpected(context))

        RecordingState.markStoppedIntentionally(context)
        assertFalse(RecordingState.isExpected(context))
    }

    @Test
    fun `a new recording is not silenced by the previous interruption notice`() {
        RecordingState.setLastNotifiedAt(context, System.currentTimeMillis())

        RecordingState.markStarted(context)

        assertEquals(0L, RecordingState.lastNotifiedAt(context))
    }

    @Test
    fun `sign-out clears expected recording and pending consent without a live service`() {
        RecordingState.markStarted(context)
        ScreenshotService.markMediaProjectionRequestPending(context)

        RecordingState.clearForSignOut(context)

        assertFalse(RecordingState.isExpected(context))
        assertFalse(
            context.getSharedPreferences("screenshot_service_state", Context.MODE_PRIVATE)
                .getBoolean("pending_media_projection_request", true)
        )
    }
}
