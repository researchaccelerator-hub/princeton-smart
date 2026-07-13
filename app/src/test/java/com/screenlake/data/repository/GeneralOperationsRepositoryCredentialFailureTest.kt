package com.screenlake.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.dao.AccessibilityEventDao
import com.screenlake.data.database.dao.AppSegmentDao
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.PanelDao
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.database.dao.ScreenshotZipDao
import com.screenlake.data.database.dao.SessionDao
import com.screenlake.data.database.dao.UploadDailyDao
import com.screenlake.data.database.dao.UploadHistoryDao
import com.screenlake.data.database.dao.UserDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneralOperationsRepositoryCredentialFailureTest {

    private fun buildRepository(): GeneralOperationsRepository {
        val context: Context = ApplicationProvider.getApplicationContext()
        return GeneralOperationsRepository(
            context = context,
            logEventDao = mockk(relaxed = true),
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
        )
    }

    @Test
    fun `count starts at zero`() {
        val repo = buildRepository()

        assertEquals(0, repo.getCredentialFailureCount())
    }

    @Test
    fun `increment returns and persists the new count`() {
        val repo = buildRepository()

        assertEquals(1, repo.incrementCredentialFailureCount())
        assertEquals(2, repo.incrementCredentialFailureCount())
        assertEquals(2, repo.getCredentialFailureCount())
    }

    @Test
    fun `reset sets count back to zero`() {
        val repo = buildRepository()
        repo.incrementCredentialFailureCount()
        repo.incrementCredentialFailureCount()

        repo.resetCredentialFailureCount()

        assertEquals(0, repo.getCredentialFailureCount())
    }
}
