package com.screenlake.data.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.entity.PackageEventEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageEventDaoTest {

    private lateinit var database: ScreenshotDatabase
    private lateinit var dao: PackageEventDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScreenshotDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.getPackageEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveThenFetchReturnsTheInsertedEvent() = runBlocking {
        dao.save(
            PackageEventEntity(
                user = "hash123",
                packageName = "com.example.app",
                appName = "Example App",
                eventType = "INSTALLED",
                eventTime = 1000L,
                isReplacing = false
            )
        )

        val events = dao.getAllPackageEvents(limit = 10)

        assertEquals(1, events.size)
        assertEquals("com.example.app", events.first().packageName)
        assertEquals("Example App", events.first().appName)
        assertEquals(false, events.first().isReplacing)
    }

    @Test
    fun getAllPackageEventsOrdersByEventTimeDescending() = runBlocking {
        dao.save(PackageEventEntity(packageName = "a", eventType = "INSTALLED", eventTime = 100L))
        dao.save(PackageEventEntity(packageName = "b", eventType = "INSTALLED", eventTime = 200L))

        val events = dao.getAllPackageEvents(limit = 10)

        assertEquals(listOf("b", "a"), events.map { it.packageName })
    }

    @Test
    fun deletePackageEventsRemovesOnlyTheSpecifiedIds() = runBlocking {
        dao.save(PackageEventEntity(packageName = "a", eventType = "INSTALLED", eventTime = 100L))
        dao.save(PackageEventEntity(packageName = "b", eventType = "INSTALLED", eventTime = 200L))
        val ids = dao.getAllPackageEvents(limit = 10).mapNotNull { it.id }
        val idToDelete = ids.first { dao.getAllPackageEvents(10).first { e -> e.id == it }.packageName == "a" }

        dao.deletePackageEvents(listOf(idToDelete))

        val remaining = dao.getAllPackageEvents(limit = 10)
        assertEquals(1, remaining.size)
        assertEquals("b", remaining.first().packageName)
        assertTrue(remaining.none { it.id == idToDelete })
    }
}
