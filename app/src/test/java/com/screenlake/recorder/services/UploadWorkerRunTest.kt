package com.screenlake.recorder.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Uploads and zips go through one on-demand job each, so a run must not drop work: it retries
 * when another run holds the lock, uploads files that become ready while it is running, and
 * never fails (WorkManager would fail the run queued after it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UploadWorkerRunTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository: GeneralOperationsRepository = mockk(relaxed = true)
    private val handler: UploadHandler = mockk()

    @Before
    fun setUp() {
        coEvery { repository.userExists() } returns true
        coEvery { repository.getUser() } returns UserEntity(email = "p@example.org")
        coEvery { handler.isNetworkConnected() } returns true
        coEvery { handler.prepareUploadSession() } returns UploadResult.SUCCESS
    }

    @After
    fun tearDown() {
        if (UploadWorker.mutex.isLocked) UploadWorker.mutex.unlock()
    }

    @Test
    fun `a run that finds another upload in progress retries instead of failing`() = runBlocking {
        UploadWorker.mutex.lock()

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `a zip that finishes during the run is uploaded by the same run`() = runBlocking {
        val first = zip(1)
        val second = zip(2)
        coEvery { repository.getZipsToUpload() } returnsMany listOf(listOf(first), listOf(second), emptyList())
        coEvery { handler.uploadFile(any(), any(), any(), any(), any()) } returns UploadResult.SUCCESS

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { handler.uploadFile(File(first.file!!), 1, any(), any(), any()) }
        coVerify(exactly = 1) { handler.uploadFile(File(second.file!!), 2, any(), any(), any()) }
    }

    @Test
    fun `a file that keeps failing is tried once per run`() = runBlocking {
        val stuck = zip(1)
        coEvery { repository.getZipsToUpload() } returns listOf(stuck)
        coEvery { handler.uploadFile(any(), any(), any(), any(), any()) } returns UploadResult.CONFIGURATION_FAILURE

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { handler.uploadFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `repeated upload requests share one job`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )

        OnDemandWork.requestNow(context, UploadWorker.UPLOAD_ON_DEMAND_WORK, UploadWorker::request)
        OnDemandWork.requestNow(context, UploadWorker.UPLOAD_ON_DEMAND_WORK, UploadWorker::request)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UploadWorker.UPLOAD_ON_DEMAND_WORK).get()
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `a request queues one more run only when none is waiting`() {
        assertTrue(OnDemandWork.needsNewRun(emptyList()))
        assertTrue(OnDemandWork.needsNewRun(listOf(WorkInfo.State.RUNNING)))
        assertTrue(OnDemandWork.needsNewRun(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED)))
        assertFalse(OnDemandWork.needsNewRun(listOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)))
        assertFalse(OnDemandWork.needsNewRun(listOf(WorkInfo.State.ENQUEUED)))
    }

    @Test
    fun `an AWS setup failure ends the run without failing a queued upload`() = runBlocking {
        coEvery { handler.prepareUploadSession() } returns UploadResult.CONFIGURATION_FAILURE

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test
    fun `a zip error ends the run without failing a queued zip`() = runBlocking {
        coEvery { repository.getScreenshotCount() } throws IllegalStateException("broken row")
        val zipWorker = TestListenableWorkerBuilder<ZipFileWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ZipFileWorker(appContext, workerParameters, repository)
            })
            .build()

        assertEquals(ListenableWorker.Result.success(), zipWorker.doWork())
    }

    private fun zip(id: Int): ScreenshotZipEntity {
        val file = File(context.cacheDir, "zip_$id.zip").apply { writeText("data") }
        return ScreenshotZipEntity(file = file.path).also { it.id = id }
    }

    private fun worker(): UploadWorker =
        TestListenableWorkerBuilder<UploadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = UploadWorker(appContext, workerParameters, handler, repository)
            })
            .build()
}
