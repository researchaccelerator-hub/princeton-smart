package com.screenlake.recorder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.screenlake.data.enums.PackageEventType
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ResearchConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver that listens for app install/uninstall/replace events
 * (PACKAGE_ADDED / PACKAGE_REMOVED / PACKAGE_REPLACED) and delegates all gating,
 * filtering, and persistence to GeneralOperationsRepository.recordPackageEvent().
 *
 * Dynamically registered from TouchAccessibilityService (not manifest-declared) --
 * on-device testing showed these actions are NOT reliably delivered to a manifest
 * receiver on current Android, despite older guidance suggesting they were exempt
 * from the API 26+ implicit-broadcast background restrictions. Google's current
 * documentation confirms ACTION_PACKAGE_REPLACED is explicitly not exempt, and
 * ACTION_PACKAGE_ADDED/REMOVED aren't listed as exempt either. Dynamic registration
 * was never subject to that restriction, and since TouchAccessibilityService's
 * accessibility service stays bound continuously (independent of screen/session
 * state) for as long as the accessibility permission is granted, this preserves
 * whole-enrollment capture including while the phone is locked.
 */
@AndroidEntryPoint
class PackageEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!ResearchConfig.LOG_PACKAGE_EVENTS) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        val eventType = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> PackageEventType.INSTALLED
            Intent.ACTION_PACKAGE_REMOVED -> PackageEventType.UNINSTALLED
            Intent.ACTION_PACKAGE_REPLACED -> PackageEventType.REPLACED
            else -> return
        }
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val eventTime = System.currentTimeMillis()
        val appName = lookUpAppName(context, packageName)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generalOperationsRepository.recordPackageEvent(
                    packageName = packageName,
                    appName = appName,
                    eventType = eventType,
                    eventTime = eventTime,
                    isReplacing = isReplacing
                )
            } catch (ex: Exception) {
                Timber.tag("PackageEventReceiver").w(ex, "Failed to record package event for %s", packageName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Best-effort app label lookup. May legitimately fail for PACKAGE_REMOVED, since the
     * package can already be fully uninstalled by the time this runs.
     */
    private fun lookUpAppName(context: Context, packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (ex: PackageManager.NameNotFoundException) {
            null
        }
    }
}
