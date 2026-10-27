package com.screenlake.data.database.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.entity.ScreenshotEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OCR works through the queue oldest first, and only rows that have an image belong in it;
 * an image-less row would never be completed and would be fetched again on every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScreenshotDaoOcrQueueTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), ScreenshotDatabase::class.java
    ).allowMainThreadQueries().build()
    private val dao = db.getScreenshotDao()

    @After
    fun close() = db.close()

    @Test
    fun `queue is oldest first and skips rows without an image`() = runBlocking {
        dao.insertScreenshot(row("2026-10-27T10:00:02Z", "/img/new.jpg"))
        dao.insertScreenshot(row("2026-10-27T10:00:00Z", "/img/old.jpg"))
        dao.insertScreenshot(row("2026-10-27T10:00:01Z", ""))
        dao.insertScreenshot(row("2026-10-27T10:00:03Z", null))
        dao.insertScreenshot(row("2026-10-27T09:00:00Z", "/img/done.jpg").apply { isOcrComplete = true })

        val queue = dao.getAllScreenshotsSortedByDateWhereOcrIsNotComplete(limit = 100, offset = 0)

        assertEquals(listOf("/img/old.jpg", "/img/new.jpg"), queue.map { it.filePath })
    }

    private fun row(timestamp: String, filePath: String?) = ScreenshotEntity(
        timestamp = timestamp,
        type = "SCREENSHOT",
        isAppRestricted = false,
        filePath = filePath
    )
}
