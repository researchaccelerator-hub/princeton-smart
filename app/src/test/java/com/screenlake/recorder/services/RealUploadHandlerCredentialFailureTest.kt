package com.screenlake.recorder.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.AwsService
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.upload.AwsTransientException
import com.screenlake.recorder.upload.CredentialExpiredException
import com.screenlake.recorder.upload.Util
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Response
import java.io.File

/**
 * Upload results tell the worker what to do next. Only a credential problem that a silent
 * re-login could not fix may lead to a sign-out; offline and server errors keep the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RealUploadHandlerCredentialFailureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val awsService = mockk<AwsService>()
    private val generalOperationsRepository = mockk<GeneralOperationsRepository>(relaxed = true)
    private val util = mockk<Util>(relaxed = true)

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

    private fun respond(vararg codes: Int) {
        val calls = codes.map { code ->
            mockk<Call<okhttp3.ResponseBody>> {
                every { execute() } returns if (code in 200..299) {
                    Response.success("".toResponseBody())
                } else {
                    Response.error(code, "".toResponseBody())
                }
            }
        }
        every { awsService.uploadAsset(any(), any()) } returnsMany calls
    }

    @Test
    fun `credentials that cannot be recovered are an authentication failure and keep the file`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } throws CredentialExpiredException("expired")

        val result = buildHandler().uploadFile(file, entryId = 1, user = completeUser())

        assertEquals(UploadResult.AUTHENTICATION_FAILURE, result)
        assertTrue(file.exists())
        coVerify { generalOperationsRepository.saveLog(ConstantSettings.UPLOAD_CREDENTIAL_FAILURE, any()) }
        // Counting and the sign-out decision belong to AuthRecoveryCoordinator, once per run.
        verify(exactly = 0) { generalOperationsRepository.incrementCredentialFailureCount() }
        file.delete()
    }

    @Test
    fun `offline is a temporary failure, not a credential failure`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } throws AwsTransientException("offline")

        val result = buildHandler().uploadFile(file, entryId = 1, user = completeUser())

        assertEquals(UploadResult.TRANSIENT_FAILURE, result)
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun `server errors are temporary failures`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } returns "https://s3/upload"
        respond(503)

        assertEquals(UploadResult.TRANSIENT_FAILURE, buildHandler().uploadFile(file, entryId = 1, user = completeUser()))
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun `a rejected upload refreshes credentials and retries once`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } returns "https://s3/upload"
        respond(403, 200)

        val result = buildHandler().uploadFile(file, entryId = 1, user = completeUser())

        assertEquals(UploadResult.SUCCESS, result)
        coVerify(exactly = 1) { util.refreshUploadSession(any()) }
        assertTrue(!file.exists())
    }

    @Test
    fun `a rejected upload that is rejected again after refreshing is an authentication failure`() = runTest {
        val file = tempFile()
        coEvery { util.generates3ShareUrl(any(), any(), any()) } returns "https://s3/upload"
        respond(403, 403)

        assertEquals(UploadResult.AUTHENTICATION_FAILURE, buildHandler().uploadFile(file, entryId = 1, user = completeUser()))
        assertTrue(file.exists())
        file.delete()
    }
}
