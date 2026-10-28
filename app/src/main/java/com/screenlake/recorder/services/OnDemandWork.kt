package com.screenlake.recorder.services

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Requests for the on-demand zip and upload jobs. Each job has at most one run in progress and
 * at most one waiting behind it:
 * - a request while a run is in progress queues one more run after it, so data that becomes
 *   ready near the end of that run does not wait for the hourly job;
 * - a request while a run is already waiting adds nothing, since that run sees the new data.
 *
 * A queued run is failed by WorkManager if the run before it fails, so these workers return
 * success (after logging) for errors that retrying would not fix.
 */
object OnDemandWork {

    // One thread, so two requests cannot both decide to queue a run.
    private val executor = Executors.newSingleThreadExecutor()

    fun request(context: Context, uniqueName: String, build: () -> OneTimeWorkRequest) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                requestNow(appContext, uniqueName, build)
            } catch (e: Exception) {
                Timber.e(e, "Could not request $uniqueName")
            }
        }
    }

    internal fun requestNow(context: Context, uniqueName: String, build: () -> OneTimeWorkRequest) {
        val workManager = WorkManager.getInstance(context)
        val states = workManager.getWorkInfosForUniqueWork(uniqueName).get().map { it.state }
        if (!needsNewRun(states)) return
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.APPEND_OR_REPLACE, build()).result.get()
    }

    /** False when a run is already waiting (enqueued, or blocked behind the running one). */
    internal fun needsNewRun(states: List<WorkInfo.State>): Boolean =
        states.none { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }
}
