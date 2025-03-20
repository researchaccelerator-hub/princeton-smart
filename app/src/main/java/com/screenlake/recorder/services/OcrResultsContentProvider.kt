package com.screenlake.recorder.services

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.support.annotation.NonNull
import android.support.annotation.Nullable
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.di.DatabaseModule

class ScreenshotContentProvider : ContentProvider() {
    companion object {
        // Authority should match what's in your manifest
        private const val AUTHORITY = "com.screenlake.provider"

        // Define URIs for different operations
        val SCREENSHOTS_URI = Uri.parse("content://$AUTHORITY/screenshots")
        val OCR_INCOMPLETE_URI = Uri.parse("content://$AUTHORITY/screenshots/ocr_incomplete")

        // Define column names that match your Room entities
        const val COLUMN_ID = "id"
        const val COLUMN_IS_OCR_COMPLETE = "isOcrComplete"
        const val COLUMN_TEXT = "text"
        const val COLUMN_TYPE = "type"
        const val COLUMN_IS_APP_RESTRICTED = "isAppRestricted"
        const val COLUMN_TIMESTAMP = "timestamp"

        // Create UriMatcher to identify which operation is being requested
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "screenshots", 1)
            addURI(AUTHORITY, "screenshots/#", 2) // Single item by ID
            addURI(AUTHORITY, "screenshots/ocr_incomplete", 3) // OCR incomplete screenshots
        }

        // MIME types
        private const val SCREENSHOTS_DIR_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.screenshots"
        private const val SCREENSHOT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.screenshots"
    }

    private lateinit var database: ScreenshotDatabase

    override fun onCreate(): Boolean {
        database = DatabaseModule.provideDatabase(context!!)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val sqliteDb = database.openHelper.readableDatabase
        val cursor: Cursor?

        when (uriMatcher.match(uri)) {
            3 -> { // OCR incomplete screenshots
                // Get limit and offset from query parameters or use defaults
                val limit = uri.getQueryParameter("limit")?.toIntOrNull() ?: 20
                val offset = uri.getQueryParameter("offset")?.toIntOrNull() ?: 0

                // Create a raw query to match the DAO function
                val rawQuery = "SELECT * FROM screenshot_table where isOcrComplete is 0 and type is 'SCREENSHOT' and isAppRestricted is 0 ORDER BY timestamp DESC LIMIT ? OFFSET ?"
                cursor = sqliteDb.query(rawQuery, arrayOf(limit.toString(), offset.toString()))
            }
            2 -> { // Single screenshot by ID
                val id = uri.lastPathSegment?.toIntOrNull() ?: return null
                cursor = sqliteDb.query("SELECT * FROM screenshot_table WHERE id = ?",
                    arrayOf(id.toString()))
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

        // Set notification URI for cursor
        cursor?.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            1, 3 -> SCREENSHOTS_DIR_TYPE
            2 -> SCREENSHOT_ITEM_TYPE
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Not supporting insert operation for this implementation
        throw UnsupportedOperationException("Insert operation not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not supporting delete operation for this implementation
        throw UnsupportedOperationException("Delete operation not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        if (values == null) return 0

        return when (uriMatcher.match(uri)) {
            2 -> { // Update single screenshot by ID
                val id = uri.lastPathSegment?.toIntOrNull() ?: return 0

                // Extract values needed for setOcrComplete
                val isOcrComplete = values.getAsBoolean(COLUMN_IS_OCR_COMPLETE)
                val text = values.getAsString(COLUMN_TEXT) ?: ""

                // Use a direct SQL update that matches the DAO function
                val sqliteDb = database.openHelper.writableDatabase
                val statement = sqliteDb.compileStatement(
                    "UPDATE screenshot_table SET isOcrComplete=?, text=? WHERE id = ?"
                )

                try {
                    statement.bindLong(1, if (isOcrComplete) 1 else 0)
                    statement.bindString(2, text)
                    statement.bindLong(3, id.toLong())

                    val count = statement.executeUpdateDelete()
                    if (count > 0) {
                        // Notify changes to both URIs
                        context!!.contentResolver.notifyChange(uri, null)
                        context!!.contentResolver.notifyChange(OCR_INCOMPLETE_URI, null)
                    }
                    return count
                } finally {
                    statement.close()
                }
            }
            else -> throw IllegalArgumentException("Update not supported for URI: $uri")
        }
    }

    // Helper method for cursor transformations could be added if needed
}