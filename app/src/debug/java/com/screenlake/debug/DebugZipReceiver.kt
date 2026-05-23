package com.screenlake.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.screenlake.recorder.services.ZipFileWorker
import timber.log.Timber

/**
 * Debug-only broadcast receiver that triggers ZipFileWorker without chaining to UploadWorker.
 * Only compiled into debug builds via the debug source set.
 *
 * Trigger via adb:
 *   adb shell am broadcast -a com.screenlake.DEBUG_TRIGGER_ZIP -n com.screenlake/.debug.DebugZipReceiver
 *
 * Or use the helper script:
 *   python3 claude-docs/scripts/pull_debug_zip.py
 */
class DebugZipReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Timber.d("DebugZipReceiver: triggering ZipFileWorker (no upload)")
        val request = OneTimeWorkRequestBuilder<ZipFileWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val ACTION = "com.screenlake.DEBUG_TRIGGER_ZIP"
    }
}
