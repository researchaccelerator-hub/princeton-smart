package com.screenlake.recorder.utilities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SendLogsHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Timber.plant(RingBufferTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(RingBufferTree)
        RingBufferTree.clear()
    }

    @Test
    fun `buildLogFile writes metadata header and buffered log lines`() {
        Timber.tag("TagA").i("hello from the buffer")

        val file = SendLogsHelper.buildLogFile(context)
        val contents = file.readText()

        assertTrue(contents.contains("${context.getString(R.string.log_export_label)} log export"))
        assertTrue(contents.contains("App version:"))
        assertTrue(contents.contains("OS version: Android"))
        assertTrue(contents.contains("Device:"))
        assertTrue(contents.contains("I/TagA: hello from the buffer"))
        // The prefix is the app_name brand string reduced to letters and digits, so a fork's name works too.
        val prefix = Regex.escape(context.getString(R.string.app_name).filter { it.isLetterOrDigit() }.ifEmpty { "app" })
        assertTrue(file.name.matches(Regex("""${prefix}_\d{8}_\d{6}_logs\.txt""")))
    }

    @Test
    fun `buildLogFile lives under a dedicated send_logs subdirectory, not filesDir root`() {
        val file = SendLogsHelper.buildLogFile(context)

        assertTrue(file.parentFile?.name == "send_logs")
    }

    @Test
    fun `buildLogFile replaces any previous export, leaving only the latest file on disk`() {
        Timber.tag("TagA").i("first run")
        SendLogsHelper.buildLogFile(context)

        RingBufferTree.clear()
        Timber.tag("TagB").i("second run")
        val file = SendLogsHelper.buildLogFile(context)

        val contents = file.readText()
        assertTrue(contents.contains("second run"))
        assertFalse(contents.contains("first run"))
        assertEquals(1, file.parentFile?.listFiles()?.size)
    }
}
