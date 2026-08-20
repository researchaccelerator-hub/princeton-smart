package com.screenlake.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.dao.AccessibilityEventDao
import com.screenlake.data.database.dao.AppSegmentDao
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.PackageEventDao
import com.screenlake.data.database.dao.PanelDao
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.database.dao.ScreenshotZipDao
import com.screenlake.data.database.dao.SessionDao
import com.screenlake.data.database.dao.UploadDailyDao
import com.screenlake.data.database.dao.UploadHistoryDao
import com.screenlake.data.database.dao.UserDao
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneralOperationsRepositoryAccessibilitySessionActiveTest {

    private fun buildRepository(): GeneralOperationsRepository {
        val context: Context = ApplicationProvider.getApplicationContext()
        return GeneralOperationsRepository(
            context = context,
            logEventDao = mockk<LogEventDao>(relaxed = true),
            accessibilityEventDao = mockk<AccessibilityEventDao>(relaxed = true),
            appSegmentDao = mockk<AppSegmentDao>(relaxed = true),
            panelDao = mockk<PanelDao>(relaxed = true),
            sessionDao = mockk<SessionDao>(relaxed = true),
            screenshotDao = mockk<ScreenshotDao>(relaxed = true),
            screenshotZipDao = mockk<ScreenshotZipDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            uploadHistoryDao = mockk<UploadHistoryDao>(relaxed = true),
            uploadDailyDao = mockk<UploadDailyDao>(relaxed = true),
            restrictedAppDao = mockk<RestrictedAppDao>(relaxed = true),
            packageEventDao = mockk<PackageEventDao>(relaxed = true),
        )
    }

    @Test
    fun `isAccessibilitySessionActive defaults to false when never set`() {
        val repo = buildRepository()

        assertFalse(repo.isAccessibilitySessionActive())
    }

    @Test
    fun `markAccessibilitySessionActive makes isAccessibilitySessionActive return true`() {
        val repo = buildRepository()

        repo.markAccessibilitySessionActive()

        assertTrue(repo.isAccessibilitySessionActive())
    }

    @Test
    fun `markAccessibilitySessionInactive makes isAccessibilitySessionActive return false`() {
        val repo = buildRepository()
        repo.markAccessibilitySessionActive()

        repo.markAccessibilitySessionInactive()

        assertFalse(repo.isAccessibilitySessionActive())
    }

    @Test
    fun `state persists across repository instances backed by the same context`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repo1 = GeneralOperationsRepository(
            context = context,
            logEventDao = mockk<LogEventDao>(relaxed = true),
            accessibilityEventDao = mockk<AccessibilityEventDao>(relaxed = true),
            appSegmentDao = mockk<AppSegmentDao>(relaxed = true),
            panelDao = mockk<PanelDao>(relaxed = true),
            sessionDao = mockk<SessionDao>(relaxed = true),
            screenshotDao = mockk<ScreenshotDao>(relaxed = true),
            screenshotZipDao = mockk<ScreenshotZipDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            uploadHistoryDao = mockk<UploadHistoryDao>(relaxed = true),
            uploadDailyDao = mockk<UploadDailyDao>(relaxed = true),
            restrictedAppDao = mockk<RestrictedAppDao>(relaxed = true),
            packageEventDao = mockk<PackageEventDao>(relaxed = true),
        )
        repo1.markAccessibilitySessionActive()

        val repo2 = GeneralOperationsRepository(
            context = context,
            logEventDao = mockk<LogEventDao>(relaxed = true),
            accessibilityEventDao = mockk<AccessibilityEventDao>(relaxed = true),
            appSegmentDao = mockk<AppSegmentDao>(relaxed = true),
            panelDao = mockk<PanelDao>(relaxed = true),
            sessionDao = mockk<SessionDao>(relaxed = true),
            screenshotDao = mockk<ScreenshotDao>(relaxed = true),
            screenshotZipDao = mockk<ScreenshotZipDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            uploadHistoryDao = mockk<UploadHistoryDao>(relaxed = true),
            uploadDailyDao = mockk<UploadDailyDao>(relaxed = true),
            restrictedAppDao = mockk<RestrictedAppDao>(relaxed = true),
            packageEventDao = mockk<PackageEventDao>(relaxed = true),
        )

        // Confirms the flag lives in SharedPreferences (survives a fresh repository
        // instance, e.g. a different process/object graph reading the same disk-backed
        // prefs file), not in an in-memory field on the repository object itself.
        assertTrue(repo2.isAccessibilitySessionActive())
    }
}
