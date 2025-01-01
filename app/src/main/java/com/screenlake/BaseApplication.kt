package com.screenlake

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.amplifyframework.AmplifyException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.data.database.dao.RestrictedAppDao
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class BaseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var restrictedAppDao: RestrictedAppDao

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        try {
            plantTimber()

            Timber.tag("BaseApplication").d("Initialized Screenlake Application")
        } catch (e: AmplifyException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Timber.tag("BaseApplication").e("Could not initialize Screenlake Application: $e")
        }
    }

    private fun plantTimber() {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(
                priority: Int, tag: String?, message: String, t: Throwable?
            ) {
                if (BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "debug")
                    super.log(priority, "Screenlake$tag", message, t)
            }
        })
    }
}