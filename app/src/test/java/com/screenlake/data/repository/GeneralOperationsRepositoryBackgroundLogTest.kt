package com.screenlake.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.UserDao
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.UserEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Boot and shutdown logs are written from a fresh process where no screen has set
 * [AmplifyRepository.email] yet. They must still carry the participant from the stored user,
 * like every other log row.
 */
@RunWith(RobolectricTestRunner::class)
class GeneralOperationsRepositoryBackgroundLogTest {

    private val logEventDao = mockk<LogEventDao>(relaxed = true)
    private val userDao = mockk<UserDao>(relaxed = true)
    private val amplifyRepository = mockk<AmplifyRepository>(relaxed = true)

    private fun buildRepository(): GeneralOperationsRepository {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repo = GeneralOperationsRepository(
            context = context,
            logEventDao = logEventDao,
            accessibilityEventDao = mockk(relaxed = true),
            appSegmentDao = mockk(relaxed = true),
            panelDao = mockk(relaxed = true),
            sessionDao = mockk(relaxed = true),
            screenshotDao = mockk(relaxed = true),
            screenshotZipDao = mockk(relaxed = true),
            userDao = userDao,
            uploadHistoryDao = mockk(relaxed = true),
            uploadDailyDao = mockk(relaxed = true),
            restrictedAppDao = mockk(relaxed = true),
            packageEventDao = mockk(relaxed = true),
        )
        repo.amplifyRepository = amplifyRepository
        return repo
    }

    private fun savedLog(): LogEventEntity {
        val log = slot<LogEventEntity>()
        coVerify { logEventDao.saveException(capture(log)) }
        return log.captured
    }

    @Test
    fun `uses the stored user when no screen has set the email`() = runTest {
        every { amplifyRepository.email } returns ""
        coEvery { userDao.getUser() } returns UserEntity(email = "participant@example.com")

        buildRepository().saveLogForStoredUser("PHONE_POWER_STATE", "POWER_ON")

        val log = savedLog()
        assertEquals("PHONE_POWER_STATE", log.event)
        assertEquals("POWER_ON", log.msg)
        assertEquals("participant@example.com", log.user)
    }

    @Test
    fun `uses the signed-in email when it is already set`() = runTest {
        every { amplifyRepository.email } returns "signed-in@example.com"
        coEvery { userDao.getUser() } returns UserEntity(email = "stored@example.com")

        buildRepository().saveLogForStoredUser("PHONE_POWER_STATE", "POWER_OFF")

        assertEquals("signed-in@example.com", savedLog().user)
    }

    @Test
    fun `still logs the event when no user is stored`() = runTest {
        every { amplifyRepository.email } returns ""
        coEvery { userDao.getUser() } throws NullPointerException("no user row")

        buildRepository().saveLogForStoredUser("PHONE_POWER_STATE", "POWER_ON")

        assertEquals("", savedLog().user)
    }
}
