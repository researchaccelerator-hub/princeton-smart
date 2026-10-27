package com.screenlake.data.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Updating to 1.53.0 must keep screenshots that were captured but not yet uploaded. Builds a
 * database from the exported schema 3 and opens it with the current database and migrations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Migration3To4Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `pending screenshots survive the update and new rows store a state`() {
        createVersion3Database()

        val db = Room.databaseBuilder(context, ScreenshotDatabase::class.java, dbName)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val sqlite = db.openHelper.writableDatabase
            sqlite.query("SELECT filePath, state FROM screenshot_table").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("/data/img_pending.jpg", cursor.getString(0))
                assertNull(cursor.getString(1))
            }

            sqlite.execSQL(
                "INSERT INTO screenshot_table (isOcrComplete, filePath, state) " +
                    "VALUES (1, '', '${ScreenshotState.SCREEN_LOCKED}')"
            )
            sqlite.query("SELECT state FROM screenshot_table WHERE filePath = ''").use { cursor ->
                cursor.moveToFirst()
                assertEquals(ScreenshotState.SCREEN_LOCKED, cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }

    private fun createVersion3Database() {
        val schema = JSONObject(File(SCHEMA_3).readText()).getJSONObject("database")
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
            sqlite.execSQL(
                "INSERT INTO screenshot_table (isOcrComplete, filePath) VALUES (0, '/data/img_pending.jpg')"
            )
            sqlite.version = 3
        }
    }

    private companion object {
        // Unit tests run from the app module directory.
        const val SCHEMA_3 = "schemas/com.screenlake.data.database.ScreenshotDatabase/3.json"
    }
}
