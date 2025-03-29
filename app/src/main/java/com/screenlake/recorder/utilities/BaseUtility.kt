package com.screenlake.recorder.utilities

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.model.AppInfo
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import com.screenlake.R
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.*

object BaseUtility {
    fun isAndroidFifteen() : Boolean {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    fun getInstalledApps(context: Context): List<ApplicationInfo> {
        val pm: PackageManager = context.packageManager
        return pm.getInstalledApplications(0)
    }

    // TODO: Not always giving the right answer.
    fun getForegroundTaskPackageName(context: Context) : AppInfo {
        val appInfo = AppInfo()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList =
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000*1000, time)
        if (appList != null && appList.size > 0) {
            val mySortedMap: SortedMap<Long, UsageStats> = TreeMap()
            for (usageStats in appList) {
                mySortedMap[usageStats.lastTimeUsed] = usageStats
            }
            if (!mySortedMap.isEmpty()) {
                appInfo.apk = mySortedMap[mySortedMap.lastKey()]?.packageName ?: "package name not found"

                silence {
                    appInfo.name = getAppNameFromPackage(context, appInfo.apk)
                }

                Timber.d("******* ${appInfo.name} *******")
            }
        }

        return appInfo
    }

    fun getAppNameFromPackage(context: Context, packageName: String): String {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)

        return applicationInfo.loadLabel(packageManager).toString()
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun ApplicationInfo.toRestrictedApp(appName: String) : RestrictedAppPersistentEntity {
        return RestrictedAppPersistentEntity(
            appName = appName,
            this.packageName,
            isUserRestricted = false,
            authOverride = false,
            isAuthRestricted = false
        )
    }

    fun getDeviceName(): String? {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.lowercase(Locale.getDefault())
                .startsWith(manufacturer.lowercase(Locale.getDefault()))
        ) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }


    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            first.uppercaseChar().toString() + s.substring(1)
        }
    }
}

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun String.toBase64(): String? {
    return Base64.getEncoder().encodeToString(this.toByteArray())
}

inline fun silence(body: () -> Unit) {
    try {
        body()
    } catch (e: Exception) {
        FirebaseCrashlytics.getInstance().recordException(e)
    }
}

fun Exception.record() {
    try {
        FirebaseCrashlytics.getInstance().recordException(this)
    } catch (ex: Exception) {

    }
}
