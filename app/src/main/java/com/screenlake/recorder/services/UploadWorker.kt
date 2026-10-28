package com.screenlake.recorder.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenlake.R
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Worker for handling the upload of screenshot zip files to the server.
 *
 * This worker fetches the list of screenshot zips to upload, checks for network connectivity,
 * and uploads the files to a remote server. It also handles local log files and records
 * the progress and results of the upload process.
 *
 * @property generalOperationsRepository Instance of GenOp to interact with the database.
 * @property user The current user information.
 * @property zipsToUpload List of screenshot zip files to upload.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadHandler: UploadHandler,
    private val generalOperationsRepository: GeneralOperationsRepository
) : CoroutineWorker(context, workerParams) {

    private var user: UserEntity? = null

    private data class UploadSummary(
        var succeeded: Int = 0,
        var failed: Int = 0,
        var skipped: Int = 0,
        var transientFailure: Boolean = false,
        var authenticationFailure: Boolean = false,
        var remainingAfterRun: Int = 0
    )

    companion object {
        private const val TAG = "UploadWorker"
        /** Successful uploads per run; a larger backlog continues in a retried run. */
        private const val MAX_UPLOADS_PER_RUN = 100
        /** The one on-demand upload job; the periodic job in [WorkerStarter] is the only other. */
        const val UPLOAD_ON_DEMAND_WORK = "WORK_MANAGER_UPLOAD_ON_DEMAND"
        private const val UPLOAD_CHANNEL_ID = "upload_service_channel"
        val uploadFeedback = MutableLiveData<String>()
        val mutex = Mutex()
        var isRunning = false

        /**
         * Starts an upload unless one is already waiting or running. A file that is ready while
         * a run is in progress is picked up by that run before it ends.
         */
        fun enqueue(context: Context): Operation =
            WorkManager.getInstance(context).enqueueUniqueWork(
                UPLOAD_ON_DEMAND_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .build()
            )
    }

    /**
     * Uploads pending zips and log files.
     *
     * The outcome decides what WorkManager does next:
     * - success, nothing left: [Result.success].
     * - offline, a temporary AWS or network error, or more files than one run takes:
     *   [Result.retry], files are kept.
     * - credentials could not be renewed: counted by [AuthRecoveryCoordinator]; below the
     *   threshold [Result.retry], at the threshold recovery begins and the run ends with
     *   [Result.success] so it does not retry in a loop.
     * - while recovery waits for the participant to sign in: nothing is uploaded.
     */
    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Upload Worker has started.")

        // Another run is uploading (the periodic and on-demand jobs can overlap). Retry rather
        // than fail, so this request is not lost if that run stops early.
        if (!mutex.tryLock()) {
            Timber.tag(TAG).w("Upload already in progress. Retrying this worker.")
            return Result.retry()
        }

        return try {
            if (isRunning) {
                generalOperationsRepository.saveLog("uploadZipFilesAsync","uploadZipFilesAsync already running.")
                Timber.tag(TAG).w("uploadZipFilesAsync already running.")
                return Result.retry()
            }
            isRunning = true

            if (AuthRecoveryCoordinator.isPending(applicationContext)) {
                WorkerProgressManager.updateProgress("Upload paused: sign in again to resume. Collected data is safe.")
                return Result.success()
            }

            if (!generalOperationsRepository.userExists()) {
                Timber.tag(TAG).d("No user registered yet. Skipping this upload run.")
                return Result.success()
            }

            if (!uploadHandler.isNetworkConnected()) {
                Timber.tag(TAG).d("No network connection. Upload Worker has finished.")
                return Result.retry()
            }

            generalOperationsRepository.saveLog("UPLOAD_SERVICE_RUN", "")
            runInForeground()
            user = generalOperationsRepository.getUser()
            val recovery = AuthRecoveryCoordinator(applicationContext, generalOperationsRepository)

            when (uploadHandler.prepareUploadSession()) {
                UploadResult.SUCCESS -> Unit
                UploadResult.AUTHENTICATION_FAILURE ->
                    return authenticationFailed(recovery, "upload_session_credentials")
                UploadResult.TRANSIENT_FAILURE -> {
                    WorkerProgressManager.updateProgress("Upload delayed: credentials are temporarily unavailable.")
                    return Result.retry()
                }
                UploadResult.CONFIGURATION_FAILURE -> {
                    WorkerProgressManager.updateProgress("Upload failed: the app's AWS setup could not be loaded.")
                    return Result.failure()
                }
            }

            val summary = uploadZipFilesAsync()
            if (summary.authenticationFailure) {
                return authenticationFailed(recovery, "upload_rejected")
            }

            Timber.tag(TAG).d("Upload Worker has finished.")
            when {
                summary.transientFailure -> {
                    WorkerProgressManager.updateProgress("Upload paused after ${summary.succeeded} file(s); the rest will retry.")
                    Result.retry()
                }
                summary.remainingAfterRun > 0 -> {
                    WorkerProgressManager.updateProgress("Uploaded ${summary.succeeded} file(s); ${summary.remainingAfterRun} more will upload in the next run.")
                    Result.retry()
                }
                else -> {
                    WorkerProgressManager.updateProgress(
                        "Upload has finished. Uploaded ${summary.succeeded}, failed ${summary.failed}, skipped ${summary.skipped}."
                    )
                    Result.success()
                }
            }
        } catch (ex: Exception) {
            generalOperationsRepository.saveLog("UPLOAD_SERVICE_RUN_FAIL", ex.stackTraceToString())
            Timber.tag(TAG).e(ex, "Upload Worker failed.")
            Result.retry()
        } finally {
            isRunning = false
            mutex.unlock()
        }
    }

    private suspend fun authenticationFailed(recovery: AuthRecoveryCoordinator, reason: String): Result {
        return if (recovery.onAuthenticationFailure(user, reason)) {
            WorkerProgressManager.updateProgress("Upload paused: sign in again to resume. Collected data is safe.")
            Result.success()
        } else {
            WorkerProgressManager.updateProgress("Upload failed -> credentials expired. Will retry.")
            Result.retry()
        }
    }

    /**
     * Shows an upload notification and keeps the run going past WorkManager's 10-minute limit.
     * Android 12 and later may refuse to start a foreground service while the app is in the
     * background; the run then continues as a normal background job instead of waiting.
     */
    private suspend fun runInForeground() {
        try {
            createUploadNotificationChannel()
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Upload continues in the background.")
            generalOperationsRepository.saveLog("UPLOAD_FOREGROUND_UNAVAILABLE", e.javaClass.simpleName)
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, UPLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_just_square_small)
            .setContentTitle(applicationContext.getString(R.string.upload_notification_title))
            .setContentText(applicationContext.getString(R.string.upload_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ConstantSettings.UPLOAD_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ConstantSettings.UPLOAD_NOTIFICATION_ID, notification)
        }
    }

    private fun createUploadNotificationChannel() {
        val channel = NotificationChannel(
            UPLOAD_CHANNEL_ID,
            applicationContext.getString(R.string.upload_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = applicationContext.getString(R.string.upload_channel_description)
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * Uploads pending files until none are left, up to [MAX_UPLOADS_PER_RUN] successful uploads,
     * log files after the zips. Files that become ready during the run (a zip finishing while
     * this run holds the one upload job) are picked up before it ends. Each file is tried at
     * most once per run. Stops at the first temporary or authentication failure, since the
     * files after it would fail too.
     */
    private suspend fun uploadZipFilesAsync(): UploadSummary = withContext(Dispatchers.IO) {
        val summary = UploadSummary()
        val attempted = mutableSetOf<String>()
        while (true) {
            val pending = (generalOperationsRepository.getZipsToUpload() + getLocalLogFiles())
                .distinctBy { it.file }
                .filter { attemptKey(it) !in attempted }
            if (pending.isEmpty()) break
            uploadPass(pending, summary, attempted)
            if (summary.transientFailure || summary.authenticationFailure || summary.remainingAfterRun > 0) break
        }

        if (summary.transientFailure || summary.authenticationFailure) {
            summary.remainingAfterRun = 0
        }
        System.gc() // Suggest garbage collection after uploads
        summary
    }

    private fun attemptKey(zip: ScreenshotZipEntity) = zip.file ?: "id:${zip.id}"

    private suspend fun uploadPass(
        pending: List<ScreenshotZipEntity>,
        summary: UploadSummary,
        attempted: MutableSet<String>
    ) {
        Timber.tag(TAG).d("Found ${pending.size} files to upload.")

        // The per-run limit counts successful uploads only, so files that keep failing (for
        // example a missing invite code) never stop the files after them from being tried.
        for ((index, zip) in pending.withIndex()) {
            if (summary.succeeded >= MAX_UPLOADS_PER_RUN) {
                summary.remainingAfterRun = pending.size - index
                break
            }
            attempted += attemptKey(zip)
            WorkerProgressManager.updateProgress("Uploading ${index + 1} of ${pending.size}")
            val filePath = zip.file
            if (filePath == null) {
                summary.skipped++
                continue
            }
            val file = File(filePath)
            if (!file.exists()) {
                handleFileNotFound(zip, file)
                summary.skipped++
                continue
            }
            if (!uploadHandler.isNetworkConnected()) {
                summary.transientFailure = true
                break
            }

            Timber.tag(TAG).d("Uploading file ${file.name}")
            when (uploadHandler.uploadFile(file, zip.id, user)) {
                UploadResult.SUCCESS -> summary.succeeded++
                UploadResult.CONFIGURATION_FAILURE -> summary.failed++
                UploadResult.TRANSIENT_FAILURE -> {
                    summary.failed++
                    summary.transientFailure = true
                }
                UploadResult.AUTHENTICATION_FAILURE -> {
                    summary.failed++
                    summary.authenticationFailure = true
                }
            }
            ScreenshotService.manualUploadPercentComplete.postValue((index + 1).toDouble() / pending.size)
            if (summary.transientFailure || summary.authenticationFailure) break
        }
    }

    /**
     * Handles the case where a file to be uploaded is not found.
     *
     * This method logs a warning if a file does not exist on disk and deletes
     * the associated database entry.
     *
     * @param zip The ScreenshotZip object representing the file to upload.
     * @param file The File object for the file to upload.
     */
    private suspend fun handleFileNotFound(zip: ScreenshotZipEntity, file: File) {
        if (!file.exists()) {
            Timber.tag(TAG).w("Could not upload file ${file.name} because it does not exist on disk.")
            zip.id?.let { generalOperationsRepository.deleteScreenshotZip(zip) }
        }
    }

    /** Local event-log files waiting to upload, oldest first. */
    private fun getLocalLogFiles(): List<ScreenshotZipEntity> {
        val dir = File(applicationContext.filesDir?.path)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.contains("log_data") }
            ?.sortedBy { it.lastModified() }
            ?.map { ScreenshotZipEntity(file = it.path) }
            .orEmpty()
    }
}
