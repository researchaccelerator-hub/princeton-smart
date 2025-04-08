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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    private var zipsToUpload: List<ScreenshotZipEntity>? = mutableListOf()

    companion object {
        private const val TAG = "UploadWorker"
        val uploadFeedback = MutableLiveData<String>()
        val mutex = Mutex()
        var isRunning = false
    }

    /**
     * Performs the upload work.
     *
     * This method is called when the worker is executed. It logs the start of the upload service,
     * attempts to upload zip files asynchronously, and logs the completion status.
     *
     * @return Result indicating the success or failure of the upload process.
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
                Result.failure()
            } else {
                isRunning = true

                if (!uploadHandler.isNetworkConnected()) {
                    Timber.tag(TAG).d("No network connection. Upload Worker has finished.")
                    return Result.failure()
                }

                generalOperationsRepository.saveLog("UPLOAD_SERVICE_RUN", "")

                uploadZipFilesAsync()

                Timber.tag(TAG).d("Upload Worker has finished.")
                WorkerProgressManager.updateProgress("Upload has finished.")
                Result.success()
            }
        } catch (ex: Exception) {
            generalOperationsRepository.saveLog("UPLOAD_SERVICE_RUN_FAIL", ex.stackTraceToString())
            Timber.tag(TAG).e(ex, "Upload Worker failed.")
            Result.failure()
        } finally {
            isRunning = false
            mutex.unlock()
        }
    }

    /**
     * Asynchronously uploads zip files to the server.
     *
     * This method fetches zip files from the database, checks network connectivity,
     * and uploads each file. It handles upload progress updates and cleans up files
     * after successful uploads.
     */
    private suspend fun uploadZipFilesAsync() = withContext(Dispatchers.IO) {
        // Fetch the list of zips to upload and the user information
        zipsToUpload = generalOperationsRepository.getZipsToUpload().take(10)?.toMutableList()
        user = generalOperationsRepository.getUser()

        Timber.tag(TAG).d("Found ${zipsToUpload?.count()} zips to upload.")

        // Add local log files to the list of zips to upload
        addLocalLogFiles()

        var count = 0.0
        zipsToUpload?.let {
            for (zip in it) {
                WorkerProgressManager.updateProgress("Uploading $count of ${zipsToUpload?.count()}")
                zip.file?.let { filePath ->
                    val file = File(filePath)
                    if (file.exists() && uploadHandler.isNetworkConnected()) {
                        Timber.tag(TAG).d("Uploading file ${file.name}")

                        // Upload the file
                        async {
                            uploadHandler.uploadFile(file, zip.id, user)
                        }.await()

                        ScreenshotService.manualUploadPercentComplete.postValue(count / it.count())
                        count++
                    } else {
                        // Handle the case where the file does not exist
                        handleFileNotFound(zip, file)
                        count++
                    }
                } ?: count++
            }
            System.gc() // Suggest garbage collection after uploads
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

    /**
     * Adds local log files to the list of zips to upload.
     *
     * This method searches for log files in the app's file directory and adds them
     * to the list of screenshot zips to be uploaded.
     */
    private fun addLocalLogFiles() {
        val dir = File(applicationContext.filesDir?.path)

        // Filter and map log files to ScreenshotZip objects
        val csvs = dir.listFiles()?.filter { it.path.contains("log_data") }?.map { ScreenshotZipEntity(file = it.path) }
        csvs?.let {
            (zipsToUpload as? MutableList<ScreenshotZipEntity>)?.addAll(it)
        }
    }
}
