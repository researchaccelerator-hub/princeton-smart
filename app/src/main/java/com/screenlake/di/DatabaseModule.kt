package com.screenlake.di

import android.content.Context
import androidx.room.Room
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.dao.AccessibilityEventDao
import com.screenlake.data.database.dao.AppSegmentDao
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.PackageEventDao
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
import com.screenlake.recorder.authentication.CognitoSessionAuthenticator
import com.screenlake.recorder.authentication.RealCognitoSessionAuthenticator
import com.screenlake.recorder.services.RealUploadHandler
import com.screenlake.recorder.services.UploadHandler
import com.screenlake.recorder.upload.Util
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
        generalOperationsRepository: GeneralOperationsRepository,
        util: Util
    ): UploadHandler {
        return RealUploadHandler(context, awsService, generalOperationsRepository, util)
    }

    // Fixes a pre-existing gap from Task 3 of the AC-1043 plan: CognitoSessionAuthenticator
    // (interface) and RealCognitoSessionAuthenticator (impl) were added without ever wiring a
    // Hilt binding between them. This went unnoticed because nothing routed Util through Hilt
    // until this task, so the AuthRecoveryManager -> CognitoSessionAuthenticator chain was never
    // actually resolved by the DI graph before now.
    @Provides
    fun provideCognitoSessionAuthenticator(impl: RealCognitoSessionAuthenticator): CognitoSessionAuthenticator {
        return impl
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

    @Provides
    fun providePackageEventDao(database: ScreenshotDatabase): PackageEventDao {
        return database.getPackageEventDao()
    }
}