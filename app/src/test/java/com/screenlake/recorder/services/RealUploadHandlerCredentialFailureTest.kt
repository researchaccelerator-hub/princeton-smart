package com.screenlake.recorder.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.AwsService
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.upload.CredentialExpiredException
import com.screenlake.recorder.upload.Util
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RealUploadHandlerCredentialFailureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val awsService = mockk<AwsService>()
    private val generalOperationsRepository = mockk<GeneralOperationsRepository>(relaxed = true)
    private val util = mockk<Util>()

    private fun buildHandler() = RealUploadHandler(context, awsService, generalOperationsRepository, util)

    private fun tempFile(): File {
        val f = File.createTempFile("credtest", ".zip", context.cacheDir)
        f.writeText("dummy")
        return f
    }

    // A complete user (invite code + tenant/panel assignment) so the panelist-info guard doesn't
    // short-circuit these tests before they can exercise the credential-failure path itself.
    private fun completeUser() = UserEntity(
        email = "participant@example.com",
        emailHash = "1234",
        tenantId = "acme_tenant",
        tenantName = "Acme",
        panelId = "panel_9",
        panelName = "Acme Panel",
    )

    @Test
    fun `credential failure increments counter, logs, and rethrows`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } throws CredentialExpiredException("expired")
        every { generalOperationsRepository.incrementCredentialFailureCount() } returns 1

        val handler = buildHandler()

        assertThrows(CredentialExpiredException::class.java) {
            kotlinx.coroutines.runBlocking { handler.uploadFile(file, entryId = 1, user = completeUser()) }
        }

        io.mockk.verify { generalOperationsRepository.incrementCredentialFailureCount() }
        io.mockk.coVerify { generalOperationsRepository.saveLog(ConstantSettings.UPLOAD_CREDENTIAL_FAILURE, any()) }
        file.delete()
    }

    @Test
    fun `notification is not shown before the failure threshold`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } throws CredentialExpiredException("expired")
        every { generalOperationsRepository.incrementCredentialFailureCount() } returns 5

        val handler = buildHandler()
        try {
            kotlinx.coroutines.runBlocking { handler.uploadFile(file, entryId = 1, user = completeUser()) }
        } catch (_: CredentialExpiredException) {}

        val notificationManager = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
        assertTrue(notificationManager.getNotification(ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID) == null)
        file.delete()
    }

    @Test
    fun `notification is shown once the failure threshold is reached`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } throws CredentialExpiredException("expired")
        every { generalOperationsRepository.incrementCredentialFailureCount() } returns ConstantSettings.CREDENTIAL_FAILURE_NOTIFICATION_THRESHOLD

        val handler = buildHandler()
        try {
            kotlinx.coroutines.runBlocking { handler.uploadFile(file, entryId = 1, user = completeUser()) }
        } catch (_: CredentialExpiredException) {}

        val notificationManager = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
        assertTrue(notificationManager.getNotification(ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID) != null)
        file.delete()
    }
}
