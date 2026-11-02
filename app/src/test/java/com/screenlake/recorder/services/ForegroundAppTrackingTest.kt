package com.screenlake.recorder.services

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The foreground app decides whether a screenshot is taken, so a restricted app must stay the
 * foreground app while a keyboard, the notification shade or a system panel is drawn over it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ForegroundAppTrackingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun registerActivities() {
        val pm = shadowOf(context.packageManager)
        pm.addOrUpdateActivity(activityInfo(ComponentName(BANK, "$BANK.MainActivity")))
        pm.addOrUpdateActivity(activityInfo(ComponentName(BROWSER, "$BROWSER.BrowserActivity")))
    }

    @Test
    fun `an activity window coming to the front becomes the foreground app`() {
        TouchAccessibilityService.updateLatestPackage(context, windowEvent(BROWSER, "$BROWSER.BrowserActivity"))
        TouchAccessibilityService.updateLatestPackage(context, windowEvent(BANK, "$BANK.MainActivity"))

        assertEquals(BANK, TouchAccessibilityService.getLatestPackage(60_000))
    }

    @Test
    fun `windows that are not activities do not replace the foreground app`() {
        TouchAccessibilityService.updateLatestPackage(context, windowEvent(BANK, "$BANK.MainActivity"))

        TouchAccessibilityService.updateLatestPackage(context, windowEvent("com.android.systemui", "android.widget.FrameLayout"))
        TouchAccessibilityService.updateLatestPackage(context, windowEvent("com.google.android.inputmethod.latin", "android.inputmethodservice.SoftInputWindow"))
        TouchAccessibilityService.updateLatestPackage(context, windowEvent("android", "android.app.AlertDialog"))

        assertEquals(BANK, TouchAccessibilityService.getLatestPackage(60_000))
    }

    @Test
    fun `other event types do not replace the foreground app`() {
        TouchAccessibilityService.updateLatestPackage(context, windowEvent(BANK, "$BANK.MainActivity"))

        val click = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = BROWSER
            className = "$BROWSER.BrowserActivity"
        }
        TouchAccessibilityService.updateLatestPackage(context, click)

        assertEquals(BANK, TouchAccessibilityService.getLatestPackage(60_000))
    }

    private fun windowEvent(pkg: String, cls: String) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = pkg
            className = cls
        }

    private fun activityInfo(component: ComponentName) = android.content.pm.ActivityInfo().apply {
        packageName = component.packageName
        name = component.className
    }

    private companion object {
        const val BANK = "com.example.bank"
        const val BROWSER = "com.example.browser"
    }
}
