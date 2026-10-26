package com.screenlake.recorder.viewmodels

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The progress log is shown in the stats screen and is fed by upload, zip, and OCR work for as
 * long as the app runs, so it must stay bounded and keep each message intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkerProgressManagerTest {

    @Before
    fun setUp() {
        WorkerProgressManager.clear()
        idleMainLooper()
    }

    @After
    fun tearDown() {
        WorkerProgressManager.clear()
        idleMainLooper()
    }

    @Test
    fun `messages are kept one per line with their own timestamp`() {
        WorkerProgressManager.updateProgress("Uploading 1 of 3")
        WorkerProgressManager.updateProgress("Upload failed -> Unable to resolve host, retrying")
        idleMainLooper()

        val lines = WorkerProgressManager.progressUpdates.value!!.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].matches(Regex("""\[\d{2}:\d{2}:\d{2}] Uploading 1 of 3""")))
        // A comma inside a message must not split it into two entries.
        assertTrue(lines[1].endsWith("] Upload failed -> Unable to resolve host, retrying"))
    }

    @Test
    fun `only the most recent 250 messages are kept`() {
        repeat(300) { WorkerProgressManager.updateProgress("message $it") }
        idleMainLooper()

        val lines = WorkerProgressManager.progressUpdates.value!!.lines()
        assertEquals(250, lines.size)
        assertTrue(lines.first().endsWith("] message 50"))
        assertTrue(lines.last().endsWith("] message 299"))
    }

    @Test
    fun `clear empties the log`() {
        WorkerProgressManager.updateProgress("Upload has finished.")
        WorkerProgressManager.clear()
        idleMainLooper()

        assertEquals("", WorkerProgressManager.progressUpdates.value)

        WorkerProgressManager.updateProgress("Starting OCR batch 1")
        idleMainLooper()
        assertEquals(1, WorkerProgressManager.progressUpdates.value!!.lines().size)
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()
}
