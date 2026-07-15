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
import com.screenlake.recorder.authentication.CloudAuthentication
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the same-user/different-user reconciliation added alongside the credential-expiry
 * forced sign-out: pending research data (screenshots, zips, sessions) must survive the same
 * participant reconnecting, but must be wiped if a different participant logs in afterward,
 * since it may contain sensitive screen captures.
 */
@RunWith(RobolectricTestRunner::class)
class GeneralOperationsRepositoryReauthTest {

    private lateinit var userDao: UserDao
    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var screenshotZipDao: ScreenshotZipDao
    private lateinit var sessionDao: SessionDao
    private lateinit var appSegmentDao: AppSegmentDao
    private lateinit var accessibilityEventDao: AccessibilityEventDao
    private lateinit var panelDao: PanelDao
    private lateinit var cloudAuthentication: CloudAuthentication

    private fun buildRepository(): GeneralOperationsRepository {
        val context: Context = ApplicationProvider.getApplicationContext()
        userDao = mockk(relaxed = true)
        screenshotDao = mockk(relaxed = true)
        screenshotZipDao = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)
        appSegmentDao = mockk(relaxed = true)
        accessibilityEventDao = mockk(relaxed = true)
        panelDao = mockk(relaxed = true)
        cloudAuthentication = mockk(relaxed = true)

        val repo = GeneralOperationsRepository(
            context = context,
            logEventDao = mockk<LogEventDao>(relaxed = true),
            accessibilityEventDao = accessibilityEventDao,
            appSegmentDao = appSegmentDao,
            panelDao = panelDao,
            sessionDao = sessionDao,
            screenshotDao = screenshotDao,
            screenshotZipDao = screenshotZipDao,
            userDao = userDao,
            uploadHistoryDao = mockk<UploadHistoryDao>(relaxed = true),
            uploadDailyDao = mockk<UploadDailyDao>(relaxed = true),
            restrictedAppDao = mockk<RestrictedAppDao>(relaxed = true),
        )
        repo.cloudAuthentication = cloudAuthentication
        return repo
    }

    @Test
    fun `forceSignOutForCredentialExpiry signs out and deletes the local user record`() = runTest {
        val repo = buildRepository()

        repo.forceSignOutForCredentialExpiry("participant@example.com")

        verify { cloudAuthentication.signOut(any()) }
        coVerify { userDao.deleteUser() }
    }

    @Test
    fun `same user reconnecting does not wipe pending research data`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry("participant@example.com")

        repo.reconcilePendingReauthUser("participant@example.com")

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
        coVerify(exactly = 0) { screenshotZipDao.nukeTable() }
        coVerify(exactly = 0) { sessionDao.nukeTable() }
    }

    @Test
    fun `different user logging in wipes pending research data`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry("participant-a@example.com")

        repo.reconcilePendingReauthUser("participant-b@example.com")

        coVerify { screenshotDao.nukeTable() }
        coVerify { screenshotZipDao.nukeTable() }
        coVerify { sessionDao.nukeTable() }
        coVerify { appSegmentDao.nukeTable() }
        coVerify { accessibilityEventDao.deleteAccessibilityEvents() }
        coVerify { panelDao.deletePanels() }
    }

    @Test
    fun `reconciling twice only wipes once (marker is cleared after reconciling)`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry("participant-a@example.com")

        repo.reconcilePendingReauthUser("participant-b@example.com")
        repo.reconcilePendingReauthUser("participant-c@example.com")

        coVerify(exactly = 1) { screenshotDao.nukeTable() }
    }

    @Test
    fun `reconciling with no pending marker is a no-op`() = runTest {
        val repo = buildRepository()

        repo.reconcilePendingReauthUser("anyone@example.com")

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
        coVerify(exactly = 0) { userDao.deleteUser() }
    }

    @Test
    fun `same user reconnecting with different email casing is not treated as a different user`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry("Participant@Example.com")

        repo.reconcilePendingReauthUser("participant@example.com")

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
    }

    @Test
    fun `same user reconnecting with surrounding whitespace is not treated as a different user`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry("participant@example.com")

        repo.reconcilePendingReauthUser("  participant@example.com  ")

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
    }

    @Test
    fun `reconciling with a blank incoming email does not wipe or clear the marker`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry("participant@example.com")

        repo.reconcilePendingReauthUser("")

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
        // Marker should still be pending -- a real reconciliation should still catch a mismatch later.
        repo.reconcilePendingReauthUser("someone-else@example.com")
        coVerify(exactly = 1) { screenshotDao.nukeTable() }
    }

    @Test
    fun `forceSignOutForCredentialExpiry resets the consecutive failure counter`() = runTest {
        val repo = buildRepository()
        repo.incrementCredentialFailureCount()
        repo.incrementCredentialFailureCount()

        repo.forceSignOutForCredentialExpiry("participant@example.com")

        assert(repo.getCredentialFailureCount() == 0) {
            "expected failure count to reset to 0, was ${repo.getCredentialFailureCount()}"
        }
    }
}
