package com.screenlake.recorder.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.AwsService
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.upload.Util
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A fresh login only ever creates a bare UserEntity with just an email -- the invite code
 * (emailHash) and tenant/panel assignment are either restored (same participant reconnecting,
 * see GeneralOperationsRepository.reconcilePendingReauthUser) or missing entirely (a genuinely
 * new participant who hasn't entered their invite code yet). Uploading before that information
 * is present would write to a placeholder or literally-"null" S3 path, so uploadFile() must
 * refuse to even attempt it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RealUploadHandlerPanelistGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val awsService = mockk<AwsService>()
    private val generalOperationsRepository = mockk<GeneralOperationsRepository>(relaxed = true)
    private val util = mockk<Util>()

    private fun buildHandler() = RealUploadHandler(context, awsService, generalOperationsRepository, util)

    private fun tempFile(): File {
        val f = File.createTempFile("panelisttest", ".zip", context.cacheDir)
        f.writeText("dummy")
        return f
    }

    private fun completeUser() = UserEntity(
        email = "participant@example.com",
        emailHash = "1234",
        tenantId = "acme_tenant",
        tenantName = "Acme",
        panelId = "panel_9",
        panelName = "Acme Panel",
    )

    @Test
    fun `upload is skipped when user is null`() = runTest {
        val file = tempFile()
        val handler = buildHandler()

        runBlocking { handler.uploadFile(file, entryId = 1, user = null) }

        coVerify(exactly = 0) { util.generates3ShareUrl(any(), any(), any()) }
        file.delete()
    }

    @Test
    fun `upload is skipped when emailHash (invite code) is missing`() = runTest {
        val file = tempFile()
        val user = completeUser().apply { emailHash = null }
        val handler = buildHandler()

        runBlocking { handler.uploadFile(file, entryId = 1, user = user) }

        coVerify(exactly = 0) { util.generates3ShareUrl(any(), any(), any()) }
        coVerify { generalOperationsRepository.saveLog("UPLOAD_MISSING_PANELIST_INFO", any()) }
        file.delete()
    }

    @Test
    fun `upload is skipped when tenantId is missing`() = runTest {
        val file = tempFile()
        val user = completeUser().apply { tenantId = null }
        val handler = buildHandler()

        runBlocking { handler.uploadFile(file, entryId = 1, user = user) }

        coVerify(exactly = 0) { util.generates3ShareUrl(any(), any(), any()) }
        file.delete()
    }

    @Test
    fun `upload is skipped when panelId is missing`() = runTest {
        val file = tempFile()
        val user = completeUser().apply { panelId = null }
        val handler = buildHandler()

        runBlocking { handler.uploadFile(file, entryId = 1, user = user) }

        coVerify(exactly = 0) { util.generates3ShareUrl(any(), any(), any()) }
        file.delete()
    }

    @Test
    fun `upload proceeds when the user has a complete invite code and panel assignment`() = runTest {
        val file = tempFile()
        val user = completeUser()
        val handler = buildHandler()
        io.mockk.coEvery { util.generates3ShareUrl(any(), any(), any()) } returns ""

        runBlocking { handler.uploadFile(file, entryId = 1, user = user) }

        coVerify { util.generates3ShareUrl(any(), any(), any()) }
        file.delete()
    }
}
