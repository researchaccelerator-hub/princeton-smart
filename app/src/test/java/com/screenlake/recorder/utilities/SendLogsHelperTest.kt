package com.screenlake.recorder.utilities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
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

        assertTrue(contents.contains("Princeton SMART log export"))
        assertTrue(contents.contains("App version:"))
        assertTrue(contents.contains("OS version: Android"))
        assertTrue(contents.contains("Device:"))
        assertTrue(contents.contains("I/TagA: hello from the buffer"))
    }

    @Test
    fun `buildLogFile lives under a dedicated send_logs subdirectory, not filesDir root`() {
        val file = SendLogsHelper.buildLogFile(context)

        assertTrue(file.parentFile?.name == "send_logs")
    }

    @Test
    fun `buildLogFile overwrites any previous export`() {
        Timber.tag("TagA").i("first run")
        SendLogsHelper.buildLogFile(context)

        RingBufferTree.clear()
        Timber.tag("TagB").i("second run")
        val file = SendLogsHelper.buildLogFile(context)

        val contents = file.readText()
        assertTrue(contents.contains("second run"))
        assertFalse(contents.contains("first run"))
    }
}
