package com.screenlake.recorder.constants

import com.screenlake.recorder.services.ScreenshotService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The session CSV records the active preset's fps, and the README tells researchers to use it to
 * confirm the deployed capture rate. Capture and the notification must use that same rate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenshotIntervalTest {

    @Test
    fun `each preset's interval matches its fps`() {
        DataCollectionPreset.values().forEach { preset ->
            assertEquals(preset.name, Math.round(1000 / preset.fps), preset.intervalMs)
        }
    }

    @Test
    fun `capture uses the active preset's interval`() {
        assertEquals(ResearchConfig.ACTIVE_PRESET.intervalMs, ScreenshotService.SCREENSHOT_INTERVAL_MS)
    }
}
