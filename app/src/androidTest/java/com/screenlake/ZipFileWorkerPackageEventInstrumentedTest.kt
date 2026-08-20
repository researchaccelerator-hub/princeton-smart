package com.screenlake

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.screenlake.data.TestWorkerFactory
import com.screenlake.data.database.entity.PackageEventEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ResearchConfig
import com.screenlake.recorder.services.ZipFileWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class ZipFileWorkerPackageEventInstrumentedTest {

    private lateinit var context: Context
    private lateinit var genOp: GeneralOperationsRepository

    @Before
    fun setUp() {
        Timber.plant(Timber.DebugTree())
        context = ApplicationProvider.getApplicationContext()
        genOp = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(ResearchConfig)
        Timber.uprootAll()
    }

    @Test
    fun testDoWork_flushesPendingPackageEventsWithZeroScreenshots() = runBlocking {
        mockkObject(ResearchConfig)
        every { ResearchConfig.LOG_PACKAGE_EVENTS } returns true

        val pendingEvent = PackageEventEntity(
            user = "testhash",
            packageName = "com.example.app",
            appName = "Example App",
            eventType = "INSTALLED",
            eventTime = System.currentTimeMillis(),
            isReplacing = false
        ).apply { id = 1 }

        coEvery { genOp.getScreenshotCount() } returns 0
        coEvery { genOp.getUser() } returns UserEntity(
            email = "test@example.com",
            panelId = "1",
            panelName = "Test Panel",
            emailHash = "testhash",
            tenantId = "tenant123",
            tenantName = "TestTenant"
        )
        coEvery { genOp.getPendingPackageEvents() } returns listOf(pendingEvent)

        val zipFileWorker = TestListenableWorkerBuilder<ZipFileWorker>(context)
            .setWorkerFactory(TestWorkerFactory(genOp))
            .build()

        val result = zipFileWorker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { genOp.deletePackageEvents(listOf(1)) }
        coVerify(exactly = 1) { genOp.insertScreenshotZip(any()) }
    }

    @Test
    fun testDoWork_noPendingPackageEventsSkipsZipCreation() = runBlocking {
        mockkObject(ResearchConfig)
        every { ResearchConfig.LOG_PACKAGE_EVENTS } returns true

        coEvery { genOp.getScreenshotCount() } returns 0
        coEvery { genOp.getUser() } returns UserEntity(
            email = "test@example.com",
            panelId = "1",
            panelName = "Test Panel",
            emailHash = "testhash",
            tenantId = "tenant123",
            tenantName = "TestTenant"
        )
        coEvery { genOp.getPendingPackageEvents() } returns emptyList()

        val zipFileWorker = TestListenableWorkerBuilder<ZipFileWorker>(context)
            .setWorkerFactory(TestWorkerFactory(genOp))
            .build()

        val result = zipFileWorker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { genOp.deletePackageEvents(any()) }
        coVerify(exactly = 0) { genOp.insertScreenshotZip(any()) }
    }

    @Test
    fun testDoWork_doesNothingWhenPackageEventLoggingDisabled() = runBlocking {
        // ResearchConfig.LOG_PACKAGE_EVENTS is left at its real default (false) --
        // no mockkObject needed, this is exactly what a study that never enables the
        // feature will run with.
        val pendingEvent = PackageEventEntity(
            user = "testhash",
            packageName = "com.example.app",
            appName = "Example App",
            eventType = "INSTALLED",
            eventTime = System.currentTimeMillis(),
            isReplacing = false
        ).apply { id = 1 }

        coEvery { genOp.getScreenshotCount() } returns 0
        coEvery { genOp.getUser() } returns UserEntity(
            email = "test@example.com",
            panelId = "1",
            panelName = "Test Panel",
            emailHash = "testhash",
            tenantId = "tenant123",
            tenantName = "TestTenant"
        )
        coEvery { genOp.getPendingPackageEvents() } returns listOf(pendingEvent)

        val zipFileWorker = TestListenableWorkerBuilder<ZipFileWorker>(context)
            .setWorkerFactory(TestWorkerFactory(genOp))
            .build()

        val result = zipFileWorker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { genOp.getPendingPackageEvents() }
        coVerify(exactly = 0) { genOp.deletePackageEvents(any()) }
        coVerify(exactly = 0) { genOp.insertScreenshotZip(any()) }
    }
}
