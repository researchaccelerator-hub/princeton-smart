package com.screenlake

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.amplifyframework.AmplifyException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.recorder.ocr.Assets
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.lang.ref.WeakReference
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
            Assets.extractAssets(WeakReference(this))
            Timber.tag("BaseApplication").d("Initialized SRK Application")
        } catch (e: AmplifyException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Timber.tag("BaseApplication").e("Could not initialize SRK Application: $e")
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