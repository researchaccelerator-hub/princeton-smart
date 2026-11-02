package com.screenlake.recorder.services

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A participant is signed out only after repeated credential failures, once, with a way back in,
 * and without losing data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AuthRecoveryCoordinatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = mockk<GeneralOperationsRepository>(relaxed = true)
    private val user = UserEntity(email = "participant@example.com", emailHash = "1234")
    private val threshold = ConstantSettings.CREDENTIAL_FAILURE_NOTIFICATION_THRESHOLD

    private fun notification() = shadowOf(context.getSystemService(NotificationManager::class.java))
        .getNotification(ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID)

    private fun grantNotifications(granted: Boolean) {
        val app = shadowOf(context as Application)
        if (granted) app.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        else app.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `failures below the threshold keep retrying without signing out`() = runTest {
        grantNotifications(true)
        every { repository.incrementCredentialFailureCount() } returns threshold - 1

        val began = AuthRecoveryCoordinator(context, repository).onAuthenticationFailure(user, "test")

        assertFalse(began)
        assertFalse(AuthRecoveryCoordinator.isPending(context))
        assertNull(notification())
        coVerify(exactly = 0) { repository.forceSignOutForCredentialExpiry(any()) }
    }

    @Test
    fun `reaching the threshold signs out once and shows a notification that opens login`() = runTest {
        grantNotifications(true)
        every { repository.incrementCredentialFailureCount() } returns threshold
        val coordinator = AuthRecoveryCoordinator(context, repository)

        assertTrue(coordinator.onAuthenticationFailure(user, "test"))
        coordinator.onAuthenticationFailure(user, "test")

        assertTrue(AuthRecoveryCoordinator.isPending(context))
        val shown = notification()
        assertNotNull(shown)
        val intent = shadowOf(shown!!.contentIntent).savedIntent
        assertTrue(intent.action == NotificationHelper.ACTION_OPEN_LOGIN)
        coVerify(exactly = 1) { repository.forceSignOutForCredentialExpiry(user) }
        assertFalse(AuthRecoveryCoordinator.consumeInAppMessage(context))
    }

    @Test
    fun `without notification permission the login screen shows the message once`() = runTest {
        grantNotifications(false)
        every { repository.incrementCredentialFailureCount() } returns threshold

        AuthRecoveryCoordinator(context, repository).onAuthenticationFailure(user, "test")

        assertTrue(AuthRecoveryCoordinator.consumeInAppMessage(context))
        assertFalse(AuthRecoveryCoordinator.consumeInAppMessage(context))
    }

    @Test
    fun `signing in again ends recovery`() = runTest {
        grantNotifications(true)
        every { repository.incrementCredentialFailureCount() } returns threshold
        AuthRecoveryCoordinator(context, repository).onAuthenticationFailure(user, "test")

        assertTrue(AuthRecoveryCoordinator.onLoginSucceeded(context))

        assertFalse(AuthRecoveryCoordinator.isPending(context))
        assertNull(notification())
        assertFalse(AuthRecoveryCoordinator.onLoginSucceeded(context))
    }
}
