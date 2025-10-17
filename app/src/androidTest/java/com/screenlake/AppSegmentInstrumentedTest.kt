package com.screenlake

import android.util.Log // REMOVE LATER
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.model.AppSegmentResult
import com.screenlake.data.database.entity.AppSegmentEntity
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.di.DatabaseModule
import com.screenlake.recorder.screenshot.DataTransformation
import com.screenlake.screenshotdata.ScreenshotData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AppSegmentInstrumentedTest {

    private lateinit var database: ScreenshotDatabase
    private lateinit var appContext: Context
    private lateinit var daoScreenshot: ScreenshotDao
    private lateinit var sampleScreenshots: List<ScreenshotEntity>

    @Before
    fun setDB() = runBlocking {
        // Set up the test environment, initialize database, DAO, and sample screenshots
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.getDir("tmp1", Context.MODE_PRIVATE)
        database = DatabaseModule.provideDatabase(appContext)
        daoScreenshot = database.getScreenshotDao()

        sampleScreenshots = listOf(
            ScreenshotEntity(
                user = "user1",
                filePath = "/path/to/file1",
                zipFileId = "zip1",
                currentAppInUse = "App1",
                currentAppRealNameInUse = "App1Real",
                epochTimeStamp = Instant.parse("2023-11-01T10:00:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:00:00Z",
                sessionId = "session1"
            ),
            ScreenshotEntity(
                user = "user1",
                filePath = "/path/to/file2",
                zipFileId = "zip2",
                currentAppInUse = "App1",
                currentAppRealNameInUse = "App1Real",
                epochTimeStamp = Instant.parse("2023-11-01T10:03:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:03:00Z",
                sessionId = "session1"
            ),
            ScreenshotEntity(
                user = "user1",
                filePath = "/path/to/file3",
                zipFileId = "zip3",
                currentAppInUse = "App2",
                currentAppRealNameInUse = "App2Real",
                epochTimeStamp = Instant.parse("2023-11-01T10:05:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:05:00Z",
                sessionId = "session1"
            )
        )
    }

    @After
    fun cleanup() {
        // Clean up the database after test execution
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                daoScreenshot.nukeTable()
            }
            database.close()
        }
    }

    /**
     * Test to verify that the application context is being correctly used.
     * The test asserts that the package name matches the expected value.
     */
    @Test
    fun useAppContext() {
        // Context of the app under test.
        assertEquals("com.screenlake", appContext.packageName)
    }

    /**
     * Test to verify the creation of app segments based on screenshot data.
     * Inserts sample screenshots into the database and checks if the resulting app segments are as expected.
     */
    @Test
    fun appSegmentDemo() = runBlocking {
        val screenshots = ScreenshotData.screenshotList
        screenshots.forEach { daoScreenshot.insertScreenshot(it) }

        val screenshotsBySession = screenshots.first().sessionId?.let {
            daoScreenshot.getScreenshotsBySessionId(it)
        }

        val appSegmentData = DataTransformation.getAppSegmentData(screenshotsBySession)

        val screenshotsSize = screenshots.size
        val segmentSize = appSegmentData?.appSegments?.size
        val sessionId = screenshots.first().sessionId

        Log.i("appSegmentDemo", "screenshot size: $screenshotsSize")
        Log.i("appSegmentDemo", "segment size: $segmentSize")
        Log.i("appSegmentDemo", "session id: $sessionId")

        assert(appSegmentData?.appSegments?.size == 2)
    }

    /**
     * Test to verify that the createAppSegmentCSV function correctly generates a CSV from app segment data.
     * The resulting CSV is checked to ensure it contains the expected values.
     */
    @Test
    fun testCreateAppSegmentCSV() {
        val appSegments = listOf(
            AppSegmentEntity(
                appTitle = "App1",
                appSegmentStart = 1635765600000,
                appSegmentDuration = 300000,
                sessionId = "session1",
                appSegmentId = "segment1",
                userId = "user1"
            ),
            AppSegmentEntity(
                appTitle = "App2",
                appSegmentStart = 1635765900000,
                appSegmentDuration = 180000,
                sessionId = "session1",
                appSegmentId = "segment2",
                userId = "user1"
            )
        )

        val csvResult = DataTransformation.createAppSegmentCSV(appSegments)
        assertNotNull(csvResult)
        assertTrue(csvResult.contains("App1"))
        assertTrue(csvResult.contains("session1"))
        assertTrue(csvResult.contains("segment1"))
    }

    /**
     * Test to verify the grouping and sorting of screenshot data into app segments.
     * The test checks if the resulting segments are grouped and sorted as expected.
     */
    @Test
    fun testGroupAndSortData() {
        val result = DataTransformation.groupAndSortData(sampleScreenshots)
        assertNotNull(result)
        assertEquals(2, result.appSegments.size)
        assertEquals("App1", result.appSegments[0].appTitle)
        assertEquals("App2", result.appSegments[1].appTitle)
    }

    /**
     * Test to verify that previous app data is correctly added to app segments.
     * Checks if each app segment correctly reflects the previous app usage data.
     */
    @Test
    fun testAddPrevAppDataToSegments() {
        val appSegments = AppSegmentResult(
            appSegments = arrayOf(
                AppSegmentEntity("App1", 1635765600000, 300000, 1, "session1", "segment1", "user1"),
                AppSegmentEntity("App2", 1635765900000, 180000, 1, "session1", "segment2", "user1")
            ),
            screenshots = emptyList()
        )

        val result = DataTransformation.addPrevAppDataToSegments(appSegments)
        assertNotNull(result)
        assertEquals("App1", result.appSegments[1].appPrev1)
        assertEquals("NA", result.appSegments[0].appPrev1)
    }

    /**
     * Test to verify that the getAppSegmentData function correctly processes screenshot data
     * and returns app segments with accurate app titles.
     */
    @Test
    fun testGetAppSegmentData() {
        val result = DataTransformation.getAppSegmentData(sampleScreenshots)
        assertNotNull(result)
        assertEquals(2, result?.appSegments?.size)
        assertEquals("App1", result?.appSegments?.get(0)?.appTitle)
        assertEquals("App2", result?.appSegments?.get(1)?.appTitle)
    }
}
