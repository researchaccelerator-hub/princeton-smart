package com.screenlake.recorder.viewmodels

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The progress log is shown in the stats screen and is fed by upload, zip, and OCR work for as
 * long as the app runs, so it must stay bounded and keep each message intact.
 *
 * LiveData tasks run immediately here (what `InstantTaskExecutorRule` does), so `postValue` is
 * visible to the assertions without depending on a main looper.
 */
class WorkerProgressManagerTest {

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        WorkerProgressManager.clear()
    }

    @After
    fun tearDown() {
        WorkerProgressManager.clear()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun `messages are kept one per line with their own timestamp`() {
        WorkerProgressManager.updateProgress("Uploading 1 of 3")
        WorkerProgressManager.updateProgress("Upload failed -> Unable to resolve host, retrying")

        val lines = WorkerProgressManager.progressUpdates.value!!.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].matches(Regex("""\[\d{2}:\d{2}:\d{2}] Uploading 1 of 3""")))
        // A comma inside a message must not split it into two entries.
        assertTrue(lines[1].endsWith("] Upload failed -> Unable to resolve host, retrying"))
    }

    @Test
    fun `only the most recent 250 messages are kept`() {
        repeat(300) { WorkerProgressManager.updateProgress("message $it") }

        val lines = WorkerProgressManager.progressUpdates.value!!.lines()
        assertEquals(250, lines.size)
        assertTrue(lines.first().endsWith("] message 50"))
        assertTrue(lines.last().endsWith("] message 299"))
    }

    @Test
    fun `clear empties the log`() {
        WorkerProgressManager.updateProgress("Upload has finished.")
        WorkerProgressManager.clear()

        assertEquals("", WorkerProgressManager.progressUpdates.value)

        WorkerProgressManager.updateProgress("Starting OCR batch 1")
        assertEquals(1, WorkerProgressManager.progressUpdates.value!!.lines().size)
    }
}
