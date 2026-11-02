package com.screenlake.data.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A phone on schema 2 (SMART 1.50.x, or a copy of SMART forked from it) must keep screenshots
 * and zips that have not uploaded when it updates. Builds a database from the exported schema 2
 * and opens it with the current database and migrations; Room fails the open if the migrated
 * schema does not match the current one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Migration2To4Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-2-test"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `pending screenshots and zips survive the update from schema 2`() {
        createVersion2Database()

        val db = Room.databaseBuilder(context, ScreenshotDatabase::class.java, dbName)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val sqlite = db.openHelper.writableDatabase
            sqlite.query("SELECT filePath FROM screenshot_table").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("/data/img_pending.jpg", cursor.getString(0))
            }
            sqlite.query("SELECT file FROM screenshot_zip_table").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("/data/pending.zip", cursor.getString(0))
            }

            sqlite.execSQL("INSERT INTO package_event (packageName, eventType, isReplacing) VALUES ('com.example', 'INSTALLED', 0)")
            sqlite.query("SELECT COUNT(*) FROM package_event").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    private fun createVersion2Database() {
        val schema = JSONObject(File(SCHEMA_2).readText()).getJSONObject("database")
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO screenshot_table (isOcrComplete, filePath) VALUES (0, '/data/img_pending.jpg')")
            sqlite.execSQL("INSERT INTO screenshot_zip_table (file) VALUES ('/data/pending.zip')")
            sqlite.version = 2
        }
    }

    private companion object {
        // Unit tests run from the app module directory.
        const val SCHEMA_2 = "schemas/com.screenlake.data.database.ScreenshotDatabase/2.json"
    }
}
