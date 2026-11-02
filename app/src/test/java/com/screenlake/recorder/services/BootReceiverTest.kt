package com.screenlake.recorder.services

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Power on/off events are logged so researchers can tell a gap in the data caused by the phone
 * being off from one caused by the app.
 */
class BootReceiverTest {

    @Test
    fun `boot is logged as power on`() {
        assertEquals("POWER_ON", BootReceiver.powerStateFor(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `shutdown and quickboot power off are logged as power off`() {
        assertEquals("POWER_OFF", BootReceiver.powerStateFor(Intent.ACTION_SHUTDOWN))
        assertEquals("POWER_OFF", BootReceiver.powerStateFor("android.intent.action.QUICKBOOT_POWEROFF"))
    }

    @Test
    fun `other actions are not logged`() {
        assertNull(BootReceiver.powerStateFor(Intent.ACTION_SCREEN_ON))
        assertNull(BootReceiver.powerStateFor(null))
    }
}
