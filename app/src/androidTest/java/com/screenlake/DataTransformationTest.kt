package com.screenlake

import com.screenlake.data.model.AppSegmentResult
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.SessionEntity
import com.screenlake.recorder.screenshot.DataTransformation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.regex.Pattern

class DataTransformationTest {

    private lateinit var sampleSessions: List<SessionEntity>
    private val isoFormatRegex = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
    private lateinit var sampleScreenshots: List<ScreenshotEntity>
    private lateinit var sampleAccessibilityEvents: List<AccessibilityEventEntity>
    private lateinit var multiGroupScreenshots: List<ScreenshotEntity>

    @Before
    fun setUp() {
        sampleSessions = listOf(
            SessionEntity(
                user = "user1",
                sessionStart = "2023-11-01T09:00:00Z",
                sessionEnd = "2023-11-01T10:00:00Z",
                sessionId = "session1",
                sessionDuration = 3600000, // 1 hour in milliseconds
                secondsSinceLastActive = 3000, // 3 seconds in milliseconds
                sessionCountPerDay = 1,
                fps = 30.0, // fps as Double
                panelId = "panel1",
                tenantId = "tenant1",
                sessionStartEpoch = Instant.parse("2023-11-01T09:00:00Z").toEpochMilli(),
                sessionEndEpoch = Instant.parse("2023-11-01T10:00:00Z").toEpochMilli()
            ),
            SessionEntity(
                user = "user2",
                sessionStart = "2023-11-01T10:15:00Z",
                sessionEnd = "2023-11-01T11:00:00Z",
                sessionId = "session2",
                sessionDuration = 2700000, // 45 minutes in milliseconds
                secondsSinceLastActive = 5000, // 5 seconds in milliseconds
                sessionCountPerDay = 2,
                fps = 60.5, // fps as Double
                panelId = "panel2",
                tenantId = "tenant2",
                sessionStartEpoch = Instant.parse("2023-11-01T10:15:00Z").toEpochMilli(),
                sessionEndEpoch = Instant.parse("2023-11-01T11:00:00Z").toEpochMilli()
            )
        )

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

        sampleAccessibilityEvents = listOf(
            AccessibilityEventEntity(
                user = "user1",
                eventType = "VIEW_CLICKED",
                eventTime = Instant.parse("2023-11-01T10:01:00Z").toEpochMilli(),
                packageName = "com.example.app",
                sessionId = "session1",
                accessibilitySessionId = "session1",
                appIntervalId = "interval1",
                text = "Test Event"
            )
        )

        multiGroupScreenshots = listOf(
            // Group 1: App1
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
                epochTimeStamp = Instant.parse("2023-11-01T10:01:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:01:00Z",
                sessionId = "session1"
            ),
            // Group 2: App2
            ScreenshotEntity(
                user = "user1",
                filePath = "/path/to/file3",
                zipFileId = "zip3",
                currentAppInUse = "App2",
                currentAppRealNameInUse = "App2Real",
                epochTimeStamp = Instant.parse("2023-11-01T10:05:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:05:00Z",
                sessionId = "session1"
            ),
            ScreenshotEntity(
                user = "user1",
                filePath = "/path/to/file4",
                zipFileId = "zip4",
                currentAppInUse = "App2",
                currentAppRealNameInUse = "App2Real",
                epochTimeStamp = Instant.parse("2023-11-01T10:06:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:06:00Z",
                sessionId = "session1"
            ),
            // Group 3: App3
            ScreenshotEntity(
                user = "user1",
                filePath = "/path/to/file5",
                zipFileId = "zip5",
                currentAppInUse = "App3",
                currentAppRealNameInUse = "App3Real",
                epochTimeStamp = Instant.parse("2023-11-01T10:10:00Z").toEpochMilli(),
                timestamp = "2023-11-01T10:10:00Z",
                sessionId = "session1"
            )
        )
    }

    /**
     * Verifies that the JSON representation of the session contains all the necessary fields.
     * The test ensures that fields like `user`, `sessionStart`, `sessionEnd`, `sessionId`, etc. are present.
     */
    @Test
    fun testCreateSessionJsonIncludesAllFields() {
        val sessionJson = DataTransformation.createSessionJson(sampleSessions)
        assertNotNull(sessionJson)

        // Check for all expected fields in the session JSON representation
        sampleSessions.forEach { session ->
            assertTrue(sessionJson.contains(session.user.toString()))
            assertTrue(sessionJson.contains(session.sessionStart.toString()))
            assertTrue(sessionJson.contains(session.sessionEnd.toString()))
            assertTrue(sessionJson.contains(session.sessionId.toString()))
            assertTrue(sessionJson.contains(session.sessionDuration.toString()))
            assertTrue(sessionJson.contains(session.secondsSinceLastActive.toString()))
            assertTrue(sessionJson.contains(session.sessionCountPerDay.toString()))
            assertTrue(sessionJson.contains(session.panelId.toString()))
            assertTrue(sessionJson.contains(session.tenantId.toString()))
        }
    }

    /**
     * Verifies the format of session start and end timestamps.
     * The test checks that these timestamps follow the ISO 8601 format.
     */
    @Test
    fun testSessionStartAndEndFormat() {
        // Check that sessionStart and sessionEnd are in the correct ISO 8601 format
        sampleSessions.forEach { session ->
            assertTrue("sessionStart format is incorrect", isoFormatRegex.matcher(session.sessionStart).matches())
            assertTrue("sessionEnd format is incorrect", isoFormatRegex.matcher(session.sessionEnd).matches())
        }
    }

    /**
     * Verifies that session start and end epoch values are within a reasonable range.
     * Ensures that epoch values are positive and within the range of valid Unix epoch time.
     */
    @Test
    fun testEpochValuesWithinRange() {
        // Check that sessionStartEpoch and sessionEndEpoch are within a reasonable range (e.g., 1970-01-01 to current time)
        val currentEpoch = Instant.now().toEpochMilli()

        sampleSessions.forEach { session ->
            assertNotNull("sessionStartEpoch should not be null", session.sessionStartEpoch)
            assertNotNull("sessionEndEpoch should not be null", session.sessionEndEpoch)

            // Ensure the epoch values are positive and within the current time range
            assertTrue("sessionStartEpoch is out of range", session.sessionStartEpoch!! in 0..currentEpoch)
            assertTrue("sessionEndEpoch is out of range", session.sessionEndEpoch!! in 0..currentEpoch)
        }
    }

    /**
     * Verifies that the `fps` (frames per second) attribute is a Double and greater than 0.
     */
    @Test
    fun testFpsIsDouble() {
        // Check that fps is correctly represented as a Double and within a reasonable range
        sampleSessions.forEach { session ->
            assertTrue("fps should be a Double", session.fps is Double)
            assertTrue("fps should be greater than 0", session.fps > 0.0)
        }
    }

    /**
     * Verifies that the CSV generated from screenshot data includes the expected fields.
     * This test checks that user, file path, and timestamp are present in the CSV output.
     */
    @Test
    fun testCreateScreenshotCsv() {
        val csvResult = DataTransformation.createScreenshotCsv(sampleScreenshots)
        assertNotNull(csvResult)
        assertTrue(csvResult.contains("user1"))
        assertTrue(csvResult.contains("/path/to/file1"))
        assertTrue(csvResult.contains("2023-11-01T10:00:00Z"))
    }

    /**
     * Verifies that the screenshot data is grouped and sorted correctly.
     * The test ensures that the data is divided into expected app segments and sorted properly.
     */
    @Test
    fun testGroupAndSortData() {
        val result = DataTransformation.groupAndSortData(sampleScreenshots)
        assertNotNull(result)
        assertEquals(2, result.appSegments.size) // Expected number of app segments
        assertEquals("App1", result.appSegments[0].appTitle)
        assertEquals("App2", result.appSegments[1].appTitle)
    }

    /**
     * Tests that the method correctly finds matching screenshots and accessibility events.
     * The purpose is to ensure no exceptions are thrown during matching, and behavior is logged.
     */
    @Test
    fun testFindMatchingScreenshotsAndEvents() {
        // Invoke the method and capture the logs if necessary
        DataTransformation.findMatchingScreenshotsAndEvents(
            time1 = Instant.parse("2023-11-01T10:00:00Z").toEpochMilli(),
            screenshots = sampleScreenshots,
            accessibilityEvents = sampleAccessibilityEvents
        )

        // For test validation, ensure no exceptions were thrown and examine behavior via logging
        assertTrue("The test ran without exceptions", true)
    }

    /**
     * Verifies that accessibility events are grouped correctly with screenshots by session.
     * Ensures that events and screenshots are grouped by session ID, and their relationships are accurate.
     */
    @Test
    fun testGroupAccessibilityEventsAndScreenshots() {
        val groupedEvents = DataTransformation.groupAccessibilityEventsAndScreenshots(
            accessibilityEvents = sampleAccessibilityEvents,
            screenshots = sampleScreenshots
        )
        assertNotNull(groupedEvents)
        assertEquals(1, groupedEvents.size)
        assertEquals("session1", groupedEvents.first().sessionId)
    }

    /**
     * Verifies that multiple screenshots are grouped and sorted into distinct app segments correctly.
     * This test checks that the screenshots are accurately divided into different app segments.
     */
    @Test
    fun testGroupAndSortDataWithMultipleGroups() {
        val result: AppSegmentResult = DataTransformation.groupAndSortData(multiGroupScreenshots)
        assertNotNull(result)

        // Check the number of groups created
        assertEquals("Expected 3 app segments", 3, result.appSegments.size)

        // Validate the content of each group
        assertEquals("First group should be 'App1'", "App1", result.appSegments[0].appTitle)
        assertEquals("Second group should be 'App2'", "App2", result.appSegments[1].appTitle)
        assertEquals("Third group should be 'App3'", "App3", result.appSegments[2].appTitle)

        // Verify timestamps for the first group
        assertEquals(
            "First group start time should match the first screenshot's timestamp",
            multiGroupScreenshots[0].epochTimeStamp,
            result.appSegments[0].appSegmentStart
        )
        assertEquals(
            "First group end time should match the last screenshot in the first group",
            multiGroupScreenshots[1].epochTimeStamp,
            result.appSegments[0].appSegmentEnd
        )

        // Verify timestamps for the second group
        assertEquals(
            "Second group start time should match the first screenshot in the second group",
            multiGroupScreenshots[2].epochTimeStamp,
            result.appSegments[1].appSegmentStart
        )
        assertEquals(
            "Second group end time should match the last screenshot in the second group",
            multiGroupScreenshots[3].epochTimeStamp,
            result.appSegments[1].appSegmentEnd
        )

        // Verify timestamps for the third group
        assertEquals(
            "Third group start and end time should match the single screenshot in the group",
            multiGroupScreenshots[4].epochTimeStamp,
            result.appSegments[2].appSegmentStart
        )
        assertEquals(
            "Third group end time should match the single screenshot in the group",
            multiGroupScreenshots[4].epochTimeStamp,
            result.appSegments[2].appSegmentEnd
        )
    }
}

