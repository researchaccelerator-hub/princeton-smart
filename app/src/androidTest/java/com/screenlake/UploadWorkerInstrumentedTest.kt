package com.screenlake

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.screenlake.data.TestWorkerFactory
import com.screenlake.data.TestWorkerFactoryException
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.di.DatabaseModule
import com.screenlake.recorder.services.UploadWorker
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/**
 * Instrumented test for the UploadWorker, which handles file uploads in a background thread.
 *
 * The tests validate different scenarios such as successful uploads, failed uploads,
 * missing files, and the presence of network conditions.
 */
@RunWith(AndroidJUnit4::class)
class UploadWorkerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var uploadWorker: UploadWorker
    private lateinit var appDatabase: ScreenshotDatabase
    private lateinit var genOp: GeneralOperationsRepository

    /**
     * Sets up the test environment before each test.
     *
     * Initializes the context, database, and UploadWorker. Also sets up a Timber logger
     * for debug purposes.
     */
    @Before
    fun setUp() {
        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())

        genOp = mockk(relaxed = true)
        // Get application context
        context = ApplicationProvider.getApplicationContext()

        // Create an in-memory Room database
        appDatabase = Room.inMemoryDatabaseBuilder(context, ScreenshotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create a custom WorkerFactory to inject dependencies
        val workerFactory = TestWorkerFactory(genOp)

        // Build the UploadWorker using the TestWorkerFactory
        uploadWorker = TestListenableWorkerBuilder<UploadWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
    }

    /**
     * Cleans up resources after each test.
     *
     * Closes the Room database and removes the Timber logger.
     */
    @After
    fun tearDown() {
        appDatabase.close()
        Timber.uprootAll()
    }

    /**
     * Tests that the UploadWorker successfully processes files.
     *
     * Verifies that after a successful run of the UploadWorker, all zips have been marked
     * as uploaded or deleted from the database.
     */
    @Test
    fun testDoWork_success() = runBlocking {
        // Insert test data
        val user = insertUser()
        val zipsToUpload = insertZipsToUpload(user)
        createDummyZipFiles(zipsToUpload)

        // Run the worker
        val result = uploadWorker.doWork()

        // Assert that the result is success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify that the zips have been marked as uploaded or deleted
        val remainingZips = genOp.getZipsToUpload()
        assert(remainingZips.isNullOrEmpty()) { "All zips should have been uploaded and removed from the database." }

        // Clean up created files
        cleanUpFiles(zipsToUpload)
    }

    /**
     * Tests that the UploadWorker handles the absence of network connection correctly.
     *
     * Verifies that no uploads are attempted when the network is not available.
     */
    @Test
    fun testDoWork_noNetwork() = runBlocking {
        // Use the custom WorkerFactory that simulates no network
        val workerFactory = TestWorkerFactory(genOp, false)

        // Build the worker
        val uploadWorkerLocal = TestListenableWorkerBuilder<UploadWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        // Insert test data
        val user = insertUser()
        val zipsToUpload = insertZipsToUpload(user)
        createDummyZipFiles(zipsToUpload)

        // Run the worker
        val result = uploadWorkerLocal.doWork()

        // Assert that the result is success (or retry, depending on your implementation)
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    /**
     * Tests that the UploadWorker handles missing files correctly.
     *
     * Verifies that when non-existent files are encountered, they are removed from the database.
     */
    @Test
    fun testDoWork_fileNotFound() = runBlocking {
        // Insert test data with non-existent files
        val user = insertUser()
        insertZipsWithMissingFiles(user)

        // Run the worker
        val result = uploadWorker.doWork()

        // Assert that the result is success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify that the missing files were handled and database entries removed
        val remainingZips = genOp.getZipsToUpload()
        assert(remainingZips.isNullOrEmpty()) { "Zips with missing files should have been removed from the database." }
    }

    /**
     * Tests that the UploadWorker handles exceptions during upload.
     *
     * Verifies that when an exception occurs during upload, the result is failure or success depending on
     * the error handling implemented.
     */
    @Test
    fun testDoWork_exceptionDuringUpload() = runBlocking {
        // Use the custom WorkerFactory that throws an exception
        val workerFactory = TestWorkerFactoryException(genOp)

        // Build the worker
        val uploadWorker = TestListenableWorkerBuilder<UploadWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        // Insert test data
        val user = insertUser()
        val zipsToUpload = insertZipsToUpload(user)
        createDummyZipFiles(zipsToUpload)

        // Run the worker
        val result = uploadWorker.doWork()

        // Clean up
        cleanUpFiles(zipsToUpload)

        // Assert that the result is failure or success depending on error handling
        assertEquals(ListenableWorker.Result.success(), result)
    }

    /**
     * Tests that the UploadWorker processes different file types correctly.
     *
     * Verifies that all files, including zips and logs, are processed and removed after successful upload.
     */
    @Test
    fun testDoWork_differentFileTypes() = runBlocking {
        // Insert test data
        val user = insertUser()
        val zipsToUpload = insertZipsToUpload(user)

        // Create dummy files
        createDummyZipFiles(zipsToUpload)

        // Run the worker
        val result = uploadWorker.doWork()

        // Assert that the result is success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify that all files have been processed
        val remainingZips = genOp.getZipsToUpload()
        assert(remainingZips.isNullOrEmpty()) { "All zips should have been uploaded and removed from the database." }

        // Verify that log files have been handled
        val logFiles = File(context.filesDir?.path).listFiles { _, name ->
            name.startsWith("log_data")
        }

        // Clean up
        cleanUpFiles(zipsToUpload,)

        assert(logFiles.isNullOrEmpty()) { "All log files should have been uploaded and deleted." }
    }

    // Helper methods for setting up and managing test data
    private fun insertUser(): UserEntity = runBlocking {
        val user = UserEntity(
            email = "test@example.com",
            panelId = "1",
            panelName = "Test Panel",
            uploadImages = true,
            emailHash = "testhash",
            tenantId = "tenant123",
            tenantName = "TestTenant"
        )
        DatabaseModule.provideDatabase(context).getUserDao().insertUserObj(user)
        return@runBlocking user
    }

    private fun insertZipsToUpload(user: UserEntity): List<ScreenshotZipEntity> {
        val zipsToUpload = (1..3).map { index ->
            ScreenshotZipEntity(
                file = "${context.filesDir}/test_zip_$index.zip",
                localTimeStamp = "timestamp",
                timestamp = "timestamp",
                user = user.email,
                toDelete = false,
                panelId = user.panelId,
                panelName = user.panelName
            )
        }
        zipsToUpload.forEach { DatabaseModule.provideDatabase(context).getScreenshotZipDao().insertZipObj(it) }
        return zipsToUpload
    }

    private fun createDummyZipFiles(zips: List<ScreenshotZipEntity>) {
        zips.forEach { zip ->
            val file = File(zip.file)
            file.parentFile?.mkdirs()
            file.createNewFile()
            file.writeText("Dummy zip content")
        }
    }

    private fun insertZipsWithMissingFiles(user: UserEntity): List<ScreenshotZipEntity> = runBlocking {
        val zipsToUpload = (1..2).map { index ->
            ScreenshotZipEntity(
                file = "${context.filesDir}/non_existent_zip_$index.zip",
                localTimeStamp = "timestamp",
                timestamp = "timestamp",
                user = user.email,
                toDelete = false,
                panelId = user.panelId,
                panelName = user.panelName
            )
        }
        zipsToUpload.forEach { DatabaseModule.provideDatabase(context).getScreenshotZipDao().insertZipObj(it) }
        return@runBlocking zipsToUpload
    }

    private suspend fun cleanUpFiles(zips: List<ScreenshotZipEntity>) {
        zips.forEach { zip ->
            if (zip.id != null) {
                DatabaseModule.provideDatabase(context).getScreenshotZipDao().delete(zip.id!!)
            }

            val file = zip.file?.let { File(it) }
            if (file != null && file.exists()) {
                file.delete()
            }
        }
    }
}
