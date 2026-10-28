package com.screenlake.recorder.screenshot

import com.screenlake.data.database.ScreenshotState
import com.screenlake.data.database.entity.ScreenshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * State marker rows are recorded with whatever app is in front at that moment (often the
 * launcher). They must not create or split app segments, but still need a segment so they are
 * zipped and uploaded.
 */
class AppSegmentMarkerTest {

    @Test
    fun `markers with another app do not split a segment`() {
        val rows = listOf(
            row(0, "com.example.news"),
            row(1, "com.android.launcher", ScreenshotState.PAUSE_START),
            row(2, "com.android.launcher", ScreenshotState.PAUSE_END),
            row(3, "com.example.news"),
            row(4, "com.example.chat"),
            row(5, "com.android.launcher", ScreenshotState.SCREEN_LOCKED),
        )

        val result = DataTransformation.groupAndSortData(rows)

        assertEquals(listOf("com.example.news", "com.example.chat"), result.appSegments.map { it.appTitle })
        assertEquals(rows.size, result.screenshots.size)
        assertTrue(result.screenshots.all { it.appSegmentId != null })
        assertEquals(result.appSegments[1].appSegmentId, rows.last().appSegmentId)
    }

    @Test
    fun `a session that starts with a marker is named after its first capture`() {
        val rows = listOf(
            row(0, "com.android.launcher", ScreenshotState.SCREEN_UNLOCKED),
            row(1, "com.example.news"),
        )

        val result = DataTransformation.groupAndSortData(rows)

        assertEquals(listOf("com.example.news"), result.appSegments.map { it.appTitle })
    }

    private fun row(second: Long, app: String, state: String = ScreenshotState.NORMAL): ScreenshotEntity {
        val time = Instant.ofEpochSecond(1_700_000_000L + second)
        return ScreenshotEntity(
            currentAppInUse = app,
            epochTimeStamp = time.toEpochMilli(),
            timestamp = time.toString(),
            sessionId = "session",
            state = state
        )
    }
}
