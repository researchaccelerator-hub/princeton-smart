package com.screenlake.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.screenlake.data.database.dao.AccessibilityEventDao
import com.screenlake.data.database.dao.AppSegmentDao
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.PanelDao
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.database.dao.ScreenshotZipDao
import com.screenlake.data.database.dao.ScrollEventDao
import com.screenlake.data.database.dao.SessionDao
import com.screenlake.data.database.dao.SettingsDao
import com.screenlake.data.database.dao.TopicSeenDao
import com.screenlake.data.database.dao.UploadDailyDao
import com.screenlake.data.database.dao.UploadHistoryDao
import com.screenlake.data.database.dao.UserDao
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.database.entity.AppSegmentEntity
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.PanelInviteEntity
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import com.screenlake.data.database.entity.ScrollEventSegmentEntity
import com.screenlake.data.database.entity.TopicSeenIntervalEntity
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.SessionEntity
import com.screenlake.data.database.entity.SettingsEntity
import com.screenlake.data.database.entity.UploadDailyEntity
import com.screenlake.data.database.entity.UploadHistoryEntity
import com.screenlake.data.database.entity.UserEntity

@Database(
    entities = [
        ScreenshotEntity::class,
        ScreenshotZipEntity::class,
        UserEntity::class,
        RestrictedAppPersistentEntity::class,
        SettingsEntity::class,
        UploadDailyEntity::class,
        UploadHistoryEntity::class,
        PanelInviteEntity::class,
        LogEventEntity::class,
        SessionEntity::class,
        AppSegmentEntity::class,
        AccessibilityEventEntity::class,
        ScrollEventSegmentEntity::class,
        TopicSeenIntervalEntity::class
    ],
    version = 1
)
abstract class ScreenshotDatabase : RoomDatabase() {
    abstract fun getScreenshotDao(): ScreenshotDao
    abstract fun getAppSegmentDao(): AppSegmentDao
    abstract fun getSessionDao(): SessionDao
    abstract fun getScreenshotZipDao(): ScreenshotZipDao
    abstract fun getRestrictedAppDao(): RestrictedAppDao
    abstract fun getUserDao(): UserDao
    abstract fun getSettingsDao(): SettingsDao
    abstract fun getUploadDailyDao(): UploadDailyDao
    abstract fun getUploadHistoryDao(): UploadHistoryDao
    abstract fun getPanelDao(): PanelDao
    abstract fun getLogEventDao(): LogEventDao
    abstract fun getAccessibilityEventDao(): AccessibilityEventDao
    abstract fun getScrollEventDao(): ScrollEventDao
    abstract fun getTopicSeenDao(): TopicSeenDao

    companion object {
        const val DATABASE_NAME = "the_lake"
    }
}