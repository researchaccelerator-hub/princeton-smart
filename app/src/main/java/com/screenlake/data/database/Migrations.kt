package com.screenlake.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 4 adds the screenshot `state` column. A real migration keeps screenshots that were
 * captured but not yet uploaded when a phone updates to 1.53.0; destructive migration would
 * delete them. Rows from before the update keep a null state.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `screenshot_table` ADD COLUMN `state` TEXT")
    }
}
