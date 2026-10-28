package com.screenlake.recorder.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.MutableLiveData
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

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
        val uploadFeedback = MutableLiveData<String>()
        val mutex = Mutex()
        var isRunning = false
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

        if (!mutex.tryLock()) {
            Timber.tag(TAG).w("Upload already in progress. Skipping this worker.")
            return Result.failure()
        }

        return try {
            if (isRunning) {
                generalOperationsRepository.saveLog("uploadZipFilesAsync","uploadZipFilesAsync already running.")
                Timber.tag(TAG).w("uploadZipFilesAsync already running.")
                return Result.failure()
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
     * Uploads up to [MAX_UPLOADS_PER_RUN] pending files, oldest log files after the zips. Stops at
     * the first temporary or authentication failure, since the files after it would fail too.
     */
    private suspend fun uploadZipFilesAsync(): UploadSummary = withContext(Dispatchers.IO) {
        val summary = UploadSummary()
        val pending = (generalOperationsRepository.getZipsToUpload() + getLocalLogFiles()).distinctBy { it.file }

        Timber.tag(TAG).d("Found ${pending.size} files to upload.")

        // The per-run limit counts successful uploads only, so files that keep failing (for
        // example a missing invite code) never stop the files after them from being tried.
        for ((index, zip) in pending.withIndex()) {
            if (summary.succeeded >= MAX_UPLOADS_PER_RUN) {
                summary.remainingAfterRun = pending.size - index
                break
            }
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

        if (summary.transientFailure || summary.authenticationFailure) {
            summary.remainingAfterRun = 0
        }
        System.gc() // Suggest garbage collection after uploads
        summary
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
