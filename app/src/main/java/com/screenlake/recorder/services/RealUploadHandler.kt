package com.screenlake.recorder.services

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.screenlake.data.repository.AwsService
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.upload.Util
import com.screenlake.recorder.utilities.HardwareChecks
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealUploadHandler @Inject constructor(
    private val context: Context,
    private val awsService: AwsService,
    private val generalOperationsRepository: GeneralOperationsRepository
) : UploadHandler {
    private var zipsToUpload: List<ScreenshotZipEntity>? = mutableListOf()
    companion object {
        private const val TAG = "UploadWorker"
        val uploadFeedback = MutableLiveData<String>()
    }

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ) {
        val uploadPath = buildUploadPath(file, user, test)

        Timber.tag(TAG).d("Upload path -> $uploadPath")

        try {
            // Generate a signed URL for S3 upload
            val urlFromS3 = Util().generates3ShareUrl(testContext ?: context, file.path, uploadPath)
            if (!urlFromS3.isNullOrEmpty()) {
                val requestFile = file.asRequestBody()

                // Execute the upload request
                val result = withContext(Dispatchers.IO) {
                    awsService.uploadAsset(urlFromS3, requestFile).execute()
                }

                // Handle the upload result
                handleUploadResult(result, file, entryId, uploadPath, test)
            }
        } catch (error: Exception) {
            // Log upload failure
            if (file.extension != "csv") ScreenshotService.lastUploadSuccessful.postValue(false)
            generalOperationsRepository.saveLog(
                ConstantSettings.UPLOAD_FAILED,
                "Upload failed with message -> ${error.message} stacktrace -> ${ScreenshotData.ocrCleanUp(error.stackTraceToString())}."
            )
            Timber.tag(TAG).e(error, "Upload failed")
            WorkerProgressManager.updateProgress("Upload failed -> ${error.message}")
        }
    }

    /**
     * Checks if the device is connected to the network.
     *
     * @return True if the device is connected to the network, false otherwise.
     */
    override suspend fun isNetworkConnected(): Boolean {
        return withContext(Dispatchers.IO) {
            HardwareChecks.isConnectedAsync(WeakReference(context))
        }
    }

    /**
     * Adds local log files to the list of zips to upload.
     *
     * This method searches for log files in the app's file directory and adds them
     * to the list of screenshot zips to be uploaded.
     */
    private fun addLocalLogFiles() {
        val dir = File(context.filesDir?.path)

        // Filter and map log files to ScreenshotZip objects
        val csvs = dir.listFiles()?.filter { it.path.contains("log_data") }?.map { ScreenshotZipEntity(file = it.path) }
        csvs?.let {
            (zipsToUpload as? MutableList<ScreenshotZipEntity>)?.addAll(it)
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
     * Uploads a file asynchronously to the server.
     *
     * This method builds the upload path, generates a signed URL, and uploads the file
     * to the server. It handles the response and logs the upload result.
     *
     * @param file The file to be uploaded.
     * @param entryId The ID of the ScreenshotZip entry associated with the file.
     * @param user The current user information.
     * @param test Flag indicating whether this is a test run.
     * @param testContext Optional context for testing purposes.
     */
    private suspend fun uploadFileAsync(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean = false,
        testContext: Context? = null
    ) {
        val uploadPath = buildUploadPath(file, user, test)

        Timber.tag(TAG).d("Upload path -> $uploadPath")

        try {
            // Generate a signed URL for S3 upload
            val urlFromS3 = Util().generates3ShareUrl(testContext ?: context, file.path, uploadPath)
            if (!urlFromS3.isNullOrEmpty()) {
                val requestFile = file.asRequestBody()

                // Execute the upload request
                val result = withContext(Dispatchers.IO) {
                    awsService.uploadAsset(urlFromS3, requestFile).execute()
                }

                // Handle the upload result
                handleUploadResult(result, file, entryId, uploadPath, test)
            }
        } catch (error: Exception) {
            // Log upload failure
            generalOperationsRepository.saveLog(
                ConstantSettings.UPLOAD_FAILED,
                "Upload failed with message -> ${error.message} stacktrace -> ${ScreenshotData.ocrCleanUp(error.stackTraceToString())}."
            )
            Timber.tag(TAG).e(error, "Upload failed")
        }
    }

    /**
     * Builds the upload path for the given file and user.
     *
     * This method constructs the S3 upload path based on the file extension,
     * user information, and whether the upload is a test.
     *
     * @param file The file to be uploaded.
     * @param user The current user information.
     * @param test Flag indicating whether this is a test run.
     * @return The upload path as a string.
     */
    private fun buildUploadPath(file: File, user: UserEntity?, test: Boolean): String {
        val testPath = if (test) "test/" else ""
        val ext = file.extension

        return if (user?.uploadImages == false) {
            if (ext == "csv") {
                "${testPath}log_events/${getBuildVersion()}/${UUID.randomUUID()}.csv"
            } else {
                ScreenshotService.lastUploadTime.postValue(System.currentTimeMillis())
                "${testPath}tenant/${user.tenantId}_${user.tenantName}/${getBuildVersion()}/${user.emailHash}/${file.name}.zip"
            }
        } else {
            if (ext == "csv") {
                "${testPath}academia/log_events/${getBuildVersion()}/${UUID.randomUUID()}.csv"
            } else {
                ScreenshotService.lastUploadTime.postValue(System.currentTimeMillis())
                "${testPath}academia/tenant/${user?.tenantId}_${user?.tenantName}/panel/${user?.panelId}/${getBuildVersion()}/panelist/${user?.emailHash}/${file.name}.zip"
            }
        }
    }

    /**
     * Handles the result of an upload operation.
     *
     * This method logs the result of the upload, deletes the file and database entry
     * if the upload is successful, and updates the upload feedback.
     *
     * @param result The response from the upload operation.
     * @param file The file that was uploaded.
     * @param entryId The ID of the ScreenshotZip entry associated with the file.
     * @param uploadPath The upload path for the file.
     * @param test Flag indicating whether this is a test run.
     */
    private suspend fun handleUploadResult(
        result: Response<ResponseBody>?,
        file: File,
        entryId: Int?,
        uploadPath: String,
        test: Boolean
    ) {
        if (result?.isSuccessful == true) {
            // Log successful upload
            generalOperationsRepository.saveLog("LAST_UPLOAD", result.message())
            if (file.extension != "csv") ScreenshotService.lastUploadSuccessful.postValue(true)
            file.delete()
            entryId?.let { generalOperationsRepository.deleteZip(it) }
            if (test) UploadWorker.uploadFeedback.postValue("Upload succeeded -> $uploadPath")
            Timber.tag(TAG).d("Upload succeeded")
        } else {
            if (test) UploadWorker.uploadFeedback.postValue("Upload failed -> $uploadPath")
            if (file.extension != "csv") ScreenshotService.lastUploadSuccessful.postValue(false)
            Timber.tag(TAG).d("Upload failed")
        }
    }

    /**
     * Retrieves the build version string.
     *
     * @return The build version string prefixed with "V_".
     */
//    private fun getBuildVersion() = "V_${BuildConfig.VERSION_CODE}"

    private fun getBuildVersion() = "V_12"
}
