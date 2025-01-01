package com.screenlake.di

import android.content.Context
import androidx.room.Room
import com.screenlake.data.database.ScreenshotDatabase
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
import com.screenlake.data.repository.AwsService
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.RealUploadHandler
import com.screenlake.recorder.services.UploadHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScreenshotDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = ScreenshotDatabase::class.java,
            name = ScreenshotDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideUploadHandler(
        context: Context,
        awsService: AwsService,
        generalOperationsRepository: GeneralOperationsRepository
    ): UploadHandler {
        return RealUploadHandler(context, awsService, generalOperationsRepository)
    }

    @Provides
    fun provideRestrictedAppDao(database: ScreenshotDatabase): RestrictedAppDao {
        return database.getRestrictedAppDao()
    }

    @Provides
    fun provideSettingsDao(database: ScreenshotDatabase): SettingsDao {
        return database.getSettingsDao()
    }

    @Provides
    fun provideScreenshotDao(database: ScreenshotDatabase): ScreenshotDao {
        return database.getScreenshotDao()
    }

    @Provides
    fun provideScreenshotZipDao(database: ScreenshotDatabase): ScreenshotZipDao {
        return database.getScreenshotZipDao()
    }

    @Provides
    fun provideUserDao(database: ScreenshotDatabase): UserDao {
        return database.getUserDao()
    }

    @Provides
    fun provideLogEventDao(database: ScreenshotDatabase): LogEventDao {
        return database.getLogEventDao()
    }

    @Provides
    fun provideScrollEventDao(database: ScreenshotDatabase): ScrollEventDao {
        return database.getScrollEventDao()
    }

    @Provides
    fun provideAccessibilityEventDao(database: ScreenshotDatabase): AccessibilityEventDao {
        return database.getAccessibilityEventDao()
    }

    @Provides
    fun provideAppSegmentDao(database: ScreenshotDatabase): AppSegmentDao {
        return database.getAppSegmentDao()
    }

    @Provides
    fun providePanelDao(database: ScreenshotDatabase): PanelDao {
        return database.getPanelDao()
    }

    @Provides
    fun provideSessionDao(database: ScreenshotDatabase): SessionDao {
        return database.getSessionDao()
    }

    @Provides
    fun provideUploadHistoryDao(database: ScreenshotDatabase): UploadHistoryDao {
        return database.getUploadHistoryDao()
    }

    @Provides
    fun provideUploadDailyDao(database: ScreenshotDatabase): UploadDailyDao {
        return database.getUploadDailyDao()
    }

    @Provides
    fun provideTopicSeenDao(database: ScreenshotDatabase): TopicSeenDao {
        return database.getTopicSeenDao()
    }
}