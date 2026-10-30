package com.screenlake.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 3 (1.51.0) added the `package_event` table and changed nothing else. Without this
 * migration, a phone still on schema 2 (SMART 1.50.x, or a copy of SMART forked from it) would
 * have its local database wiped on update, losing screenshots and zips not yet uploaded.
 * The SQL matches the exported schema 3.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `package_event` (`user` TEXT, `packageName` TEXT, " +
                "`appName` TEXT, `eventType` TEXT, `eventTime` INTEGER, `isReplacing` INTEGER NOT NULL, " +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT)"
        )
    }
}

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
