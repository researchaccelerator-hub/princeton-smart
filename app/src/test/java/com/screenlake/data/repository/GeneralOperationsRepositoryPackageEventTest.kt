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
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import com.screenlake.data.database.entity.SessionEntity
import com.screenlake.data.enums.PackageEventType
import com.screenlake.recorder.constants.ResearchConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneralOperationsRepositoryPackageEventTest {

    private lateinit var userDao: UserDao
    private lateinit var restrictedAppDao: RestrictedAppDao
    private lateinit var sessionDao: SessionDao
    private lateinit var packageEventDao: PackageEventDao

    private fun buildRepository(): GeneralOperationsRepository {
        val context: Context = ApplicationProvider.getApplicationContext()
        userDao = mockk(relaxed = true)
        restrictedAppDao = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)
        packageEventDao = mockk(relaxed = true)
        return GeneralOperationsRepository(
            context = context,
            logEventDao = mockk<LogEventDao>(relaxed = true),
            accessibilityEventDao = mockk<AccessibilityEventDao>(relaxed = true),
            appSegmentDao = mockk<AppSegmentDao>(relaxed = true),
            panelDao = mockk<PanelDao>(relaxed = true),
            sessionDao = sessionDao,
            screenshotDao = mockk<ScreenshotDao>(relaxed = true),
            screenshotZipDao = mockk<ScreenshotZipDao>(relaxed = true),
            userDao = userDao,
            uploadHistoryDao = mockk<UploadHistoryDao>(relaxed = true),
            uploadDailyDao = mockk<UploadDailyDao>(relaxed = true),
            restrictedAppDao = restrictedAppDao,
            packageEventDao = packageEventDao,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(ResearchConfig)
    }

    @Test
    fun `recordPackageEvent does nothing when no user is enrolled`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns false

        repo.recordPackageEvent("com.example.app", "Example", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 0) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent skips a package on the system restricted list`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true

        // "com.android.settings" is a real, hardcoded entry in ConstantSettings.RESTRICTED_APPS.
        repo.recordPackageEvent("com.android.settings", "Settings", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 0) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent skips a package on the study's additional block list`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true
        mockkObject(ResearchConfig)
        every { ResearchConfig.ADDITIONAL_BLOCKED_APPS } returns listOf("com.study.internal")

        repo.recordPackageEvent("com.study.internal", "Internal", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 0) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent logs a package on the system list when allow-overridden`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true
        coEvery { userDao.getUser() } returns com.screenlake.data.database.entity.UserEntity(emailHash = "hash123")
        mockkObject(ResearchConfig)
        every { ResearchConfig.ALLOWED_APPS_OVERRIDE } returns listOf("com.android.settings")

        repo.recordPackageEvent("com.android.settings", "Settings", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 1) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent skips a package the participant has user-restricted`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true
        coEvery { restrictedAppDao.getRestrictedAppByPackageName("com.example.app") } returns listOf(
            RestrictedAppPersistentEntity(appName = "Example", packageName = "com.example.app", isUserRestricted = true)
        )

        repo.recordPackageEvent("com.example.app", "Example", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 0) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent skips outside an active session when session-only mode is enabled`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true
        mockkObject(ResearchConfig)
        every { ResearchConfig.LOG_PACKAGE_EVENTS_SESSION_ONLY } returns true
        every { sessionDao.getSessionAtTime(1000L) } returns null

        repo.recordPackageEvent("com.example.app", "Example", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 0) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent logs inside an active session when session-only mode is enabled`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true
        coEvery { userDao.getUser() } returns com.screenlake.data.database.entity.UserEntity(emailHash = "hash123")
        mockkObject(ResearchConfig)
        every { ResearchConfig.LOG_PACKAGE_EVENTS_SESSION_ONLY } returns true
        every { sessionDao.getSessionAtTime(1000L) } returns SessionEntity()

        repo.recordPackageEvent("com.example.app", "Example", PackageEventType.INSTALLED, 1000L, false)

        coVerify(exactly = 1) { packageEventDao.save(any()) }
    }

    @Test
    fun `recordPackageEvent saves the expected entity fields`() = runTest {
        val repo = buildRepository()
        coEvery { userDao.userExists() } returns true
        coEvery { userDao.getUser() } returns com.screenlake.data.database.entity.UserEntity(emailHash = "hash123")
        val saved = slot<com.screenlake.data.database.entity.PackageEventEntity>()
        coEvery { packageEventDao.save(capture(saved)) } returns Unit

        repo.recordPackageEvent("com.example.app", "Example App", PackageEventType.REPLACED, 1234L, true)

        assertEquals("hash123", saved.captured.user)
        assertEquals("com.example.app", saved.captured.packageName)
        assertEquals("Example App", saved.captured.appName)
        assertEquals("REPLACED", saved.captured.eventType)
        assertEquals(1234L, saved.captured.eventTime)
        assertEquals(true, saved.captured.isReplacing)
    }

    @Test
    fun `isPackageRestricted is false for an unlisted package with no overrides`() = runTest {
        val repo = buildRepository()
        coEvery { restrictedAppDao.getRestrictedAppByPackageName("com.example.unrestricted") } returns emptyList()

        assertFalse(repo.isPackageRestricted("com.example.unrestricted"))
    }
}
