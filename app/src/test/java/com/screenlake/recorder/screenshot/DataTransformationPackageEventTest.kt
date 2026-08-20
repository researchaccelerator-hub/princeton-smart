package com.screenlake.recorder.screenshot

import com.screenlake.data.database.entity.PackageEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DataTransformationPackageEventTest {

    @Test
    fun `creates one CSV row per package event with the expected header`() {
        val event = PackageEventEntity(
            user = "hash123",
            packageName = "com.example.app",
            appName = "Example App",
            eventType = "INSTALLED",
            eventTime = 1_700_000_000_000L,
            isReplacing = false
        )

        val csv = DataTransformation.createPackageEventCSV(listOf(event))
        val lines = csv.trim().lines()

        assertEquals(2, lines.size)
        assertEquals(
            "\"id_user\",\"apk\",\"app_name\",\"event_type\",\"t_unix_ts_ms\",\"is_replacing\"",
            lines[0]
        )
        assertEquals(
            "\"hash123\",\"com.example.app\",\"Example App\",\"INSTALLED\",\"1700000000000\",\"false\"",
            lines[1]
        )
    }

    @Test
    fun `returns header only for an empty list`() {
        val csv = DataTransformation.createPackageEventCSV(emptyList())

        assertEquals(1, csv.trim().lines().size)
    }

    @Test
    fun `renders a null app name as the literal string null`() {
        val event = PackageEventEntity(
            user = "hash123",
            packageName = "com.example.gone",
            appName = null,
            eventType = "UNINSTALLED",
            eventTime = 1000L,
            isReplacing = false
        )

        val csv = DataTransformation.createPackageEventCSV(listOf(event))

        assertEquals(
            "\"hash123\",\"com.example.gone\",\"null\",\"UNINSTALLED\",\"1000\",\"false\"",
            csv.trim().lines()[1]
        )
    }
}
