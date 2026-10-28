package com.screenlake.recorder.screenshot

import com.screenlake.data.database.ScreenshotState
import com.screenlake.data.database.entity.ScreenshotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Schema 4 appends `state` as the last screenshot column, so readers of older files that look
 * up columns by name or position before it keep working.
 */
class DataTransformationScreenshotStateTest {

    @Test
    fun `state is the last column`() {
        val csv = DataTransformation.createScreenshotCsv(
            listOf(ScreenshotEntity(epochTimeStamp = 1_700_000_000_000L, state = ScreenshotState.PAUSE_START))
        )
        val lines = csv.trim().lines()

        assertEquals("\"state\"", lines[0].split(",").last())
        assertEquals("\"pause start\"", lines[1].split(",").last())
    }

    @Test
    fun `rows from before schema 4 have an empty state`() {
        val csv = DataTransformation.createScreenshotCsv(listOf(ScreenshotEntity(epochTimeStamp = 0L)))

        assertEquals("\"\"", csv.trim().lines()[1].split(",").last())
    }
}
