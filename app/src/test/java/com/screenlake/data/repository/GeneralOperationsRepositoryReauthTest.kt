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
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.authentication.CloudAuthentication
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the same-user/different-user reconciliation added alongside the credential-expiry
 * forced sign-out: pending research data (screenshots, zips, sessions) must survive the same
 * participant reconnecting, but must be wiped if a different participant logs in afterward,
 * since it may contain sensitive screen captures. Also covers restoring the invite code
 * (emailHash) and tenant/panel assignment for the same-user case, since a fresh login only ever
 * creates a bare UserEntity with just an email.
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

    private fun outgoingUser(
        email: String,
        emailHash: String = "1234",
        tenantId: String = "acme_tenant",
        tenantName: String = "Acme",
        panelId: String = "panel_9",
        panelName: String = "Acme Panel",
    ) = UserEntity(
        email = email,
        emailHash = emailHash,
        tenantId = tenantId,
        tenantName = tenantName,
        panelId = panelId,
        panelName = panelName,
    )

    private fun bareLoggedInUser(email: String) = UserEntity(email = email)

    @Test
    fun `forceSignOutForCredentialExpiry signs out and deletes the local user record`() = runTest {
        val repo = buildRepository()

        repo.forceSignOutForCredentialExpiry(outgoingUser("participant@example.com"))

        verify { cloudAuthentication.signOut(any()) }
        coVerify { userDao.deleteUser() }
    }

    @Test
    fun `same user reconnecting does not wipe pending research data`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(outgoingUser("participant@example.com"))

        repo.reconcilePendingReauthUser(bareLoggedInUser("participant@example.com"))

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
        coVerify(exactly = 0) { screenshotZipDao.nukeTable() }
        coVerify(exactly = 0) { sessionDao.nukeTable() }
    }

    @Test
    fun `same user reconnecting has their invite code and panel assignment restored`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(
            outgoingUser(
                "participant@example.com",
                emailHash = "9821",
                tenantId = "acme_tenant",
                tenantName = "Acme",
                panelId = "panel_9",
                panelName = "Acme Panel",
            )
        )

        val freshLogin = bareLoggedInUser("participant@example.com")
        repo.reconcilePendingReauthUser(freshLogin)

        assertEquals("9821", freshLogin.emailHash)
        assertEquals("acme_tenant", freshLogin.tenantId)
        assertEquals("Acme", freshLogin.tenantName)
        assertEquals("panel_9", freshLogin.panelId)
        assertEquals("Acme Panel", freshLogin.panelName)
    }

    @Test
    fun `different user logging in wipes pending research data and does not inherit the invite code`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(outgoingUser("participant-a@example.com", emailHash = "1111"))

        val newParticipant = bareLoggedInUser("participant-b@example.com")
        repo.reconcilePendingReauthUser(newParticipant)

        coVerify { screenshotDao.nukeTable() }
        coVerify { screenshotZipDao.nukeTable() }
        coVerify { sessionDao.nukeTable() }
        coVerify { appSegmentDao.nukeTable() }
        coVerify { accessibilityEventDao.deleteAccessibilityEvents() }
        coVerify { panelDao.deletePanels() }
        // A genuinely new participant must not inherit the previous participant's invite code.
        assertEquals(null, newParticipant.emailHash)
    }

    @Test
    fun `reconciling twice only wipes once (marker is cleared after reconciling)`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(outgoingUser("participant-a@example.com"))

        repo.reconcilePendingReauthUser(bareLoggedInUser("participant-b@example.com"))
        repo.reconcilePendingReauthUser(bareLoggedInUser("participant-c@example.com"))

        coVerify(exactly = 1) { screenshotDao.nukeTable() }
    }

    @Test
    fun `reconciling with no pending marker is a no-op`() = runTest {
        val repo = buildRepository()

        repo.reconcilePendingReauthUser(bareLoggedInUser("anyone@example.com"))

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
        coVerify(exactly = 0) { userDao.deleteUser() }
    }

    @Test
    fun `same user reconnecting with different email casing is not treated as a different user`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(outgoingUser("Participant@Example.com"))

        repo.reconcilePendingReauthUser(bareLoggedInUser("participant@example.com"))

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
    }

    @Test
    fun `same user reconnecting with surrounding whitespace is not treated as a different user`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(outgoingUser("participant@example.com"))

        repo.reconcilePendingReauthUser(bareLoggedInUser("  participant@example.com  "))

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
    }

    @Test
    fun `reconciling with a blank incoming email does not wipe or clear the marker`() = runTest {
        val repo = buildRepository()
        repo.forceSignOutForCredentialExpiry(outgoingUser("participant@example.com"))

        repo.reconcilePendingReauthUser(bareLoggedInUser(""))

        coVerify(exactly = 0) { screenshotDao.nukeTable() }
        // Marker should still be pending -- a real reconciliation should still catch a mismatch later.
        repo.reconcilePendingReauthUser(bareLoggedInUser("someone-else@example.com"))
        coVerify(exactly = 1) { screenshotDao.nukeTable() }
    }

    @Test
    fun `forceSignOutForCredentialExpiry resets the consecutive failure counter`() = runTest {
        val repo = buildRepository()
        repo.incrementCredentialFailureCount()
        repo.incrementCredentialFailureCount()

        repo.forceSignOutForCredentialExpiry(outgoingUser("participant@example.com"))

        assertEquals(0, repo.getCredentialFailureCount())
    }
}
